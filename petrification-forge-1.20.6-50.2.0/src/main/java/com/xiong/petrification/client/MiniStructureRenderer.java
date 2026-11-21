package com.xiong.petrification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.block.MiniStructureBlockEntity;
import com.xiong.petrification.block.StatueBlock;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.component.StructureStatueData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public class MiniStructureRenderer implements BlockEntityRenderer<MiniStructureBlockEntity> {
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
    public void render(MiniStructureBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        StructureStatueData data = blockEntity.getStructureData();
        if (data == null || data.positions().length == 0) {
            return;
        }

        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();

        // 等比缩放：统一取三轴最大尺寸的倒数，保持方块比例不变形
        float scale = 1.0f / Math.max(Math.max(data.width(), data.height()), data.depth());

        poseStack.pushPose();
        poseStack.translate((1.0f - data.width() * scale) / 2.0f, 0.0f,
                (1.0f - data.depth() * scale) / 2.0f);
        poseStack.scale(scale, scale, scale);

        int submitted = 0;
        for (int i = 0; i < data.positions().length; i++) {
            poseStack.pushPose();
            try {
                int packed = data.positions()[i];
                BlockState renderState = data.stateAt(i);
                if (renderState.getBlock() instanceof StatueBlock) {
                    renderState = statueSubstituteBlock(data, packed);
                }

                poseStack.translate(StructureStatueData.unpackX(packed),
                        StructureStatueData.unpackY(packed),
                        StructureStatueData.unpackZ(packed));
                // renderSingleBlock 是原版独立方块渲染路径（物品/活塞等场景）：
                // 自动解析 BlockStateModel 部件、按模型渲染层分桶并处理生物群系染色。
                // 注意：1.21.5+ 的 renderBatched 需要显式传模型部件列表，传空列表会画不出任何东西。
                // 光照使用微缩方块自身位置的光（与原项目"在方块实体真实坐标采样光照"一致）
                blockRenderer.renderSingleBlock(renderState, poseStack, bufferSource,
                        packedLight, OverlayTexture.NO_OVERLAY);
                submitted++;
            } catch (Throwable e) {
                // 单个方块模型损坏只跳过该方块，不应让整个微缩建筑渲染中断
                log("render: block " + i + " threw " + e);
            } finally {
                poseStack.popPose();
            }
        }

        if (logBudget > 20) {
            log("render: " + submitted + "/" + data.positions().length
                    + " blocks at uniform scale " + scale);
        }

        poseStack.popPose();
    }

    private static BlockState statueSubstituteBlock(StructureStatueData data, int packed) {
        MaterialType material = MaterialType.STONE;
        CompoundTag tileTag = data.tileEntityData().get((long) packed);
        if (tileTag != null) {
            CompoundTag statueTag = tileTag.getCompound("StatueData");
            if (statueTag.contains("materialType")) {
                material = MaterialType.fromEffectId(statueTag.getString("materialType"));
            }
        }
        return material.getBlock().defaultBlockState();
    }

    @Override
    public int getViewDistance() {
        return ModConfigSpecHolder.statueRenderDistance;
    }
}
