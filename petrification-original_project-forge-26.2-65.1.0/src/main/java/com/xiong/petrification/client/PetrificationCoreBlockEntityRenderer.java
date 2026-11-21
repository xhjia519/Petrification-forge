package com.xiong.petrification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * 石化核心光波渲染：激活时绘制脉冲扩散的半透明绿色球壳。
 * 球面网格程序化生成（经度 24 × 纬度 12），半径 1 → 配置半径循环扩散。
 */
public class PetrificationCoreBlockEntityRenderer
        implements BlockEntityRenderer<PetrificationCoreBlockEntity, PetrificationCoreBlockEntityRenderer.CoreRenderState> {
    private static final Identifier WAVE_TEXTURE =
            Identifier.fromNamespaceAndPath("petrification", "textures/misc/wave.png");
    private static final int SLICES = 24;
    private static final int STACKS = 12;
    private static final int PULSE_TICKS = 60;

    public PetrificationCoreBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static class CoreRenderState extends BlockEntityRenderState {
        public boolean active;
        public int radius;
        public float gameTime;
    }

    @Override
    public CoreRenderState createRenderState() {
        return new CoreRenderState();
    }

    @Override
    public void extractRenderState(PetrificationCoreBlockEntity blockEntity, CoreRenderState state,
                                   float partialTicks, Vec3 cameraPosition,
                                   net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        state.active = blockEntity.isActive() && !blockEntity.isAwaitingPayment();
        state.radius = blockEntity.getRadius();
        if (blockEntity.getLevel() != null) {
            state.gameTime = blockEntity.getLevel().getGameTime() + partialTicks;
        }
    }

    @Override
    public void submit(CoreRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState) {
        if (!state.active || state.radius <= 0) {
            return;
        }
        poseStack.pushPose();
        try {
            poseStack.translate(0.5, 0.5, 0.5);
            submitNodeCollector.submitCustomGeometry(poseStack,
                    RenderTypes.entityTranslucent(WAVE_TEXTURE),
                    (pose, consumer) -> renderWave(consumer, pose, state));
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderWave(VertexConsumer consumer, PoseStack.Pose pose, CoreRenderState state) {
        // 脉冲相位 0..1：半径从 1 扩散到配置半径，透明度随扩散衰减
        float phase = (state.gameTime % PULSE_TICKS) / PULSE_TICKS;
        float radius = 1.0f + (state.radius - 1.0f) * phase;
        float alpha = 0.30f * (1.0f - phase * 0.7f);
        for (int i = 0; i < STACKS; i++) {
            float phi0 = (float) (Math.PI * i / STACKS);
            float phi1 = (float) (Math.PI * (i + 1) / STACKS);
            for (int j = 0; j < SLICES; j++) {
                float theta0 = (float) (2 * Math.PI * j / SLICES);
                float theta1 = (float) (2 * Math.PI * (j + 1) / SLICES);
                float x00 = radius * sinf(phi0) * cosf(theta0);
                float y00 = radius * cosf(phi0);
                float z00 = radius * sinf(phi0) * sinf(theta0);
                float x10 = radius * sinf(phi0) * cosf(theta1);
                float y10 = y00;
                float z10 = radius * sinf(phi0) * sinf(theta1);
                float x01 = radius * sinf(phi1) * cosf(theta0);
                float y01 = radius * cosf(phi1);
                float z01 = radius * sinf(phi1) * sinf(theta0);
                float x11 = radius * sinf(phi1) * cosf(theta1);
                float y11 = radius * cosf(phi1);
                float z11 = radius * sinf(phi1) * sinf(theta1);
                // 两个三角形（逆时针朝外）
                emit(consumer, pose, x00, y00, z00, radius, alpha);
                emit(consumer, pose, x10, y10, z10, radius, alpha);
                emit(consumer, pose, x01, y01, z01, radius, alpha);
                emit(consumer, pose, x10, y10, z10, radius, alpha);
                emit(consumer, pose, x11, y11, z11, radius, alpha);
                emit(consumer, pose, x01, y01, z01, radius, alpha);
            }
        }
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
                             float radius, float alpha) {
        consumer.addVertex(pose, x, y, z)
                .setColor(0.2f, 1.0f, 0.5f, alpha)
                .setUv(0.0f, 0.0f)
                .setUv2(0xF0, 0xF0)
                .setNormal(x / radius, y / radius, z / radius);
    }

    private static float sinf(float v) {
        return (float) Math.sin(v);
    }

    private static float cosf(float v) {
        return (float) Math.cos(v);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
