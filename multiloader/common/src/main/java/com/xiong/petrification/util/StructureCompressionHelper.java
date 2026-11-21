package com.xiong.petrification.util;

import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.component.StructureStatueData;
import com.xiong.petrification.data.RestoredStructureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 建筑压缩模块的核心逻辑：
 * 1. 三级自动识别（原版结构 → 洪水填充 → 密度扫描），玩家无需手动框选坐标；
 * 2. 捕获边界框内全部方块（含水、植物、门、作物，内部方块不剔除），带 BlockEntity 的方块以打包坐标为键存储完整 NBT；
 * 3. 还原时在主线程分 Tick 批量放置，既防卡顿又保证线程安全。
 */
public final class StructureCompressionHelper {
    // 压缩流程诊断日志（服务端），排查"压缩失败/识别不准"问题
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /**
     * 批量放置/清空方块使用的更新标志：同步客户端 + 跳过形状更新 + 抑制掉落。
     * 形状更新不受 flag 1/2 控制（只要未设 UPDATE_KNOWN_SHAPE 就会执行），
     * 批量操作中途的形状更新会把失去支撑的火把/花/门等弹出成掉落物。
     */
    private static final int BATCH_PLACE_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS
            | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE
            | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS;

    private StructureCompressionHelper() {}

    /** 识别结果：边界框 + 建筑名称。 */
    public record StructureDetection(BoundingBox box, String name) {}

    // ===== 第一章：自动建筑识别 =====

    /**
     * 按优先级自动检测玩家所在的建筑：
     * 第一级 原版结构匹配（村庄、神殿、要塞等）；
     * 第二级 连通域检测（玩家自制建筑，6 方向洪水填充）；
     * 第三级 视距智能扫描（露天或小型建筑，取密度最高的 16³ 区域）。
     */
    public static StructureDetection detect(ServerLevel level, BlockPos playerPos) {
        StructureDetection result = detectVanillaStructure(level, playerPos);
        if (result == null) {
            result = detectFloodFill(level, playerPos.below());
        }
        if (result == null) {
            result = detectDensityScan(level, playerPos);
        }
        if (result != null) {
            // 识别结果日志：边界框尺寸 + 名称，便于核对识别精度
            LOGGER.info("[MiniStructure] detected '{}' box {}x{}x{} at ({},{},{}) for player at {}",
                    result.name(),
                    result.box().getXSpan(), result.box().getYSpan(), result.box().getZSpan(),
                    result.box().minX(), result.box().minY(), result.box().minZ(), playerPos);
        }
        return result;
    }

    /**
     * 第一级：利用 StructureStart 系统获取玩家所在位置的原版结构边界框。
     * getStructureWithPieceAt 保证返回的 start 至少有一个 piece 包含玩家位置；
     * 优先取"玩家所在的那个 piece"（如村庄中的单栋房屋），
     * 而不是整个 StructureStart 的巨大包围盒（整座村庄，会夹带大量地形）。
     */
    private static StructureDetection detectVanillaStructure(ServerLevel level, BlockPos pos) {
        for (Structure structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
            StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure);
            if (!start.isValid()) {
                continue;
            }
            // 结构名称取自 worldgen/structure 注册表（如 "minecraft:village_plains"）
            var key = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure);
            String name = key != null ? key.toString() : StructureStatueData.PLAYER_BUILD;

            // 玩家所在的 piece = 精确到单栋建筑（识别准确性的关键）
            BoundingBox pieceBox = null;
            for (StructurePiece piece : start.getPieces()) {
                if (piece.getBoundingBox().isInside(pos)) {
                    pieceBox = piece.getBoundingBox();
                    break;
                }
            }
            if (pieceBox != null && fitsScanLimit(pieceBox)) {
                return new StructureDetection(pieceBox, name);
            }

