package com.xiong.petrification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.registry.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 雕像物品的无方块实体渲染器（BEWLR）：让手中 / GUI 里的雕像显示原生物 3D 模型。
 *
 * 1.21.1 无 net.minecraft.client.renderer.special（1.21.4+ 才有 SpecialModelRenderer），
 * 改用 Forge 通用的 BlockEntityWithoutLevelRenderer + IClientItemExtensions。
 * 触发条件：物品模型 parent = minecraft:builtin/entity，使 BakedModel.isCustomRenderer() 为 true。
 * 渲染逻辑复用 StatueBlockEntityRenderer.renderItemStatue，避免重复实现。
 */
public class StatueItemRenderer extends BlockEntityWithoutLevelRenderer {
    public StatueItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        StatueComponent component = stack.get(ModDataComponents.STATUE_DATA.get());
        // 创造模式直接拿出的空白雕像也显示一个默认石头雕像，避免出现完全不可见的物品
        if (component == null) {
            component = StatueComponent.defaultStatue(MaterialType.STONE);
        }
        StatueBlockEntityRenderer.renderItemStatue(component, poseStack, bufferSource, packedLight);
    }

    /** 供 IClientItemExtensions 延迟获取，避免类加载期触碰 Minecraft 实例。 */
    public static BlockEntityWithoutLevelRenderer create() {
        Minecraft mc = Minecraft.getInstance();
        return new StatueItemRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }
}
