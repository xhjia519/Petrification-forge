package com.xiong.petrification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.registry.ModDataComponents;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class StatueSpecialRenderer implements SpecialModelRenderer<StatueComponent> {

    @Override
    public void render(@Nullable StatueComponent argument, ItemDisplayContext displayContext,
                       PoseStack poseStack, MultiBufferSource bufferSource, int lightCoords,
                       int overlayCoords, boolean hasFoil) {
        if (argument == null) {
            return;
        }
        StatueBlockEntityRenderer.renderItemStatue(argument, poseStack, bufferSource, lightCoords);
    }

    @Override
    @Nullable
    public StatueComponent extractArgument(ItemStack stack) {
        return stack.get(ModDataComponents.STATUE_DATA.get());
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<StatueComponent> bake(EntityModelSet modelSet) {
            return new StatueSpecialRenderer();
        }
    }
}
