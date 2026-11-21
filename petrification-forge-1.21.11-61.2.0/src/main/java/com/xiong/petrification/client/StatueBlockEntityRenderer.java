package com.xiong.petrification.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xiong.petrification.block.StatueBlockEntity;
import com.xiong.petrification.component.StatueComponent;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class StatueBlockEntityRenderer implements BlockEntityRenderer<StatueBlockEntity, StatueBlockEntityRenderer.StatueRenderState> {
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
        this.entityRenderDispatcher = context.entityRenderer();
    }

    @Override
    public StatueRenderState createRenderState() {
        return new StatueRenderState();
    }

    @Override
    public void extractRenderState(StatueBlockEntity blockEntity, StatueRenderState state,
                                   float partialTicks, Vec3 cameraPosition,
                                   net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);

        StatueComponent component = blockEntity.getStatueData();
        if (component == null) {
            log("extract: component null");
            state.entityRenderState = null;
            return;
        }

        Optional<? extends net.minecraft.core.Holder.Reference<EntityType<?>>> entityRef =
                BuiltInRegistries.ENTITY_TYPE.get(component.entityType());
        if (entityRef.isEmpty()) {
            log("extract: entityRef empty for " + component.entityType());
            state.entityRenderState = null;
            return;
        }

        EntityType<?> entityType = entityRef.get().value();
        Entity entity = entityCache.computeIfAbsent(entityType, type -> {
            Entity created = type.create(blockEntity.getLevel(), EntitySpawnReason.LOAD);
            if (created != null) {
                // The renderer reads entity.getId(); a dummy entity never added to the level has no
                // ID and would throw "Tried to access entity ID before ID assignment". Assign one like
                // the vanilla spawner's display entity.
                created.setId(-1);
            }
            return created;
        });

        if (entity == null) {
            log("extract: entity creation failed");
            state.entityRenderState = null;
            return;
        }

        if (entity instanceof LivingEntity livingEntity) {
            applyCapturedNbt(livingEntity, component);
            applyPose(livingEntity, component);
        } else {
            // Non-living targets (boat / end crystal): align the cached dummy with the captured
            // orientation so the renderer's render state extracts the statue yaw. yRotO is pinned
            // too: the boat renderer lerps getYRot(partialTick) between yRotO and yRot, so a
            // stale yRotO makes the boat statue spin every frame.
            entity.setYRot(component.yaw());
            entity.setXRot(component.pitch());
            entity.yRotO = component.yaw();
            entity.xRotO = component.pitch();
        }

        try {
            EntityRenderer<? super Entity, ?> renderer = entityRenderDispatcher.getRenderer(entity);
            state.entityRenderState = renderer.createRenderState(entity, partialTicks);
            freezeAnimatedFields(state.entityRenderState, component);
            forceBabyState(state.entityRenderState, component);
            state.yaw = component.yaw();
            // Render at the mob's natural size (like the reference video); no fit-to-block scaling.
            state.scale = 1.0f;
            state.materialColor = computeMaterialColor(component);
            if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> livingRenderer) {
                state.livingRenderer = livingRenderer;
            }
            log("extract: ok state=" + state.entityRenderState.getClass().getSimpleName()
                    + " living=" + (state.livingRenderer != null));
        } catch (Exception e) {
            log("extract: createRenderState threw " + e);
            state.entityRenderState = null;
        }
    }

    private static void freezeAnimatedFields(EntityRenderState renderState, StatueComponent component) {
        // Pin ageInTicks: it is tickCount + partialTicks, so on a never-ticked dummy it keeps
        // advancing every frame and drives the fish tail / tadpole / allay / nautilus idle wiggle.
        renderState.ageInTicks = 0.0f;
        if (renderState instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
            living.isInWater = false;
            living.xRot = component.pitch();
            living.walkAnimationPos = component.limbSwing();
            living.walkAnimationSpeed = component.limbSwingAmount();
        }
        if (renderState instanceof net.minecraft.client.renderer.entity.state.AllayRenderState allay) {
            allay.isDancing = false;
            allay.isSpinning = false;
            allay.spinningProgress = 0.0f;
            // The raised-arm pose is driven by holdingAnimationProgress; the dummy never ticks it
            // up, so pin it fully raised whenever the statue holds an item.
            allay.holdingAnimationProgress = hasMainHandItem(component) ? 1.0f : 0.0f;
        }
        if (renderState instanceof net.minecraft.client.renderer.entity.state.ParrotRenderState parrot) {
            parrot.flapAngle = 0.0f;
        }
        if (renderState instanceof net.minecraft.client.renderer.entity.state.IllagerRenderState illager) {
            // Arm poses (vindicator axe raise / evoker spell casting / raid celebrating) are
            // runtime-only and never written into vanilla NBT; they were parked in custom keys
            // at capture time and are mapped back onto the render state here. Without a custom
            // key the pose extracted from the dummy is kept (e.g. the pillager's CROSSBOW_HOLD
            // / NEUTRAL), instead of forcing every illager statue into the crossed-arms pose.
            component.entityNbt().ifPresent(tag -> {
                boolean aggressive = tag.getByteOr("PetrificationAggressive", (byte) 0) != 0;
                boolean celebrating = tag.getByteOr("PetrificationCelebrating", (byte) 0) != 0;
                boolean casting = tag.getByteOr("PetrificationCasting", (byte) 0) != 0;
                illager.armPose = casting
                        ? net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose.SPELLCASTING
                        : aggressive
                                ? net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose.ATTACKING
                                : celebrating
                                        ? net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose.CELEBRATING
                                        : illager.armPose;
                // The vindicator renderer only submits its held-item layer (the iron axe) while
                // isAggressive is set; the extractor reads it from the dummy whose isAggressive
                // is always false (it is a runtime flag, not an NBT field), so it must be pinned
                // here or the axe never renders.
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

    // Safety net for mobs whose dummy setBaby is a no-op: pin the baby fields on the render state.
    private static void forceBabyState(EntityRenderState renderState, StatueComponent component) {
        if (component.isBaby()
                && renderState instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living
                && !living.isBaby) {
            living.isBaby = true;
            living.ageScale = 0.5f;
        }
    }

    private static final Map<Entity, net.minecraft.nbt.CompoundTag> APPLIED_RENDER_NBT = new HashMap<>();

    /**
     * Loads the captured full entity NBT onto the cached render dummy before posing it, so the
     * statue shows tamed/owned state too (collar colour, sitting pose, wolf armour, variant
     * texture) instead of a default wild mob. Keyed by tag identity: the same statue re-extracting
     * every frame skips the re-load, only a different statue re-applies its own NBT.
     */
    private static void applyCapturedNbt(Entity entity, StatueComponent component) {
        component.entityNbt().ifPresent(tag -> {
            if (APPLIED_RENDER_NBT.get(entity) != tag) {
                APPLIED_RENDER_NBT.put(entity, tag);
                com.xiong.petrification.util.MaterializationHelper.loadEntityNbt(entity, tag);
            }
        });
    }

    public static void applyPose(LivingEntity livingEntity, StatueComponent component) {
        // Orientation
        livingEntity.setYRot(component.yaw());
        livingEntity.setXRot(component.pitch());
        livingEntity.xRotO = component.pitch();
        livingEntity.yRotO = component.yaw();
        livingEntity.yHeadRot = component.headYaw();
        livingEntity.yHeadRotO = component.headYaw();
        livingEntity.yBodyRot = component.bodyYaw();
        livingEntity.yBodyRotO = component.bodyYaw();

        // Pose flags
        livingEntity.setShiftKeyDown(component.isSneaking());
        livingEntity.setSprinting(component.isSprinting());
        livingEntity.setSwimming(component.isSwimming());

        // Frozen walk animation (limb pose)
        setWalkAnimation(livingEntity, component.limbSwing(), component.limbSwingAmount());

        // Dispatch through Mob#setBaby (not just AgeableMob): zombies/husks/drowned and
        // piglins implement their own baby flag without extending AgeableMob, so an
        // instanceof AgeableMob check leaves their baby statues rendered as adults.
        if (livingEntity instanceof net.minecraft.world.entity.Mob mob) {
            mob.setBaby(component.isBaby());
        }

        applyVariant(livingEntity, component);
        applyEquipment(livingEntity, component);
    }

    /** Re-applies captured per-mob variant data (slime size / villager profession) onto the dummy entity. */
    private static void applyVariant(LivingEntity livingEntity, StatueComponent component) {
        com.xiong.petrification.util.MaterializationHelper.applyVariant(livingEntity, component);
    }

    /**
     * Re-applies captured equipment (saddle, armour, held items) onto the cached dummy with a
     * two-way overwrite, so no state leaks between statues sharing one cached entity.
     */
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
            // Silently ignore: limb pose falls back to neutral
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void submit(StatueRenderState state, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.entityRenderState == null) {
            log("submit: entityRenderState null");
            return;
        }

        // Light the statue from the block's position, like the vanilla spawner copies the
        // block light onto its display entity; otherwise the dummy entity at (0,0,0) is black.
        state.entityRenderState.lightCoords = state.lightCoords;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        // Orientation is handled by setupRotations (180 - bodyRot) inside the vanilla transform
        // chain; applying an extra yaw rotation here would double-rotate and flip the statue.
        poseStack.scale(state.scale, state.scale, state.scale);

        boolean tinted = false;
        if (state.livingRenderer != null && state.entityRenderState instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState livingState) {
            // Isolate the tinted path on its own pose so a failure never corrupts the
            // outer transform used by the reliable fallback below.
            poseStack.pushPose();
            try {
                dryRunModelSelection(state.livingRenderer, livingState, camera);
                net.minecraft.client.model.Model model = (net.minecraft.client.model.Model) state.livingRenderer.getModel();
                // Use the mob's original texture, and apply the material colour as a tint (shader)
                // so the statue keeps the mob's look while reading as carved from that material.
                net.minecraft.resources.Identifier texture =
                        ((net.minecraft.client.renderer.entity.LivingEntityRenderer) state.livingRenderer).getTextureLocation(livingState);
                net.minecraft.client.renderer.rendertype.RenderType renderType = model.renderType(texture);
                // Material shader: multiply the material/oxidation colour over the mob texture.
                int tint = packMaterialColor(state.materialColor);

                // Replicate the vanilla LivingEntityRenderer.submit inner transform chain so the
                // model is positioned/oriented exactly as the normal renderer does it.
                float livingScale = livingState.scale;
                poseStack.scale(livingScale, livingScale, livingScale);
                invokeSetupRotations(state.livingRenderer, livingState, poseStack, livingState.bodyRot, livingScale);
                poseStack.scale(-1.0f, -1.0f, 1.0f);
                invokeScale(state.livingRenderer, livingState, poseStack);
                poseStack.translate(0.0f, -1.501f, 0.0f);

                submitNodeCollector.submitModel(
                        model, livingState, poseStack, renderType,
                        livingState.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                        tint, null, 0, null);

                // Render all feature layers (wool, held items, slime core, profession, armour)
                // so the statue matches the full mob model, not just the base body.
                model.setupAnim(livingState);
                for (Object layerObj : getRenderLayers(state.livingRenderer)) {
                    ((net.minecraft.client.renderer.entity.layers.RenderLayer) layerObj).submit(
                            poseStack, submitNodeCollector, livingState.lightCoords,
                            livingState, livingState.yRot, livingState.xRot);
                }
                tinted = true;
                log("submit: tinted model submitted");
            } catch (Throwable e) {
                log("submit: tinted path threw " + e);
                tinted = false;
            } finally {
                poseStack.popPose();
            }
        }

        if (!tinted) {
            log("submit: using fallback dispatcher.submit");
            try {
                // The poseStack is already positioned at the block (camera-relative), so the
                // entity submit offset must be 0,0,0 like the vanilla spawner renderer.
                entityRenderDispatcher.submit(state.entityRenderState, camera,
                        0.0, 0.0, 0.0,
                        poseStack, submitNodeCollector);
            } catch (Exception e) {
                // Silently ignore rendering errors
            }
        }

        poseStack.popPose();
    }

    /**
     * Renders a statue item (held / in GUI) as the original mob model with the material
     * block texture. Reuses the cached dummy-entity + tinted-model path.
     */
    public static void renderItemStatue(StatueComponent component, PoseStack poseStack,
                                        SubmitNodeCollector submitNodeCollector, int lightCoords) {
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
            // Non-living statue (boat / end crystal): submit through the plain entity renderer,
            // oriented like the captured entity. Only boats are fit-scaled (they are wider than
            // a block); end crystals keep their previous rendering unchanged.
            try {
                entity.setYRot(component.yaw());
                entity.setXRot(component.pitch());
                entity.yRotO = component.yaw();
                entity.xRotO = component.pitch();
                EntityRenderer<? super Entity, ?> plainRenderer = dispatcher.getRenderer(entity);
                EntityRenderState plainState = plainRenderer.createRenderState(entity, 0.0f);
                freezeAnimatedFields(plainState, component);
                plainState.lightCoords = lightCoords;
                float fit = entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat
                        ? computeFitScale(entity)
                        : 1.0f;
                poseStack.pushPose();
                try {
                    poseStack.translate(0.5f, 0.0f, 0.5f);
                    if (fit != 1.0f) {
                        poseStack.scale(fit, fit, fit);
                    }
                    dispatcher.submit(plainState, null, 0.0, 0.0, 0.0, poseStack, submitNodeCollector);
                } finally {
                    poseStack.popPose();
                }
            } catch (Throwable e) {
                // Silently ignore: a broken item render must never crash the game
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
            if (!(state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState livingState)
                    || !(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> livingRenderer)) {
                // Some living entities (the ender dragon) use a renderer that is not a
                // LivingEntityRenderer; submit it through the plain path so the item statue
                // still shows up (untinted), scaled to fit like the other statues.
                float fit = computeFitScale(livingEntity);
                state.lightCoords = lightCoords;
                poseStack.pushPose();
                try {
                    poseStack.translate(0.5f, 0.0f, 0.5f);
                    poseStack.scale(fit, fit, fit);
                    dispatcher.submit(state, null, 0.0, 0.0, 0.0, poseStack, submitNodeCollector);
                } finally {
                    poseStack.popPose();
                }
                return;
            }
            livingState.lightCoords = lightCoords;

            dryRunModelSelection(livingRenderer, livingState, null);
            net.minecraft.client.model.Model model = (net.minecraft.client.model.Model) livingRenderer.getModel();
            net.minecraft.resources.Identifier texture =
                    ((net.minecraft.client.renderer.entity.LivingEntityRenderer) livingRenderer).getTextureLocation(livingState);
            net.minecraft.client.renderer.rendertype.RenderType renderType = model.renderType(texture);
            int tint = packMaterialColor(computeMaterialColor(component));

            float fit = computeFitScale(livingEntity);
            poseStack.pushPose();
            try {
                poseStack.translate(0.5f, 0.0f, 0.5f);
                poseStack.scale(fit, fit, fit);

                float livingScale = livingState.scale;
                poseStack.scale(livingScale, livingScale, livingScale);
                invokeSetupRotations(livingRenderer, livingState, poseStack, livingState.bodyRot, livingScale);
                poseStack.scale(-1.0f, -1.0f, 1.0f);
                invokeScale(livingRenderer, livingState, poseStack);
                poseStack.translate(0.0f, -1.501f, 0.0f);

                submitNodeCollector.submitModel(
                        model, livingState, poseStack, renderType,
                        livingState.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                        tint, null, 0, null);

                // Submit the feature layers too (villager/zombie-villager profession clothes,
                // armour, wool, held items); without them item statues render naked.
                model.setupAnim(livingState);
                for (Object layerObj : getRenderLayers(livingRenderer)) {
                    ((net.minecraft.client.renderer.entity.layers.RenderLayer) layerObj).submit(
                            poseStack, submitNodeCollector, livingState.lightCoords,
                            livingState, livingState.yRot, livingState.xRot);
                }
            } finally {
                poseStack.popPose();
            }
        } catch (Throwable e) {
            // Silently ignore: a broken item render must never crash the game
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
            // Copper statues gradually turn to the oxidized teal as oxidation rises,
            // mirroring the copper-oxidation behaviour shown in the reference video.
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
        // Blend 85% material with 15% white for a strong material tint that still shows detail.
        int br = Math.min(255, Math.round(r * 0.85f + 255 * 0.15f));
        int bg = Math.min(255, Math.round(g * 0.85f + 255 * 0.15f));
        int bb = Math.min(255, Math.round(b * 0.85f + 255 * 0.15f));
        return 0xFF000000 | (br << 16) | (bg << 8) | bb;
    }

    private static java.lang.reflect.Field layersField;

    // Many renderers pick their actual model inside submit() (ageable adult/baby pairs, cow and
    // chicken variant pairs, salmon sizes, pufferfish puff models) by rewriting their shared `model`
    // field. A dry-run submit against a no-op collector lets the renderer make that choice for our
    // state so getModel() afterwards returns the correct model.
    private static final SubmitNodeCollector NOOP_COLLECTOR = (SubmitNodeCollector) java.lang.reflect.Proxy.newProxyInstance(
            StatueBlockEntityRenderer.class.getClassLoader(),
            new Class<?>[]{SubmitNodeCollector.class},
            (proxy, method, args) -> {
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

    private static void dryRunModelSelection(net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> renderer,
                                             net.minecraft.client.renderer.entity.state.LivingEntityRenderState livingState,
                                             CameraRenderState camera) {
        try {
            ((net.minecraft.client.renderer.entity.EntityRenderer) renderer)
                    .submit(livingState, new PoseStack(), NOOP_COLLECTOR, camera);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static java.util.List getRenderLayers(net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> renderer) {
        try {
            if (layersField == null) {
                layersField = net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredField("layers");
                layersField.setAccessible(true);
            }
            return (java.util.List) layersField.get(renderer);
        } catch (Throwable e) {
            return java.util.List.of();
        }
    }

    private static final Map<Class<?>, java.lang.reflect.Method> SETUP_ROTATIONS_CACHE = new HashMap<>();
    private static final Map<Class<?>, java.lang.reflect.Method> SCALE_CACHE = new HashMap<>();

    public static void invokeSetupRotations(net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> renderer,
                                             net.minecraft.client.renderer.entity.state.LivingEntityRenderState state,
                                             PoseStack poseStack, float bodyRot, float entityScale) {
        java.lang.reflect.Method m = findMethodCached(renderer.getClass(), SETUP_ROTATIONS_CACHE, "setupRotations",
                net.minecraft.client.renderer.entity.state.LivingEntityRenderState.class, PoseStack.class, float.class, float.class);
        if (m != null) {
            try {
                m.invoke(renderer, state, poseStack, bodyRot, entityScale);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void invokeScale(net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> renderer,
                                    net.minecraft.client.renderer.entity.state.LivingEntityRenderState state,
                                    PoseStack poseStack) {
        java.lang.reflect.Method m = findMethodCached(renderer.getClass(), SCALE_CACHE, "scale",
                net.minecraft.client.renderer.entity.state.LivingEntityRenderState.class, PoseStack.class);
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

    public static class StatueRenderState extends BlockEntityRenderState {
        public EntityRenderState entityRenderState;
        public net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> livingRenderer;
        public float yaw;
        public float scale = 1.0f;
        public int materialColor = 0xFFFFFF;
    }
}
