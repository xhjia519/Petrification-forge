package com.xiong.petrification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xiong.petrification.block.StatueBlockEntity;
import com.xiong.petrification.component.StatueComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay, Vec3 cameraPos) {
        StatueComponent component = blockEntity.getStatueData();
        if (component == null) {
            return;
        }

        Optional<? extends net.minecraft.core.Holder.Reference<EntityType<?>>> entityRef =
                BuiltInRegistries.ENTITY_TYPE.get(component.entityType());
        if (entityRef.isEmpty()) {
            return;
        }

        EntityType<?> entityType = entityRef.get().value();
        Entity entity = entityCache.computeIfAbsent(entityType, type -> {
            Entity created = type.create(blockEntity.getLevel(), EntitySpawnReason.LOAD);
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
            EntityRenderer<? super Entity, ?> renderer = entityRenderDispatcher.getRenderer(entity);
            EntityRenderState state = renderer.createRenderState(entity, partialTick);
            freezeAnimatedFields(state, component);
            forceBabyState(state, component);

            float scale = 1.0f;
            int materialColor = computeMaterialColor(component);
            LivingEntityRenderer<?, ?, ?> livingRenderer =
                    renderer instanceof LivingEntityRenderer<?, ?, ?> ler ? ler : null;

            poseStack.pushPose();
            poseStack.translate(0.5, 0.0, 0.5);
            poseStack.scale(scale, scale, scale);

            boolean tinted = false;
            if (livingRenderer != null && state instanceof LivingEntityRenderState livingState) {
                poseStack.pushPose();
                try {
                    dryRunModelSelection(livingRenderer, livingState);
                    LivingEntityRenderer rawRenderer = (LivingEntityRenderer) livingRenderer;
                    net.minecraft.client.model.EntityModel model = (net.minecraft.client.model.EntityModel) rawRenderer.getModel();
                    ResourceLocation texture = rawRenderer.getTextureLocation(livingState);
                    RenderType renderType = model.renderType(texture);
                    int tint = packMaterialColor(materialColor);

                    float livingScale = livingState.scale;
                    poseStack.scale(livingScale, livingScale, livingScale);
                    invokeSetupRotations(livingRenderer, livingState, poseStack, livingState.bodyRot);
                    poseStack.scale(-1.0f, -1.0f, 1.0f);
                    invokeScale(livingRenderer, livingState, poseStack);
                    poseStack.translate(0.0f, -1.501f, 0.0f);

                    VertexConsumer consumer = bufferSource.getBuffer(renderType);
                    model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, tint);

                    model.setupAnim(livingState);
                    for (Object layerObj : getRenderLayers(livingRenderer)) {
                        ((RenderLayer) layerObj).render(
                                poseStack, bufferSource, packedLight,
                                livingState, livingState.yRot, livingState.xRot);
                    }
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
                    entityRenderDispatcher.render(state, 0.0, 0.0, 0.0, poseStack, bufferSource, packedLight);
                } catch (Exception e) {
                    // Silently ignore rendering errors
                }
            }

            poseStack.popPose();
        } catch (Exception e) {
            log("render: threw " + e);
        }
    }

    private static void freezeAnimatedFields(EntityRenderState renderState, StatueComponent component) {
        renderState.ageInTicks = 0.0f;
        if (renderState instanceof LivingEntityRenderState living) {
            living.isInWater = false;
            living.xRot = component.pitch();
            living.walkAnimationPos = component.limbSwing();
            living.walkAnimationSpeed = component.limbSwingAmount();
        }
        if (renderState instanceof net.minecraft.client.renderer.entity.state.AllayRenderState allay) {
            allay.isDancing = false;
            allay.isSpinning = false;
            allay.spinningProgress = 0.0f;
            allay.holdingAnimationProgress = hasMainHandItem(component) ? 1.0f : 0.0f;
        }
        if (renderState instanceof net.minecraft.client.renderer.entity.state.ParrotRenderState parrot) {
            parrot.flapAngle = 0.0f;
        }
        if (renderState instanceof net.minecraft.client.renderer.entity.state.IllagerRenderState illager) {
            component.entityNbt().ifPresent(tag -> {
                boolean aggressive = tag.getByteOr("PetrificationAggressive", (byte) 0) != 0;
                boolean celebrating = tag.getByteOr("PetrificationCelebrating", (byte) 0) != 0;
                boolean casting = tag.getByteOr("PetrificationCasting", (byte) 0) != 0;
                illager.armPose = casting
                        ? net.minecraft.world.entity.monster.AbstractIllager.IllagerArmPose.SPELLCASTING
                        : aggressive
                                ? net.minecraft.world.entity.monster.AbstractIllager.IllagerArmPose.ATTACKING
                                : celebrating
                                        ? net.minecraft.world.entity.monster.AbstractIllager.IllagerArmPose.CELEBRATING
                                        : illager.armPose;
                illager.isAggressive = aggressive;
                if (renderState instanceof net.minecraft.client.renderer.entity.state.EvokerRenderState evoker) {
                    evoker.isCastingSpell = casting;
                }
            });
        }
    }

    private static boolean hasMainHandItem(StatueComponent component) {
        return component.equipment().map(eq -> !eq.getCompoundOrEmpty("mainHand").isEmpty()).orElse(false);
    }

    private static void forceBabyState(EntityRenderState renderState, StatueComponent component) {
        if (component.isBaby()
                && renderState instanceof LivingEntityRenderState living
                && !living.isBaby) {
            living.isBaby = true;
            living.ageScale = 0.5f;
        }
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

    public static void renderItemStatue(StatueComponent component, PoseStack poseStack,
                                        MultiBufferSource bufferSource, int lightCoords) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        Optional<? extends net.minecraft.core.Holder.Reference<EntityType<?>>> entityRef =
                BuiltInRegistries.ENTITY_TYPE.get(component.entityType());
        if (entityRef.isEmpty()) {
            return;
        }
        EntityType<?> entityType = entityRef.get().value();
        Entity entity = ITEM_ENTITY_CACHE.computeIfAbsent(entityType, type -> {
            Entity created = type.create(level, EntitySpawnReason.LOAD);
            if (created != null) {
                created.setId(-1);
            }
            return created;
        });
        if (entity == null) {
            return;
        }
        if (!(entity instanceof LivingEntity livingEntity)) {
            try {
                entity.setYRot(component.yaw());
                entity.setXRot(component.pitch());
                entity.yRotO = component.yaw();
                entity.xRotO = component.pitch();
                EntityRenderer<? super Entity, ?> plainRenderer = dispatcher.getRenderer(entity);
                EntityRenderState plainState = plainRenderer.createRenderState(entity, 0.0f);
                freezeAnimatedFields(plainState, component);
                float fit = entity instanceof net.minecraft.world.entity.vehicle.AbstractBoat
                        ? computeFitScale(entity)
                        : 1.0f;
                poseStack.pushPose();
                try {
                    poseStack.translate(0.5f, 0.0f, 0.5f);
                    if (fit != 1.0f) {
                        poseStack.scale(fit, fit, fit);
                    }
                    dispatcher.render(plainState, 0.0, 0.0, 0.0, poseStack, bufferSource, lightCoords);
                } finally {
                    poseStack.popPose();
                }
            } catch (Throwable e) {
                // Silently ignore
            }
            return;
        }
        applyCapturedNbt(livingEntity, component);
        applyPose(livingEntity, component);

        try {
            EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(livingEntity);
            EntityRenderState state = renderer.createRenderState(livingEntity, 0.0f);
            freezeAnimatedFields(state, component);
            forceBabyState(state, component);
            if (!(state instanceof LivingEntityRenderState livingState)
                    || !(renderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)) {
                float fit = computeFitScale(livingEntity);
                poseStack.pushPose();
                try {
                    poseStack.translate(0.5f, 0.0f, 0.5f);
                    poseStack.scale(fit, fit, fit);
                    dispatcher.render(state, 0.0, 0.0, 0.0, poseStack, bufferSource, lightCoords);
                } finally {
                    poseStack.popPose();
                }
                return;
            }

            dryRunModelSelection(livingRenderer, livingState);
            LivingEntityRenderer rawRenderer = (LivingEntityRenderer) livingRenderer;
            net.minecraft.client.model.EntityModel model = (net.minecraft.client.model.EntityModel) rawRenderer.getModel();
            ResourceLocation texture = rawRenderer.getTextureLocation(livingState);
            RenderType renderType = model.renderType(texture);
            int tint = packMaterialColor(computeMaterialColor(component));

            float fit = computeFitScale(livingEntity);
            poseStack.pushPose();
            try {
                poseStack.translate(0.5f, 0.0f, 0.5f);
                poseStack.scale(fit, fit, fit);

                float livingScale = livingState.scale;
                poseStack.scale(livingScale, livingScale, livingScale);
                invokeSetupRotations(livingRenderer, livingState, poseStack, livingState.bodyRot);
                poseStack.scale(-1.0f, -1.0f, 1.0f);
                invokeScale(livingRenderer, livingState, poseStack);
                poseStack.translate(0.0f, -1.501f, 0.0f);

                VertexConsumer consumer = bufferSource.getBuffer(renderType);
                model.renderToBuffer(poseStack, consumer, lightCoords, OverlayTexture.NO_OVERLAY, tint);

                model.setupAnim(livingState);
                for (Object layerObj : getRenderLayers(livingRenderer)) {
                    ((RenderLayer) layerObj).render(
                            poseStack, bufferSource, lightCoords,
                            livingState, livingState.yRot, livingState.xRot);
                }
            } finally {
                poseStack.popPose();
            }
        } catch (Throwable e) {
            // Silently ignore
        }
    }

    private static final Map<EntityType<?>, Entity> ITEM_ENTITY_CACHE = new HashMap<>();

    private static float computeFitScale(Entity entity) {
        float width = Math.max(entity.getBbWidth(), 0.001f);
        float height = Math.max(entity.getBbHeight(), 0.001f);
        return Math.min(1.0f, Math.min(0.9f / width, 0.9f / height));
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

    private static final VertexConsumer NOOP_VERTEX_CONSUMER = new VertexConsumer() {
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    };

    private static final MultiBufferSource NOOP_BUFFER = (MultiBufferSource) java.lang.reflect.Proxy.newProxyInstance(
            StatueBlockEntityRenderer.class.getClassLoader(),
            new Class<?>[]{MultiBufferSource.class},
            (proxy, method, args) -> {
                if (method.getReturnType() == VertexConsumer.class) {
                    return NOOP_VERTEX_CONSUMER;
                }
                if (method.getReturnType().isInstance(proxy)) {
                    return proxy;
                }
                if (method.getReturnType() == boolean.class) {
                    return false;
                }
                if (method.getReturnType() == int.class) {
                    return 0;
                }
                if (method.getReturnType() == long.class) {
                    return 0L;
                }
                if (method.getReturnType() == float.class) {
                    return 0.0f;
                }
                if (method.getReturnType() == double.class) {
                    return 0.0d;
                }
                return null;
            });

    private static void dryRunModelSelection(LivingEntityRenderer<?, ?, ?> renderer,
                                             LivingEntityRenderState livingState) {
        try {
            ((EntityRenderer) renderer).render(livingState, new PoseStack(), NOOP_BUFFER, 0);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static java.util.List getRenderLayers(LivingEntityRenderer<?, ?, ?> renderer) {
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
    private static final Map<Class<?>, java.lang.reflect.Method> SETUP_ROTATIONS_CACHE3 = new HashMap<>();
    private static final Map<Class<?>, java.lang.reflect.Method> SCALE_CACHE = new HashMap<>();

    public static void invokeSetupRotations(LivingEntityRenderer<?, ?, ?> renderer,
                                             LivingEntityRenderState state,
                                             PoseStack poseStack, float bodyRot) {
        // 1.21.2+: setupRotations(state, poseStack, bodyRot, scale) 为 4 参数；旧渲染状态版本为 3 参数
        java.lang.reflect.Method m = findMethodCached(renderer.getClass(), SETUP_ROTATIONS_CACHE, "setupRotations",
                LivingEntityRenderState.class, PoseStack.class, float.class, float.class);
        if (m != null) {
            try {
                m.invoke(renderer, state, poseStack, bodyRot, state.scale);
            } catch (Throwable ignored) {
            }
            return;
        }
        m = findMethodCached(renderer.getClass(), SETUP_ROTATIONS_CACHE3, "setupRotations",
                LivingEntityRenderState.class, PoseStack.class, float.class);
        if (m != null) {
            try {
                m.invoke(renderer, state, poseStack, bodyRot);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void invokeScale(LivingEntityRenderer<?, ?, ?> renderer,
                                    LivingEntityRenderState state,
                                    PoseStack poseStack) {
        java.lang.reflect.Method m = findMethodCached(renderer.getClass(), SCALE_CACHE, "scale",
                LivingEntityRenderState.class, PoseStack.class);
        if (m != null) {
            try {
                m.invoke(renderer, state, poseStack);
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
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.xiong.petrification.ModConfigSpecHolder.statueRenderDistance;
    }
}
