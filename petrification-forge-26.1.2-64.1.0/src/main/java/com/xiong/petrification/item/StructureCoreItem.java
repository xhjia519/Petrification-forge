package com.xiong.petrification.item;

import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.component.StructureStatueData;
import com.xiong.petrification.registry.ModDataComponents;
import com.xiong.petrification.registry.ModItems;
import com.xiong.petrification.util.StructureCompressionHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 建筑压缩核心：站在目标建筑内/附近右键识别（粒子高亮 + 聊天提示），
 * 5 秒冷却确认期内再次右键执行压缩，建筑消失并掉落携带数据的微缩建筑物品；
 * 超时未确认则自动取消（不消耗物品）。
 */
public class StructureCoreItem extends Item {
    /** 待确认的识别结果：超时后自动失效，防止误触。 */
    private record PendingScan(BoundingBox box, String name, long deadline) {}

    private static final Map<UUID, PendingScan> PENDING = new HashMap<>();

    public StructureCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        long now = System.currentTimeMillis();
        PendingScan pending = PENDING.get(player.getUUID());

        if (pending != null && now <= pending.deadline()) {
            // 第二次右键：5 秒确认期内，执行压缩
            PENDING.remove(player.getUUID());
            return compress(serverLevel, serverPlayer, pending);
        }

        // 第一次右键：自动识别建筑
        if (pending != null) {
            PENDING.remove(player.getUUID()); // 清理已超时的旧记录
        }
        StructureCompressionHelper.StructureDetection detection =
                StructureCompressionHelper.detect(serverLevel, player.blockPosition());
        if (detection == null) {
            player.sendSystemMessage(Component.translatable("message.petrification.structure_not_found"));
            return InteractionResult.CONSUME;
        }

        PENDING.put(player.getUUID(), new PendingScan(detection.box(), detection.name(),
                now + ModConfigSpecHolder.structureConfirmationTimeout * 1000L));
        StructureCompressionHelper.spawnBorderParticles(serverLevel, detection.box());
        StructureCompressionHelper.sendIdentificationMessage(serverPlayer, detection);
        return InteractionResult.CONSUME;
    }

    /** 执行压缩：捕获外表面数据 → 清空建筑 → 掉落微缩建筑物品。 */
    private InteractionResult compress(ServerLevel level, ServerPlayer player, PendingScan pending) {
        // 先压缩出数据（内部会清空整个边界框），再检查方块数是否达标
        StructureStatueData data = StructureCompressionHelper.compressBuilding(level, pending.box(), pending.name());
        if (data.totalBlocks() < ModConfigSpecHolder.structureMinBlocks) {
            player.sendSystemMessage(Component.translatable("message.petrification.structure_too_small",
                    data.totalBlocks(), ModConfigSpecHolder.structureMinBlocks));
            return InteractionResult.CONSUME;
        }

        ItemStack stack = new ItemStack(ModItems.MINI_STRUCTURE_BLOCK_ITEM.get());
        stack.set(ModDataComponents.STRUCTURE_STATUE_DATA.get(), data);
        // 压缩核心本身不消耗（可重复使用的工具道具）
        net.minecraft.world.level.block.Block.popResource(level, player.blockPosition(), stack);
        level.playSound(null, player.blockPosition(), SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0f, 0.5f);
        player.sendSystemMessage(Component.translatable("message.petrification.structure_compressed",
                data.getDisplayName(), data.totalBlocks()));
        return InteractionResult.CONSUME;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner,
                              @Nullable EquipmentSlot slot) {
        // 识别确认期内每 20 tick 刷新一次边框粒子，形成"闪烁"提示（持续到超时）
        if (level.getServer().getTickCount() % 20 != 0) {
            return;
        }
        PendingScan pending = PENDING.get(owner.getUUID());
        if (pending == null) {
            return;
        }
        if (System.currentTimeMillis() > pending.deadline()) {
            PENDING.remove(owner.getUUID()); // 超时取消识别，不消耗物品
            if (owner instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("message.petrification.structure_cancelled"));
            }
            return;
        }
        StructureCompressionHelper.spawnBorderParticles(level, pending.box());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipComponents, TooltipFlag flag) {
        tooltipComponents.accept(Component.translatable("tooltip.petrification.structure_core_usage"));
    }
}
