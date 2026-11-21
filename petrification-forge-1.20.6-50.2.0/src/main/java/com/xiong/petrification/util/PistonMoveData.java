package com.xiong.petrification.util;

import com.mojang.logging.LogUtils;
import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.block.MiniStructureBlockEntity;
import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import com.xiong.petrification.block.RestorationBlockEntity;
import com.xiong.petrification.block.StatueBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 活塞移动方块实体的数据迁移与掉落处理（26.2 原版移动不携带方块实体数据）。
 * PistonEvent.Pre 时按配置行为处理：MOVE 保存被推方块实体 NBT，移动完成后由新方块实体
 * 恢复；DROP 提前掉落容器内容（方块本体由活塞的 loot table 掉落流程负责）。
 *
 * 恢复不能在被推方块转换的 onLoad 中立即执行：LevelChunk.setBlockState 会先创建临时方块实体
 * （消费数据后随即移除）再重建最终方块实体，因此数据只挂起不消费，由 ModEventHandlers 的
 * LevelTickEvent.Post 在下一 tick 对最终存活的方块实体恢复。
 * 本类为纯工具类（不含 @EventBusSubscriber），事件入口统一走 ModEventHandlers。
 */
public final class PistonMoveData {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 挂起数据超时（tick）：超过则丢弃，防止 Pre 被取消等场景的数据泄漏。 */
    private static final long TIMEOUT_TICKS = 200;

    /** 待恢复数据：维度 → 位置 → 方块实体类型 id + NBT。 */
    private static final Map<ResourceKey<Level>, Map<BlockPos, PendingData>> PENDING = new HashMap<>();

    private record PendingData(ResourceLocation typeId, CompoundTag tag, long savedTick) {}

    private PistonMoveData() {}

    /** PistonEvent.Pre：按配置决定被推方块是保存数据（MOVE）还是提前掉落容器内容（DROP）。 */
    public static void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            LOGGER.info("[PistonMove] Pre at {} dir={} type={} behavior={} -> resolve failed",
                    event.getPos(), event.getDirection(), event.getPistonMoveType(), ModConfigSpecHolder.pistonBehavior);
            return;
        }
        LOGGER.info("[PistonMove] Pre at {} dir={} type={} behavior={} toPush={}",
                event.getPos(), event.getDirection(), event.getPistonMoveType(),
                ModConfigSpecHolder.pistonBehavior, resolver.getToPush().size());
        switch (ModConfigSpecHolder.pistonBehavior) {
            case DROP -> dropContents(level, resolver.getToDestroy());
            case MOVE -> saveMoved(level, resolver);
            case BLOCK -> {
            }
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
                        ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType()),
                        be.saveWithoutMetadata(level.registryAccess()),
                        level.getGameTime()));
                LOGGER.info("[PistonMove] saved {} type={} hasData={} -> target {}", pos,
                        ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType()), dataHint, target);
            }
        }
    }

    private static boolean isTracked(BlockEntity be) {
        return be instanceof PetrificationCoreBlockEntity || be instanceof RestorationBlockEntity
                || be instanceof StatueBlockEntity || be instanceof MiniStructureBlockEntity;
    }

    /** 方块实体 onLoad 时调用：若该位置有被活塞推来的数据则挂起（仅服务端，不在此恢复）。 */
    public static void restoreIfMoved(BlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) {
            return;
        }
        Map<BlockPos, PendingData> byPos = PENDING.get(be.getLevel().dimension());
        if (byPos == null) {
            return;
        }
        PendingData pending = byPos.get(be.getBlockPos());
        if (pending == null) {
            // 手动放置的方块或与活塞无关的转换流程，无挂起数据
            return;
        }
        // 类型不一致说明该位置最终放置的不是被推的方块（Pre 被取消/被替换）：丢弃数据
        if (!pending.typeId().equals(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType()))) {
            byPos.remove(be.getBlockPos());
            LOGGER.info("[PistonMove] restore type mismatch at {} expected={} actual={} (dropped)", be.getBlockPos(),
                    pending.typeId(), ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType()));
            return;
        }
        // 注意：setBlock 转换时会先后创建临时实例与最终实例且都会 onLoad，此处只挂起不消费，
        // 由 onLevelTick 在下一 tick 对最终存活的 BE 恢复（否则数据被临时实例消费后随实例销毁）。
        LOGGER.debug("[PistonMove] defer restore at {} type={} be#{}", be.getBlockPos(), pending.typeId(),
                System.identityHashCode(be));
    }

    /** 每个服务端 Level Tick：对最终存活的方块实体执行挂起数据的恢复（由 ModEventHandlers 转发）。 */
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
            if (target == null || !pending.typeId().equals(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(target.getType()))) {
                // 动画未完成或方块被破坏/替换：等待；超时后丢弃防泄漏
                if (now - pending.savedTick() > TIMEOUT_TICKS) {
                    iterator.remove();
                    LOGGER.warn("[PistonMove] restore timeout at {} type={} (target absent or replaced)", pos,
                            pending.typeId());
                }
                continue;
            }
            iterator.remove();
            target.loadWithComponents(pending.tag(), level.registryAccess());
            target.setChanged();
            // Post 晚于 chunkSource.tick（broadcastChanges），且 setChanged 不标记 hasChangedSections：
            // 必须主动 blockChanged，否则恢复的数据不会广播给客户端（客户端 BE 保持空白）。
            level.sendBlockUpdated(pos, target.getBlockState(), target.getBlockState(), 3);
            LOGGER.info("[PistonMove] restored at {} type={} target#{} tick={}", pos, pending.typeId(),
                    System.identityHashCode(target), now);
        }
    }
}