package com.xiong.petrification.item;

import com.xiong.petrification.block.StatueBlockEntity;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.component.StatueComponent;
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
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class StatueBlockItem extends BlockItem {
    public StatueBlockItem(BlockItem.Properties properties) {
        super(ModBlocks.STATUE_BLOCK.get(), properties);
    }

    /**
     * 雕像物品用手持/背包/GUI 渲染器显示原生物模型（1.21.3 无 SpecialModelRenderer，改用 BEWLR）。
     * 需配合物品模型 parent = minecraft:builtin/entity 才会触发（BakedModel.isCustomRenderer）。
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.xiong.petrification.client.StatueItemRenderer.create();
            }
        });
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player,
                                                 ItemStack stack, BlockState state) {
        boolean result = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        StatueComponent component = stack.get(ModDataComponents.STATUE_DATA.get());
        if (component != null && level.getBlockEntity(pos) instanceof StatueBlockEntity blockEntity) {
            blockEntity.setStatueData(component);
        } else if (level.getBlockEntity(pos) instanceof StatueBlockEntity blockEntity) {
            blockEntity.setStatueData(StatueComponent.defaultStatue(MaterialType.STONE));
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
        StatueComponent component = stack.get(ModDataComponents.STATUE_DATA.get());
        if (component == null) {
            super.appendHoverText(stack, context, tooltipComponents, flag);
            return;
        }

        // Target type
        String targetKey = component.targetType() == com.xiong.petrification.component.TargetType.PLAYER
                ? "tooltip.petrification.target_player"
                : "tooltip.petrification.target_biological";
        tooltipComponents.add(Component.translatable("tooltip.petrification.target_type",
                Component.translatable(targetKey)));

        // Material type
        tooltipComponents.add(Component.translatable("tooltip.petrification.material",
                Component.translatable("material.petrification." + component.materialType().getSerializedName())));

        // Entity name
        tooltipComponents.add(Component.translatable("tooltip.petrification.entity",
                Component.translatable(component.entityType().toLanguageKey("entity"))));

        // Custom name
        if (component.hasCustomName() && component.displayName().isPresent()) {
            tooltipComponents.add(Component.translatable("tooltip.petrification.custom_name", component.displayName().get()));
        }

        // Copper-specific: oxidation bar + waxed status
        if (component.materialType() == MaterialType.COPPER) {
            int percent = (int) (component.oxidation() * 100);
            int filled = percent / 10;
            String bar = "█".repeat(filled) + "░".repeat(10 - filled);
            tooltipComponents.add(Component.translatable("tooltip.petrification.oxidation",
                    bar + " " + percent + "%"));
            if (component.isWaxed()) {
                tooltipComponents.add(Component.translatable("tooltip.petrification.waxed"));
            }
        }

        tooltipComponents.add(Component.translatable("tooltip.petrification.pickaxe_required"));
    }

    @Override
    public Component getName(ItemStack stack) {
        StatueComponent component = stack.get(ModDataComponents.STATUE_DATA.get());
        if (component != null) {
            // A renamed statue keeps its custom name; otherwise build a localised
            // "<material><entity>" name so it shows in the active language (e.g. Chinese).
            if (component.hasCustomName() && component.displayName().isPresent()) {
                return Component.literal(component.displayName().get());
            }
            return Component.translatable("statue.petrification.name",
                    Component.translatable("material.petrification." + component.materialType().getSerializedName()),
                    Component.translatable(component.entityType().toLanguageKey("entity")));
        }
        return super.getName(stack);
    }
}
