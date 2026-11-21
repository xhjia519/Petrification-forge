package com.xiong.petrification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xiong.petrification.block.StatueBlockEntity;
import com.xiong.petrification.component.StatueComponent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * 雕像方块实体渲染器（1.21.1 传统实体渲染 API）。
 * 将记录在雕像数据里的实体快照还原成实体并渲染：活体走"贴材质色的模型直绘"，
 * 其它实体（船 / 末影水晶）回退到 EntityRenderDispatcher 普通渲染。
 */
public class StatueBlockEntityRenderer implements BlockEntityRenderer<StatueBlockEntity> {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static int logBudget = 30;

    private static void log(String message) {
        if (logBudget > 0) {
            logBudget--;
            LOGGER.info("[Statue] {}", message);
        }
    }

    private final EntityRenderDispatcher entityRenderDispatcher;
    private final Map<EntityType<?>, Entity> entityCache = new HashMap<>();

    public StatueBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.entityRenderDispatcher = context.getEntityRenderer();
    }

    @Override
    public void render(StatueBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        StatueComponent component = blockEntity.getStatueData();
        if (component == null) {
            return;
        }

        // 1.21.1：BuiltInRegistries.ENTITY_TYPE.get 直接返回 EntityType（可能为 null）。
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(component.entityType());
        if (entityType == null) {
            return;
        }

        Entity entity = entityCache.computeIfAbsent(entityType, type -> {
            Entity created = type.create(blockEntity.getLevel());
            if (created != null) {
                created.setId(-1);
            }
            return created;
        });

        if (entity == null) {
            return;
        }

        if (entity instanceof LivingEntity livingEntity) {
            applyCapturedNbt(livingEntity, component);
            applyPose(livingEntity, component);
        } else {
            entity.setYRot(component.yaw());
            entity.setXRot(component.pitch());
            entity.yRotO = component.yaw();
            entity.xRotO = component.pitch();
        }

        try {
            int materialColor = computeMaterialColor(component);
            LivingEntityRenderer<?, ?> livingRenderer = entity instanceof LivingEntity living
                    && entityRenderDispatcher.getRenderer(entity) instanceof LivingEntityRenderer<?, ?> ler
                    ? ler
                    : null;

            poseStack.pushPose();
            poseStack.translate(0.5, 0.0, 0.5);

            boolean tinted = false;
            if (livingRenderer != null) {
                poseStack.pushPose();
                try {
                    renderLivingTinted((LivingEntityRenderer) livingRenderer, (LivingEntity) entity,
                            component, poseStack, bufferSource, packedLight, partialTick, materialColor);
                    tinted = true;
                } catch (Throwable e) {
                    log("render: tinted path threw " + e);
                    tinted = false;
                } finally {
                    poseStack.popPose();
                }
            }

            if (!tinted) {
                try {
                    entityRenderDispatcher.render(entity, 0.0, 0.0, 0.0, entity.getYRot(), partialTick,
                            poseStack, bufferSource, packedLight);
                } catch (Exception e) {
                    // Silently ignore rendering errors
                }
            }

            poseStack.popPose();
        } catch (Exception e) {
            log("render: threw " + e);
        }
    }

    /** 用生物模型 + 材质色直绘（保留朝向 / 姿态 / 变体 / 装备层）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void renderLivingTinted(LivingEntityRenderer renderer, LivingEntity living,
                                           StatueComponent component, PoseStack poseStack,
                                           MultiBufferSource bufferSource, int packedLight,
                                           float partialTick, int materialColor) {
        EntityModel model = (EntityModel) renderer.getModel();
        ResourceLocation texture = renderer.getTextureLocation(living);
        RenderType renderType = model.renderType(texture);
        int tint = packMaterialColor(materialColor);

        float bodyRot = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, living.yHeadRotO, living.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - bodyRot);
        float headPitch = Mth.lerp(partialTick, living.xRotO, living.getXRot());
        float ageInTicks = 0.0f;
        float scale = living.getScale();
        float limbSwing = living.walkAnimation.position();
        float limbSwingAmount = living.walkAnimation.speed();

        model.attackTime = 0.0f;
        model.riding = false;
        model.young = living.isBaby();

        poseStack.scale(scale, scale, scale);
        invokeSetupRotations(renderer, living, poseStack, ageInTicks, bodyRot, partialTick, scale);
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        invokeScale(renderer, living, poseStack, partialTick);
        poseStack.translate(0.0f, -1.501f, 0.0f);

        model.prepareMobModel(living, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(living, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, tint);

        for (Object layerObj : getRenderLayers(renderer)) {
            ((RenderLayer) layerObj).render(poseStack, bufferSource, packedLight, living,
                    limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        }
    }

    private static final Map<EntityType<?>, Entity> ITEM_ENTITY_CACHE = new HashMap<>();

    /**
     * 物品级渲染（BEWLR 调用）：让手中 / GUI 里的雕像显示原生物 3D 模型。
     * 与方块渲染共用同一套贴材质色逻辑，坐标按物品模型处理（ItemRenderer 已平移 -0.5）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void renderItemStatue(StatueComponent component, PoseStack poseStack,
                                        MultiBufferSource bufferSource, int packedLight) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(component.entityType());
        if (entityType == null) {
            return;
        }
        Entity entity = ITEM_ENTITY_CACHE.computeIfAbsent(entityType, type -> {
            Entity created = type.create(level);
            if (created != null) {
                created.setId(-1);
            }
            return created;
        });
        if (entity == null) {
            return;
        }

        if (entity instanceof LivingEntity livingEntity) {
            applyCapturedNbt(livingEntity, component);
            applyPose(livingEntity, component);
        } else {
            entity.setYRot(component.yaw());
            entity.setXRot(component.pitch());
            entity.yRotO = component.yaw();
            entity.xRotO = component.pitch();
        }

        try {
            int materialColor = computeMaterialColor(component);
            LivingEntityRenderer<?, ?> livingRenderer = entity instanceof LivingEntity
                    && dispatcher.getRenderer(entity) instanceof LivingEntityRenderer<?, ?> ler ? ler : null;

            float fit = computeFitScale(entity);
            poseStack.pushPose();
            try {
                poseStack.translate(0.5, 0.0, 0.5);
                poseStack.scale(fit, fit, fit);

                boolean tinted = false;
                if (livingRenderer != null) {
                    poseStack.pushPose();
                    try {
                        renderLivingTinted((LivingEntityRenderer) livingRenderer, (LivingEntity) entity,
                                component, poseStack, bufferSource, packedLight, 0.0f, materialColor);
                        tinted = true;
                    } catch (Throwable e) {
                        tinted = false;
                    } finally {
                        poseStack.popPose();
                    }
                }
                if (!tinted) {
                    dispatcher.render(entity, 0.0, 0.0, 0.0, entity.getYRot(), 0.0f,
                            poseStack, bufferSource, packedLight);
                }
            } finally {
                poseStack.popPose();
            }
        } catch (Exception e) {
            // 物品渲染失败不应影响主流程
        }
    }

    /** 把任意大小的实体缩放进 1x1x1 物品格（0.9 留边）。 */
    private static float computeFitScale(Entity entity) {
        float width = Math.max(entity.getBbWidth(), 0.001f);
        float height = Math.max(entity.getBbHeight(), 0.001f);
        return Math.min(1.0f, Math.min(0.9f / width, 0.9f / height));
    }

    private static final Map<Entity, net.minecraft.nbt.CompoundTag> APPLIED_RENDER_NBT = new HashMap<>();

    private static void applyCapturedNbt(Entity entity, StatueComponent component) {
        component.entityNbt().ifPresent(tag -> {
            if (APPLIED_RENDER_NBT.get(entity) != tag) {
                APPLIED_RENDER_NBT.put(entity, tag);
                com.xiong.petrification.util.MaterializationHelper.loadEntityNbt(entity, tag);
            }
        });
    }

    public static void applyPose(LivingEntity livingEntity, StatueComponent component) {
        livingEntity.setYRot(component.yaw());
        livingEntity.setXRot(component.pitch());
        livingEntity.xRotO = component.pitch();
        livingEntity.yRotO = component.yaw();
        livingEntity.yHeadRot = component.headYaw();
        livingEntity.yHeadRotO = component.headYaw();
        livingEntity.yBodyRot = component.bodyYaw();
        livingEntity.yBodyRotO = component.bodyYaw();

        livingEntity.setShiftKeyDown(component.isSneaking());
        livingEntity.setSprinting(component.isSprinting());
        livingEntity.setSwimming(component.isSwimming());

        setWalkAnimation(livingEntity, component.limbSwing(), component.limbSwingAmount());

        if (livingEntity instanceof net.minecraft.world.entity.Mob mob) {
            mob.setBaby(component.isBaby());
        }

        applyVariant(livingEntity, component);
        applyEquipment(livingEntity, component);
    }

    private static void applyVariant(LivingEntity livingEntity, StatueComponent component) {
        com.xiong.petrification.util.MaterializationHelper.applyVariant(livingEntity, component);
    }

    private static void applyEquipment(LivingEntity livingEntity, StatueComponent component) {
        com.xiong.petrification.util.MaterializationHelper.applyEquipment(livingEntity, component.equipment());
    }

    private static java.lang.reflect.Field walkPositionField;

    public static void setWalkAnimation(LivingEntity livingEntity, float position, float speed) {
        livingEntity.walkAnimation.setSpeed(speed);
        try {
            if (walkPositionField == null) {
                walkPositionField = net.minecraft.world.entity.WalkAnimationState.class.getDeclaredField("position");
                walkPositionField.setAccessible(true);
            }
            walkPositionField.setFloat(livingEntity.walkAnimation, position);
        } catch (Exception e) {
            // Silently ignore
        }
    }

    public static int computeMaterialColor(StatueComponent component) {
        com.xiong.petrification.component.MaterialType type = component.materialType();
        int base = type.getColor();
        if (type == com.xiong.petrification.component.MaterialType.COPPER) {
            float t = Math.max(0.0f, Math.min(1.0f, component.oxidation()));
            base = lerpColor(base, 0x4C9F82, t);
        }
        return base;
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int packMaterialColor(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int br = Math.min(255, Math.round(r * 0.85f + 255 * 0.15f));
        int bg = Math.min(255, Math.round(g * 0.85f + 255 * 0.15f));
        int bb = Math.min(255, Math.round(b * 0.85f + 255 * 0.15f));
        return 0xFF000000 | (br << 16) | (bg << 8) | bb;
    }

    private static java.lang.reflect.Field layersField;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static java.util.List getRenderLayers(LivingEntityRenderer<?, ?> renderer) {
        try {
            if (layersField == null) {
                layersField = LivingEntityRenderer.class.getDeclaredField("layers");
                layersField.setAccessible(true);
            }
            return (java.util.List) layersField.get(renderer);
        } catch (Throwable e) {
            return java.util.List.of();
        }
    }

    private static final Map<Class<?>, java.lang.reflect.Method> SETUP_ROTATIONS_CACHE = new HashMap<>();
    private static final Map<Class<?>, java.lang.reflect.Method> SCALE_CACHE = new HashMap<>();

    /** 1.21.1：setupRotations(T, PoseStack, ageInTicks, bodyRot, partialTick, scale)。 */
    public static void invokeSetupRotations(LivingEntityRenderer<?, ?> renderer, LivingEntity entity,
                                            PoseStack poseStack, float ageInTicks, float bodyRot,
                                            float partialTick, float scale) {
        java.lang.reflect.Method m = findMethodCached(renderer.getClass(), SETUP_ROTATIONS_CACHE, "setupRotations",
                LivingEntity.class, PoseStack.class, float.class, float.class, float.class, float.class);
        if (m != null) {
            try {
                m.invoke(renderer, entity, poseStack, ageInTicks, bodyRot, partialTick, scale);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void invokeScale(LivingEntityRenderer<?, ?> renderer, LivingEntity entity,
                                   PoseStack poseStack, float partialTick) {
        java.lang.reflect.Method m = findMethodCached(renderer.getClass(), SCALE_CACHE, "scale",
                LivingEntity.class, PoseStack.class, float.class);
        if (m != null) {
            try {
                m.invoke(renderer, entity, poseStack, partialTick);
            } catch (Throwable ignored) {
            }
        }
    }

    private static java.lang.reflect.Method findMethodCached(Class<?> clazz, Map<Class<?>, java.lang.reflect.Method> cache,
                                                             String name, Class<?>... params) {
        java.lang.reflect.Method cached = cache.get(clazz);
        if (cached != null) {
            return cached;
        }
        java.lang.reflect.Method found = null;
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                found = c.getDeclaredMethod(name, params);
                break;
            } catch (NoSuchMethodException e) {
                // Keep searching up the hierarchy
            }
        }
        if (found != null) {
            found.setAccessible(true);
        }
        cache.put(clazz, found);
        return found;
    }

    @Override
    public boolean shouldRenderOffScreen(StatueBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.xiong.petrification.ModConfigSpecHolder.statueRenderDistance;
    }
}
