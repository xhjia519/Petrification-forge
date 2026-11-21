package com.xiong.petrification.util;

import com.mojang.logging.LogUtils;
import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.block.MiniStructureBlockEntity;
import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import com.xiong.petrification.block.RestorationBlockEntity;
import com.xiong.petrification.block.StatueBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 活塞移动方块实体的数据迁移与掉落处理（26.2 原版移动不携带方块实体数据）。
 * 活塞 Pre 事件时按配置行为处理：MOVE 保存被推方块实体 NBT，移动完成后由新方块实体
 * 恢复；DROP 提前掉落容器内容（方块本体由活塞的 loot table 掉落流程负责）。
 *
 * 恢复不能在被推方块转换的 onLoad 中立即执行：LevelChunk.setBlockState 会先创建临时方块实体
 * （消费数据后随即移除）再重建最终方块实体，因此数据只挂起不消费，由加载器事件转发层的
 * LevelTickEvent.Post 在下一 tick 对最终存活的方块实体恢复。
 * 本类为纯工具类，事件入口由各加载器模块负责转发。
 */
public final class PistonMoveData {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 挂起数据超时（tick）：超过则丢弃，防止 Pre 被取消等场景的数据泄漏。 */
    private static final long TIMEOUT_TICKS = 200;

    /** 待恢复数据：维度 → 位置 → 方块实体类型 id + NBT。 */
    private static final Map<ResourceKey<Level>, Map<BlockPos, PendingData>> PENDING = new HashMap<>();

    private record PendingData(Identifier typeId, CompoundTag tag, long savedTick) {}

    private PistonMoveData() {}

    /**
     * 活塞 Pre 事件：按配置决定被推方块是保存数据（MOVE）、就地掉落（DROP）还是阻止推动（BLOCK）。
     *
     * @param retracting 是否为活塞收回（收回时会把前方方块"拉"回来）
     * @return true 表示加载器应取消该活塞事件
     */
    public static boolean onPistonPre(ServerLevel level, BlockPos pos, Direction direction,
                                      boolean retracting, PistonStructureResolver resolver) {
        if (resolver == null || !resolver.resolve()) {
            LOGGER.info("[PistonMove] Pre at {} dir={} retract={} behavior={} -> resolve failed",
                    pos, direction, retracting, ModConfigSpecHolder.pistonBehavior);
            return false;
        }
        boolean involvesTracked = containsTracked(level, resolver.getToPush());
        LOGGER.info("[PistonMove] Pre at {} dir={} retract={} behavior={} toPush={} tracked={}",
                pos, direction, retracting, ModConfigSpecHolder.pistonBehavior,
                resolver.getToPush().size(), involvesTracked);
        switch (ModConfigSpecHolder.pistonBehavior) {
            case BLOCK -> {
                // 推不动：无论推出还是拉回都直接取消活塞动作
                return involvesTracked;
            }
            case MOVE -> {
                // 仅推不可拉：收回时不允许把模组方块拉回来
                if (retracting && involvesTracked) {
                    return true;
                }
                saveMoved(level, resolver);
            }
            case DROP -> {
                // 推即掉落：先掉落容器内容与方块本体，再取消活塞动作
                if (involvesTracked) {
                    dropContents(level, resolver.getToPush());
                    destroyBlocks(level, resolver.getToPush());
                    return true;
                }
            }
        }
        return false;
    }

    /** 被推列表里是否包含本模组方块实体（无方块实体的方块不影响，交给原版处理）。 */
    private static boolean containsTracked(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (isTracked(level.getBlockEntity(pos))) {
                return true;
            }
        }
        return false;
    }

    /** DROP：就地销毁方块并按原版掉落规则产出物品。 */
    private static void destroyBlocks(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!isTracked(be)) {
                continue;
            }
            net.minecraft.world.level.block.Block.dropResources(
                    level.getBlockState(pos), level, pos, be);
            level.removeBlock(pos, false);
        }
    }

    /** DROP：活塞破坏（toDestroy）前掉落容器内容，方块本体由活塞的掉落流程处理。 */
    private static void dropContents(ServerLevel level, List<BlockPos> toDestroy) {
        for (BlockPos pos : toDestroy) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PetrificationCoreBlockEntity core) {
                core.dropContents(level, pos);
            } else if (be instanceof RestorationBlockEntity rest) {
                rest.dropContents(level, pos);
            }
        }
    }

    /** MOVE：保存被推方块实体的 NBT，键为目标位置，供新位置方块实体 onLoad 时恢复。 */
    private static void saveMoved(ServerLevel level, PistonStructureResolver resolver) {
        Direction pushDirection = resolver.getPushDirection();
        Map<BlockPos, PendingData> byPos = PENDING.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        for (BlockPos pos : resolver.getToPush()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (isTracked(be)) {
                BlockPos target = pos.relative(pushDirection);
                // 日志：记录保存时服务端 BE 是否带数据，用于区分“存档数据已空”与“迁移失败”
                String dataHint = (be instanceof StatueBlockEntity s) ? String.valueOf(s.getStatueData() != null) : "n/a";
                byPos.put(target, new PendingData(
                        BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()),
                        be.saveWithoutMetadata(level.registryAccess()),
                        level.getGameTime()));
                LOGGER.info("[PistonMove] saved {} type={} hasData={} -> target {}", pos,
                        BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()), dataHint, target);
            }
        }
    }

    private static boolean isTracked(BlockEntity be) {
        return be instanceof PetrificationCoreBlockEntity || be instanceof RestorationBlockEntity
                || be instanceof StatueBlockEntity || be instanceof MiniStructureBlockEntity;
    }

    /** 每个服务端 Level Tick：对最终存活的方块实体执行挂起数据的恢复（由加载器事件转发）。 */
    public static void onLevelTick(ServerLevel level) {
        Map<BlockPos, PendingData> byPos = PENDING.get(level.dimension());
        if (byPos == null || byPos.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, PendingData>> iterator = byPos.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, PendingData> entry = iterator.next();
            BlockPos pos = entry.getKey();
            PendingData pending = entry.getValue();
            BlockEntity target = level.getBlockEntity(pos);
            if (target == null || !pending.typeId().equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(target.getType()))) {
                // 动画未完成或方块被破坏/替换：等待；超时后丢弃防泄漏
                if (now - pending.savedTick() > TIMEOUT_TICKS) {
                    iterator.remove();
                    LOGGER.warn("[PistonMove] restore timeout at {} type={} (target absent or replaced)", pos,
                            pending.typeId());
                }
                continue;
            }
            iterator.remove();
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(target.problemPath(), LOGGER)) {
                target.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), pending.tag()));
            }
            target.setChanged();
            // Post 晚于 chunkSource.tick（broadcastChanges），且 setChanged 不标记 hasChangedSections：
            // 必须主动 blockChanged，否则恢复的数据不会广播给客户端（客户端 BE 保持空白）。
            level.sendBlockUpdated(pos, target.getBlockState(), target.getBlockState(), 3);
            LOGGER.info("[PistonMove] restored at {} type={} target#{} tick={}", pos, pending.typeId(),
                    System.identityHashCode(target), now);
        }
    }
}
