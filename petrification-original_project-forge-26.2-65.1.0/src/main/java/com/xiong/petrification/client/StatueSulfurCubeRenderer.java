package com.xiong.petrification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.registry.ModBlocks;
import com.xiong.petrification.registry.ModDataComponents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SulfurCubeRenderer;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Replaces the vanilla sulfur cube renderer so a swallowed statue shows the mob model instead of
 * the plain statue cube. The vanilla pipeline bakes the contained block from its BlockState only
 * (the ItemStack and its statue data are dropped), so the replacement happens here in
 * extractRenderState while the ItemStack is still reachable.
 */
public class StatueSulfurCubeRenderer extends SulfurCubeRenderer {

    public StatueSulfurCubeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void extractRenderState(SulfurCube entity, SulfurCubeRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        ItemStack body = entity.getBodyArmorItem();
        if (!body.isEmpty() && body.is(ModBlocks.STATUE_BLOCK.get().asItem())) {
            StatueComponent component = body.get(ModDataComponents.STATUE_DATA.get());
            if (component != null) {
                state.containedBlock.clear();
                state.containedBlock.setupSpecialModel(new ContainedStatueRenderer(component), new Matrix4f());
            }
        }
    }

    private record ContainedStatueRenderer(StatueComponent component) implements SpecialModelRenderer<Void> {
        @Override
        public void submit(@Nullable Void argument, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                           int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
            poseStack.pushPose();
            // The inner layer's X-180 flip + translate already converted entity model space
            // (head origin, Y down) into upright corner-based block space — the exact space
            // vanilla contained blocks (and renderItemStatue) expect, so pass it through
            // untouched or the statue ends up upside down and misaligned.
            StatueBlockEntityRenderer.renderItemStatue(component, poseStack, submitNodeCollector, lightCoords);
            poseStack.popPose();
        }

        @Override
        public void getExtents(Consumer<Vector3fc> output) {
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
        public Void extractArgument(ItemStack stack) {
            return null;
        }
    }
}