            // 玩家不在任何 piece 内（如站在村庄街道上）：
            // 只有整个结构边界框不超过扫描限制时才整体使用，超限则降级到下一级识别
            BoundingBox startBox = start.getBoundingBox();
            if (startBox != pieceBox && fitsScanLimit(startBox)) {
                return new StructureDetection(startBox, name);
            }
        }
        return null;
    }

    /** 边界框任意一轴都不能超过 maxScan，防止整座村庄等巨型结构把微缩比例压到不可见。 */
    private static boolean fitsScanLimit(BoundingBox box) {
        int maxScan = ModConfigSpecHolder.structureMaxScanSize;
        return box.getXSpan() <= maxScan && box.getYSpan() <= maxScan && box.getZSpan() <= maxScan;
    }

    /** 第二级：从玩家脚下方块开始 6 方向洪水填充，形成连续实心区域的包围盒。 */
    private static StructureDetection detectFloodFill(ServerLevel level, BlockPos start) {
        int maxScan = ModConfigSpecHolder.structureMaxScanSize;
        // 洪水填充访问上限：超过说明建筑过大或处于开放地形，降级到第三级
        int maxVisited = maxScan * maxScan * maxScan / 4;

        // 向下寻找第一个建筑材料方块作为种子（玩家可能悬空、站在地毯或泥土上）
        BlockPos seed = start;
        for (int i = 0; i < 4 && !isBuildingBlock(level, seed); i++) {
            seed = seed.below();
        }
        if (!isBuildingBlock(level, seed)) {
            return null;
        }

        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed.immutable());
        visited.add(seed.immutable());

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                cursor.setWithOffset(pos, dir);
                if (!visited.contains(cursor) && isBuildingBlock(level, cursor)) {
                    BlockPos next = cursor.immutable();
                    visited.add(next);
                    if (visited.size() > maxVisited) {
                        return null; // 超过最大检测范围，降级
                    }
                    queue.add(next);
                }
            }
        }

        // 连通域太小（露天孤立方块）则降级到密度扫描
        if (visited.size() < 10) {
            return null;
        }

        BlockPos min = null;
        BlockPos max = null;
        for (BlockPos pos : visited) {
            min = min == null ? pos : BlockPos.min(min, pos);
            max = max == null ? pos : BlockPos.max(max, pos);
        }
        BoundingBox box = BoundingBox.fromCorners(min, max);
        if (!fitsScanLimit(box)) {
            return null; // 任意一轴超出限制，降级
        }
        return new StructureDetection(box, StructureStatueData.PLAYER_BUILD);
    }

    /** 第三级：以玩家为中心小范围扫描，取建筑材料密度最高的 8³ 单元（露天或小型建筑兜底）。 */
    private static StructureDetection detectDensityScan(ServerLevel level, BlockPos center) {
        int radius = 24;
        int cellSize = 8;
        int yRadius = 12;

        BlockPos bestMin = null;
        BlockPos bestMax = null;
        int bestCount = 0;

        // 只统计建筑材料（排除自然地形），防止"密度最高"的区域永远是裸露山体
        for (int cx = center.getX() - radius; cx <= center.getX() + radius - cellSize; cx += cellSize) {
            for (int cy = Math.max(level.getMinY(), center.getY() - yRadius);
                 cy <= Math.min(level.getMaxY() - cellSize, center.getY() + yRadius - cellSize); cy += cellSize) {
                for (int cz = center.getZ() - radius; cz <= center.getZ() + radius - cellSize; cz += cellSize) {
                    // 统计该 8³ 单元内的建筑材料方块，同时记录实际包围盒
                    int count = 0;
                    BlockPos min = null;
                    BlockPos max = null;
                    for (int x = cx; x < cx + cellSize; x++) {
                        for (int y = cy; y < cy + cellSize; y++) {
                            for (int z = cz; z < cz + cellSize; z++) {
                                BlockPos pos = new BlockPos(x, y, z);
                                if (isBuildingBlock(level, pos)) {
                                    count++;
                                    min = min == null ? pos : BlockPos.min(min, pos);
                                    max = max == null ? pos : BlockPos.max(max, pos);
                                }
                            }
                        }
                    }
                    if (count > bestCount) {
                        bestCount = count;
                        bestMin = min;
                        bestMax = max;
                    }
                }
            }
        }

        if (bestMin == null) {
            return null;
        }
        return new StructureDetection(BoundingBox.fromCorners(bestMin, bestMax), StructureStatueData.PLAYER_BUILD);
    }

    /** 空气、流体、树叶视为"可穿透"，不参与连通域与密度统计。 */
    private static boolean isTraversable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir()
                || state.getBlock() instanceof LiquidBlock
                || state.getBlock() instanceof LeavesBlock;
    }

    /**
     * 建筑材料判定：非空气/流体/树叶，且不属于自然地形（泥土、石头、沙等）。
     * 自然地形作为洪水填充的"屏障"，防止玩家房屋地基连通到整片地形导致识别框失控。
     */
    private static boolean isBuildingBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !isTraversable(level, pos) && !isNaturalTerrain(state);
    }

    /** 自然地形方块集合：识别时视为环境而非建筑（建筑材料如木板/圆石/石砖不在此列）。
     *  注意：耕地/土路属于人造方块，不列为屏障，保证农田与作物能连通进识别框。 */
    private static boolean isNaturalTerrain(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.MUD) || state.is(Blocks.CLAY)
                || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE) || state.is(Blocks.BASALT) || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BEDROCK) || state.is(Blocks.NETHERRACK) || state.is(Blocks.END_STONE)
                || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PACKED_MUD);
    }

    // ===== 第三章：压缩执行逻辑 =====

    /**
     * 捕获边界框内所有外表面方块（含 BlockEntity 数据），然后清空整个边界框。
     * 捕获必须在清空之前完成，否则邻居方块已被移除会破坏暴露面检测。
     */
    public static StructureStatueData compressBuilding(ServerLevel level, BoundingBox box, String name) {
        Map<BlockState, Integer> idMap = new HashMap<>();
        List<BlockState> palette = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        Map<Long, CompoundTag> tileDataMap = new HashMap<>();

        int minX = box.minX();
        int minY = box.minY();
        int minZ = box.minZ();

        // 1. 遍历边界框内的所有方块，全部捕获（含内部方块、水、花、草、门、各种作物），
        //    保证还原后建筑完整无缺漏；重复 BlockState 仍由调色表压缩，体积可控
        for (int x = minX; x <= box.maxX(); x++) {
            for (int y = minY; y <= box.maxY(); y++) {
                for (int z = minZ; z <= box.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }

                    // 床的占用状态是方块状态属性（26.2 无床方块实体）：
                    // 村民睡觉时压缩会把 occupied=true 存进调色板，还原后床会永远"被占用"，统一复位
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OCCUPIED)) {
                        state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OCCUPIED, false);
                    }

                    // 相对坐标（0~63）按位打包，禁止直接存绝对坐标，否则高位截断导致错位
                    int relX = x - minX;
                    int relY = y - minY;
                    int relZ = z - minZ;
                    int packedPos = (relX << 16) | (relY << 8) | relZ;

                    // 方块 ID 映射表：重复的 BlockState 只存一次
                    int id = idMap.computeIfAbsent(state, k -> {
                        palette.add(k);
                        return palette.size() - 1;
                    });
                    positions.add(packedPos);
                    indices.add(id);

                    // 容器方块（箱子、熔炉等）以打包坐标为键保存完整 NBT
                    if (state.hasBlockEntity()) {
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be != null) {
                            tileDataMap.put((long) packedPos, be.saveWithFullMetadata(level.registryAccess()));
                        }
                    }
                }
            }
        }

        // 2. 捕获完成后清空整个边界框（含内部方块），建筑整体"消失"
        for (int x = minX; x <= box.maxX(); x++) {
            for (int y = minY; y <= box.maxY(); y++) {
                for (int z = minZ; z <= box.maxZ(); z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), BATCH_PLACE_FLAGS);
                }
            }
        }

        int width = box.maxX() - minX + 1;
        int height = box.maxY() - minY + 1;
        int depth = box.maxZ() - minZ + 1;
        LOGGER.info("[MiniStructure] compressed '{}' box {}x{}x{} at ({},{},{}): {} blocks, {} tile entities",
                name, width, height, depth, minX, minY, minZ, positions.size(), tileDataMap.size());
        return new StructureStatueData(width, height, depth,
                positions.stream().mapToInt(Integer::intValue).toArray(),
                indices.stream().mapToInt(Integer::intValue).toArray(),
                palette, tileDataMap, name, System.currentTimeMillis(), positions.size());
    }

    /** 暴露面检测已废弃：改为捕获边界框内全部非空气方块，杜绝缺漏。 */

    // ===== 第五章：还原逻辑（主线程分 Tick 批量放置） =====

    /** 一次进行中的还原任务；index 随批次推进，全部放置完毕后注册到全局追踪管理器。 */
    private static final class RestoreTask {
        final ServerLevel level;
        final StructureStatueData data;
        final BlockPos anchor;
        int index;

        RestoreTask(ServerLevel level, StructureStatueData data, BlockPos anchor) {
            this.level = level;
            this.data = data;
            this.anchor = anchor;
        }
    }

    private static final List<RestoreTask> PENDING_RESTORES = new ArrayList<>();

    /** 触发入口：从锚点（微缩方块位置）开始分批还原建筑。 */
    public static void startRestore(ServerLevel level, StructureStatueData data, BlockPos anchor) {
        PENDING_RESTORES.add(new RestoreTask(level, data, anchor));
    }

    /** 每个服务端 Level Tick 处理一次批量放置，禁止在工作线程操作世界对象（由加载器事件转发）。 */
    public static void onLevelTick(ServerLevel serverLevel) {
        if (PENDING_RESTORES.isEmpty()) {
            return;
        }
        tickRestores(serverLevel);
    }

    private static void tickRestores(ServerLevel level) {
        Iterator<RestoreTask> iterator = PENDING_RESTORES.iterator();
        while (iterator.hasNext()) {
            RestoreTask task = iterator.next();
            if (task.level != level) {
                continue;
            }

            int total = task.data.positions().length;
            int end = Math.min(task.index + ModConfigSpecHolder.structureRestoreBatchSize, total);
            for (int i = task.index; i < end; i++) {
                int packed = task.data.positions()[i];
                int x = StructureStatueData.unpackX(packed);
                int y = StructureStatueData.unpackY(packed);
                int z = StructureStatueData.unpackZ(packed);
                BlockPos targetPos = task.anchor.offset(x, y, z);

                // 恢复方块状态（朝向、含水等完整保留），批量放置标志见 BATCH_PLACE_FLAGS
                level.setBlock(targetPos, task.data.stateAt(i), BATCH_PLACE_FLAGS);

                // 从打包坐标映射中精准恢复 BlockEntity 数据
                CompoundTag tag = task.data.tileEntityData().get((long) packed);
                if (tag != null && level.getBlockEntity(targetPos) != null) {
                    try {
                        level.getBlockEntity(targetPos).loadWithComponents(
                                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                    } catch (Exception e) {
                        // 单个容器数据损坏不应中断整栋建筑的还原
                    }
                }
            }
            task.index = end;

            // 全部放置完毕，注册到全局追踪管理器（供"挖掘重新压缩"使用）
            if (end >= total) {
                iterator.remove();
                RestoredStructureManager.add(level, task.anchor, task.data);
            }
        }
    }

    // ===== 识别反馈：边界框彩色粒子边框 =====

    /** 在边界框的 12 条棱上撒彩色尘埃粒子，勾勒出建筑范围。 */
    public static void spawnBorderParticles(ServerLevel level, BoundingBox box) {
        int color = 0x00FF7F;
        DustParticleOptions options = new DustParticleOptions(color, 1.5f);
        int step = 2;

        for (int x = box.minX(); x <= box.maxX() + 1; x += step) {
            for (int y : new int[]{box.minY(), box.maxY() + 1}) {
                for (int z : new int[]{box.minZ(), box.maxZ() + 1}) {
                    level.sendParticles(options, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
        for (int y = box.minY(); y <= box.maxY() + 1; y += step) {
            for (int x : new int[]{box.minX(), box.maxX() + 1}) {
                for (int z : new int[]{box.minZ(), box.maxZ() + 1}) {
                    level.sendParticles(options, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
        for (int z = box.minZ(); z <= box.maxZ() + 1; z += step) {
            for (int x : new int[]{box.minX(), box.maxX() + 1}) {
                for (int y : new int[]{box.minY(), box.maxY() + 1}) {
                    level.sendParticles(options, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    /** 聊天栏提示建筑信息：§a识别到建筑：§b[名称] §a(长X宽X高)。 */
    public static void sendIdentificationMessage(net.minecraft.server.level.ServerPlayer player,
                                                  StructureDetection detection) {
        BoundingBox box = detection.box();
        int sizeX = box.maxX() - box.minX() + 1;
        int sizeY = box.maxY() - box.minY() + 1;
        int sizeZ = box.maxZ() - box.minZ() + 1;
        Component name = StructureStatueData.PLAYER_BUILD.equals(detection.name())
                ? Component.translatable("structure.petrification.player_build")
                : Component.literal(detection.name());
        player.sendSystemMessage(Component.translatable("message.petrification.structure_identified",
                name, sizeX + "×" + sizeY + "×" + sizeZ));
        player.sendSystemMessage(Component.translatable("message.petrification.structure_confirm",
                ModConfigSpecHolder.structureConfirmationTimeout));
    }
}
