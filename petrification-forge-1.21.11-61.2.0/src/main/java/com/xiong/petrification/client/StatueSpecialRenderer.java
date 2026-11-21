package com.xiong.petrification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.registry.ModDataComponents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Renders the statue item (held in hand / shown in the GUI) as the original mob model
 * with the material block texture, matching the placed statue. The per-stack data comes
 * from the {@link StatueComponent} stored on the item.
 */
public class StatueSpecialRenderer implements SpecialModelRenderer<StatueComponent> {

    @Override
    public void submit(@Nullable StatueComponent argument, ItemDisplayContext displayContext,
                       PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
                       int overlayCoords, boolean hasFoil, int outlineColor) {
        if (argument == null) {
            return;
        }
        StatueBlockEntityRenderer.renderItemStatue(argument, poseStack, submitNodeCollector, lightCoords);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        // Approximate the statue as a unit cube so the item/GUI sizing treats it like a block.
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    output.accept(new Vector3f(x, y, z));
                }
            }
        }
    }

    @Override
    @Nullable
    public StatueComponent extractArgument(ItemStack stack) {
        return stack.get(ModDataComponents.STATUE_DATA.get());
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<StatueComponent> bake(SpecialModelRenderer.BakingContext context) {
            return new StatueSpecialRenderer();
        }
    }
}
