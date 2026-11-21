package com.xiong.petrification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.block.MiniStructureBlockEntity;
import com.xiong.petrification.block.StatueBlock;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.component.StructureStatueData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 微缩建筑渲染器：把压缩数据中的全部方块按原版纹理逐个微缩渲染。
 * 等比缩放：统一按三轴最大尺寸归一化，保持方块比例不变形（宽高深不等的建筑不会被拉伸），
 * 模型水平居中、贴底摆放；每个微缩方块通过 submitMovingBlock 走原版方块渲染管线（保留原始纹理与光照）。
 */
public class MiniStructureRenderer implements BlockEntityRenderer<MiniStructureBlockEntity, MiniStructureRenderer.MiniStructureRenderState> {
    // 限额日志：只打印前 30 条，避免每帧刷屏（仿雕像渲染器的做法），便于排查"不渲染"问题
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static int logBudget = 30;

    private static void log(String message) {
        if (logBudget > 0) {
            logBudget--;
            LOGGER.info("[MiniStructure] {}", message);
        }
    }

    public MiniStructureRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public MiniStructureRenderState createRenderState() {
        return new MiniStructureRenderState();
    }

    @Override
    public void extractRenderState(MiniStructureBlockEntity blockEntity, MiniStructureRenderState state,
                                   float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        state.data = blockEntity.getStructureData();
        if (logBudget > 20) {
            log("extract: pos=" + blockEntity.getBlockPos() + " data="
                    + (state.data == null ? "null" : state.data.totalBlocks() + " blocks"));
        }
    }

    @Override
    public void submit(MiniStructureRenderState state, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        StructureStatueData data = state.data;
        if (data == null || data.positions().length == 0) {
            return;
        }
        ClientLevel clientLevel = Minecraft.getInstance().level;
        if (clientLevel == null) {
            return;
        }

        // 等比缩放：统一取三轴最大尺寸的倒数，保持方块原比例不变形。
        // 之前按各轴分别归一化（各向异性缩放）会把宽扁建筑的方块竖直拉长，
        // 统一缩放后模型整体不超过 1 格，水平居中、贴底摆放
        float scale = 1.0f / Math.max(Math.max(data.width(), data.height()), data.depth());

        poseStack.pushPose();
        // 水平居中：模型实际宽度/深度小于 1 格时，把剩余空间平均分到两侧
        poseStack.translate((1.0f - data.width() * scale) / 2.0f, 0.0f,
                (1.0f - data.depth() * scale) / 2.0f);
        poseStack.scale(scale, scale, scale);

        int submitted = 0;
        for (int i = 0; i < data.positions().length; i++) {
            // 每个微缩方块使用独立的渲染状态（提交时按引用保存，不能复用同一实例）
            MovingBlockRenderState blockState = new MovingBlockRenderState();
            poseStack.pushPose();
            try {
                int packed = data.positions()[i];
                BlockState renderState = data.stateAt(i);
                // 雕像方块渲染形状为 INVISIBLE（世界中由方块实体渲染器绘制生物模型），
                // 微缩渲染走静态方块模型管线画不出它 —— 用材质对应的原版方块作替身，
                // 既保证可见又能从外观分辨石/铁/铜/金/钻/下界合金材质
                if (renderState.getBlock() instanceof StatueBlock) {
                    renderState = statueSubstituteBlock(data, packed);
                }
                blockState.blockState = renderState;
                // 用方块实体的真实世界坐标采样光照与生物群系颜色
                blockState.blockPos = state.blockPos;
                blockState.randomSeedPos = state.blockPos;
                blockState.biome = clientLevel.getBiome(state.blockPos);
                blockState.cardinalLighting = clientLevel.cardinalLighting();
                blockState.lightEngine = clientLevel.getLightEngine();

                poseStack.translate(StructureStatueData.unpackX(packed),
                        StructureStatueData.unpackY(packed),
                        StructureStatueData.unpackZ(packed));
                submitNodeCollector.submitMovingBlock(poseStack, blockState);
                submitted++;
            } catch (Throwable e) {
                // 单个方块模型损坏只跳过该方块，不应让整个微缩建筑渲染中断
                log("submit: block " + i + " threw " + e);
            } finally {
                poseStack.popPose();
            }
        }

        if (logBudget > 20) {
            log("submit: " + submitted + "/" + data.positions().length
                    + " blocks at uniform scale " + scale);
        }

        poseStack.popPose();
    }

    /**
     * 雕像方块在微缩模型中的替身方块：从压缩数据保存的方块实体 NBT
     * （StatueData.materialType）读取材质类型，取对应的原版方块。
     */
    private static BlockState statueSubstituteBlock(StructureStatueData data, int packed) {
        MaterialType material = MaterialType.STONE;
        CompoundTag tileTag = data.tileEntityData().get((long) packed);
        if (tileTag != null) {
            CompoundTag statueTag = tileTag.getCompoundOrEmpty("StatueData");
            if (statueTag.contains("materialType")) {
                material = MaterialType.fromEffectId(statueTag.getStringOr("materialType", "stone"));
            }
        }
        return material.getBlock().defaultBlockState();
    }

    @Override
    public int getViewDistance() {
        return ModConfigSpecHolder.statueRenderDistance;
    }

    public static class MiniStructureRenderState extends BlockEntityRenderState {
        public StructureStatueData data;
    }
}
