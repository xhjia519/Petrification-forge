package com.xiong.petrification.item;

import com.xiong.petrification.block.MiniStructureBlockEntity;
import com.xiong.petrification.component.StructureStatueData;
import com.xiong.petrification.registry.ModBlocks;
import com.xiong.petrification.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 微缩建筑物品：放置微缩方块（渲染建筑微缩模型），右键微缩方块还原整栋建筑。
 * Tooltip 展示建筑名称、尺寸、外表面方块数与压缩时间。
 */
public class MiniStructureBlockItem extends BlockItem {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public MiniStructureBlockItem(BlockItem.Properties properties) {
        super(ModBlocks.MINI_STRUCTURE_BLOCK.get(), properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player,
                                                 ItemStack stack, BlockState state) {
        boolean result = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        // 放置时把物品上的压缩数据复制进方块实体，供渲染与还原使用
        StructureStatueData data = stack.get(ModDataComponents.STRUCTURE_STATUE_DATA.get());
        if (data != null && level.getBlockEntity(pos) instanceof MiniStructureBlockEntity blockEntity) {
            blockEntity.setStructureData(data);
            if (!level.isClientSide()) {
                // 保险：显式标记方块变更，确保压缩数据随方块实体数据包同步到客户端
                // （正常路径是区块延迟广播，这里兜底客户端预测失败的情况）
                level.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                com.mojang.logging.LogUtils.getLogger().info(
                        "[MiniStructure] placed at {} with {} blocks", pos, data.totalBlocks());
            }
        } else if (!level.isClientSide()) {
            // 物品上没有压缩数据：说明压缩流程出了问题，记录日志便于排查
            com.mojang.logging.LogUtils.getLogger().warn(
                    "[MiniStructure] placed at {} WITHOUT structure data", pos);
        }
        return result;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        return super.place(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag flag) {
        StructureStatueData data = stack.get(ModDataComponents.STRUCTURE_STATUE_DATA.get());
        if (data == null) {
            super.appendHoverText(stack, context, tooltipComponents, flag);
            return;
        }

        // 建筑名称
        tooltipComponents.add(Component.translatable("tooltip.petrification.mini_structure_name",
                data.getDisplayName()));
        // 尺寸
        tooltipComponents.add(Component.translatable("tooltip.petrification.mini_structure_size",
                data.width() + " × " + data.height() + " × " + data.depth()));
        // 外表面方块数
        tooltipComponents.add(Component.translatable("tooltip.petrification.mini_structure_blocks",
                data.totalBlocks()));
        // 用法提示
        tooltipComponents.add(Component.translatable("tooltip.petrification.mini_structure_usage"));
        // 压缩时间
        tooltipComponents.add(Component.translatable("tooltip.petrification.mini_structure_time",
                TIME_FORMAT.format(Instant.ofEpochMilli(data.compressedTime()))));
    }

    @Override
    public Component getName(ItemStack stack) {
        StructureStatueData data = stack.get(ModDataComponents.STRUCTURE_STATUE_DATA.get());
        if (data != null) {
            return Component.translatable("item.petrification.mini_structure_block.named", data.getDisplayName());
        }
        return super.getName(stack);
    }
}
