package com.xiong.petrification.util;

import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.block.StatueBlockEntity;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.component.TargetType;
import com.xiong.petrification.data.MaterializedBlockSavedData;
import com.xiong.petrification.data.PlayerMaterializationSavedData;
import com.xiong.petrification.registry.ModBlocks;
import com.xiong.petrification.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public final class MaterializationHelper {
    private MaterializationHelper() {}

    public static boolean canMaterialize(Player player) {
        if (player.isCreative() && !ModConfigSpecHolder.allowCreativeMaterialization) {
            return false;
        }
        // Permission check simplified for MC 26.2 (hasPermissions removed)
        // OP materialization check uses permission level from player's server
        if (!ModConfigSpecHolder.allowOpMaterialization) {
            if (player instanceof ServerPlayer serverPlayer) {
                var permSet = serverPlayer.level().getServer().getProfilePermissions(
                        new net.minecraft.server.players.NameAndId(serverPlayer.getGameProfile()));
                int permLevel = permSet.level().id();
                if (permLevel >= 2) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void materializeEntity(ServerLevel level, LivingEntity entity, MaterialType materialType) {
        if (entity instanceof Player player && !canMaterialize(player)) {
            return;
        }

        // Skip a statue block if this block already holds one (e.g. a boat passenger statue-fied
        // in the same block as the boat); the new statue is placed on top instead.
        BlockPos pos = findFreeStatuePos(level, entity.blockPosition());
        StatueComponent component = captureEntityData(entity, materialType);

        if (entity instanceof ServerPlayer serverPlayer) {
            PlayerMaterializationSavedData.get(level).storePlayerBackup(serverPlayer, pos, component);
            serverPlayer.connection.disconnect(Component.translatable("disconnect.petrification.materialized"));
        }

        BlockState statueState = ModBlocks.STATUE_BLOCK.get().defaultBlockState();
        level.setBlock(pos, statueState, 3);

        if (level.getBlockEntity(pos) instanceof StatueBlockEntity blockEntity) {
            blockEntity.setStatueData(component);
        }

        if (!(entity instanceof ServerPlayer)) {
            if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
                // Vanilla kill() notifies the dragon fight: opens the exit portal, spawns a
                // gateway and hides the boss bar. A plain discard() skips all of that.
                entity.kill(level);
            } else {
                entity.discard();
            }
        }

        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        spawnParticles(level, Vec3.atCenterOf(pos), materialType);
    }

    public static void materializeArea(ServerLevel level, Vec3 center, double radius, MaterialType materialType) {
        AABB area = new AABB(center, center).inflate(radius);
        var entities = level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive);

        entities.sort((a, b) -> {
            int priorityA = a instanceof Player ? 0 : 1;
            int priorityB = b instanceof Player ? 0 : 1;
            return Integer.compare(priorityA, priorityB);
        });

        for (LivingEntity entity : entities) {
            materializeEntity(level, entity, materialType);
        }

        // End crystals are not LivingEntities, so collect them separately.
        for (net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal :
                level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class, area)) {
            materializeNonLiving(level, crystal, materialType);
        }

        // Boats (and chest boats) are not LivingEntities either; collect them after their
        // passengers so the captured boat NBT carries no passenger entries.
        for (AbstractBoat boat : level.getEntitiesOfClass(AbstractBoat.class, area)) {
            materializeNonLiving(level, boat, materialType);
        }
    }

    /**
     * Materializes a non-living entity (end crystal / boat) that carries no equipment / pose data.
     * If the entity's block is already occupied by a statue (e.g. a passenger statue-fied in the
     * same block), the boat statue is placed on top instead of overwriting it.
     */
    public static void materializeNonLiving(ServerLevel level, Entity entity, MaterialType materialType) {
        BlockPos pos = findFreeStatuePos(level, entity.blockPosition());
        StatueComponent component = captureNonLivingData(entity, materialType);

        BlockState statueState = ModBlocks.STATUE_BLOCK.get().defaultBlockState();
        level.setBlock(pos, statueState, 3);

        if (level.getBlockEntity(pos) instanceof StatueBlockEntity blockEntity) {
            blockEntity.setStatueData(component);
        }

        entity.discard();

        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
        spawnParticles(level, Vec3.atCenterOf(pos), materialType);
    }

    /** Walks upward from {@code pos} while a statue block already occupies it (max 4 tries). */
    private static BlockPos findFreeStatuePos(ServerLevel level, BlockPos pos) {
        BlockPos candidate = pos;
        for (int i = 0; i < 4 && level.getBlockState(candidate).is(ModBlocks.STATUE_BLOCK.get()); i++) {
            candidate = candidate.above();
        }
        return candidate;
    }

    /**
     * Captures the entity's full persistent NBT (tamed/owner, saddle, NoAI, custom flags, ...)
     * so the petrify -> restore cycle keeps every vanilla tag. Passengers / leash / UUID are
     * stripped: passengers are statue-fied on their own, and the restored entity gets a fresh UUID.
     */
    private static Optional<CompoundTag> captureEntityNbt(Entity entity) {
        if (entity instanceof Player) {
            // Players are backed up through PlayerMaterializationSavedData instead.
            return Optional.empty();
        }
        try {
            TagValueOutput output = TagValueOutput.createWithContext(
                    ProblemReporter.DISCARDING, entity.registryAccess());
            entity.saveWithoutId(output);
            CompoundTag tag = output.buildResult();
            tag.remove("Passengers");
            tag.remove("leash");
            tag.remove("UUID");
            // Illager arm poses (vindicator axe raise / evoker spell casting / raid celebrating) are
            // runtime-only state that vanilla never writes into entity NBT, so they are parked in
            // custom keys here; vanilla load ignores unknown keys and the render dummy reads them back.
            if (entity instanceof net.minecraft.world.entity.monster.illager.AbstractIllager illager) {
                tag.putByte("PetrificationAggressive", (byte) (illager.isAggressive() ? 1 : 0));
                tag.putByte("PetrificationCelebrating", (byte) (illager.isCelebrating() ? 1 : 0));
                if (entity instanceof net.minecraft.world.entity.monster.illager.SpellcasterIllager spellcaster) {
                    tag.putByte("PetrificationCasting", (byte) (spellcaster.isCastingSpell() ? 1 : 0));
                }
            }
            return Optional.of(tag);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Loads the captured full entity NBT back onto a freshly created entity. Shared by the
     * server-side restoration handler; failures fall back to the field-by-field restoration.
     */
    public static void loadEntityNbt(Entity entity, CompoundTag tag) {
        try {
            net.minecraft.world.level.storage.ValueInput input = TagValueInput.create(
                    ProblemReporter.DISCARDING, entity.registryAccess(), tag);
            entity.load(input);
        } catch (Exception e) {
            // Silently ignore: the field-by-field fallbacks already restored most state
        }
    }

    public static StatueComponent captureNonLivingData(Entity entity, MaterialType materialType) {
        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Optional<String> customName = entity.hasCustomName()
                ? Optional.of(entity.getCustomName().getString())
                : Optional.empty();
        String displayName = buildDisplayName(entity, materialType);

        return new StatueComponent(
                TargetType.BIOLOGICAL,
                entityId,
                (Optional<UUID>) Optional.of(entity.getUUID()),
                materialType,
                0.0f,
                false,
                entity.level().getGameTime(),
                entity.getYRot(),
                entity.getXRot(),
                entity.getYRot(),
                entity.getYRot(),
                false, false, false, false,
                0.0f, 0.0f,
                false,
                Optional.<String>empty(),
                Optional.<Integer>empty(),
                Optional.<CompoundTag>empty(),
                Optional.<Float>empty(),
                Optional.<Integer>empty(),
                customName,
                Optional.<Boolean>empty(),
                Optional.<UUID>empty(),
                Optional.<String>empty(),
                Optional.<CompoundTag>empty(),
                Optional.<Integer>empty(),
                Optional.<Float>empty(),
                Optional.<Integer>empty(),
                Optional.<Float>empty(),
                Optional.<Integer>empty(),
                Optional.<Integer>empty(),
                Optional.<Float>empty(),
                (Optional<String>) Optional.of(displayName),
                entity.hasCustomName(),
                Optional.<CompoundTag>empty(),
                captureEntityNbt(entity)
        );
    }

    public static StatueComponent captureEntityData(LivingEntity entity, MaterialType materialType) {
        TargetType targetType = entity instanceof Player ? TargetType.PLAYER : TargetType.BIOLOGICAL;
        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

        CompoundTag equipment = new CompoundTag();
        equipment.put("mainHand", saveItemStack(entity.getMainHandItem()));
        equipment.put("offHand", saveItemStack(entity.getOffhandItem()));
        equipment.put("helmet", saveItemStack(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)));
        equipment.put("chestplate", saveItemStack(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)));
        equipment.put("leggings", saveItemStack(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS)));
        equipment.put("boots", saveItemStack(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)));
        // BODY slot holds the sulfur cube's contained block (and horse armour / happy-ghast harness);
        // SADDLE slot holds the strider's saddle.
        equipment.put("body", saveItemStack(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY)));
        equipment.put("saddle", saveItemStack(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.SADDLE)));

        Optional<String> customName = entity.hasCustomName()
                ? Optional.of(entity.getCustomName().getString())
                : Optional.empty();

        String displayName = buildDisplayName(entity, materialType);

        Optional<String> playerName = Optional.empty();
        Optional<CompoundTag> gameProfile = Optional.empty();
        Optional<Integer> foodLevel = Optional.empty();
        Optional<Float> saturation = Optional.empty();
        Optional<Integer> experienceLevel = Optional.empty();
        Optional<Float> experienceProgress = Optional.empty();
        Optional<Integer> totalExperience = Optional.empty();

        if (entity instanceof Player player) {
            playerName = Optional.of(player.getGameProfile().name());
            CompoundTag profileTag = new CompoundTag();
            profileTag.putLong("IdMost", player.getUUID().getMostSignificantBits());
            profileTag.putLong("IdLeast", player.getUUID().getLeastSignificantBits());
            profileTag.putString("Name", player.getGameProfile().name());
            gameProfile = Optional.of(profileTag);
            foodLevel = Optional.of(player.getFoodData().getFoodLevel());
            saturation = Optional.of(player.getFoodData().getSaturationLevel());
            experienceLevel = Optional.of(player.experienceLevel);
            experienceProgress = Optional.of(player.experienceProgress);
            totalExperience = Optional.of(player.totalExperience);
        }

        Optional<Boolean> isTamed = Optional.empty();
        Optional<UUID> ownerUuid = Optional.empty();
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
            isTamed = Optional.of(true);
            if (tamable.getOwnerReference() != null) {
                ownerUuid = Optional.of(tamable.getOwnerReference().getUUID());
            }
        }

        // Capture size/profession-bearing variants so the statue keeps the correct scale / look.
        Optional<String> variant = Optional.empty();
        Optional<CompoundTag> villagerOffers = Optional.empty();
        if (entity instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob cubeMob) {
            variant = Optional.of("size:" + cubeMob.getSize());
        } else if (entity instanceof net.minecraft.world.entity.npc.villager.Villager villager) {
            net.minecraft.world.entity.npc.villager.VillagerData data = villager.getVillagerData();
            String typeId = BuiltInRegistries.VILLAGER_TYPE.getKey(data.type().value()).toString();
            String profId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.profession().value()).toString();
            variant = Optional.of("villager|" + typeId + "|" + profId + "|" + data.level() + "|" + villager.getVillagerXp());
            // Snapshot the actual trade list: on restore the profession change clears offers and
            // vanilla regenerates a random subset, which players experience as "trades reset".
            if (!villager.level().isClientSide()) {
                net.minecraft.world.item.trading.MerchantOffers offers = villager.getOffers();
                if (offers != null && !offers.isEmpty()) {
                    CompoundTag offersTag = new CompoundTag();
                    net.minecraft.world.item.trading.MerchantOffers.CODEC
                            .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, offers)
                            .result()
                            .ifPresent(nbt -> {
                                if (nbt instanceof CompoundTag compoundTag) {
                                    offersTag.merge(compoundTag);
                                }
                            });
                    if (!offersTag.isEmpty()) {
                        villagerOffers = Optional.of(offersTag);
                    }
                }
            }
        } else if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombieVillager zombieVillager) {
            net.minecraft.world.entity.npc.villager.VillagerData data = zombieVillager.getVillagerData();
            String typeId = BuiltInRegistries.VILLAGER_TYPE.getKey(data.type().value()).toString();
            String profId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.profession().value()).toString();
            variant = Optional.of("zombievillager|" + typeId + "|" + profId + "|" + data.level() + "|" + zombieVillager.getVillagerXp());
            // Zombie villagers carry the pre-zombification trades and xp (transferred back to the
            // villager on cure), so snapshot them like the villager branch to keep the career intact.
            if (!zombieVillager.level().isClientSide()) {
                Object offersObj = readPrivateField(zombieVillager, "tradeOffers");
                if (offersObj instanceof net.minecraft.world.item.trading.MerchantOffers offers && !offers.isEmpty()) {
                    CompoundTag offersTag = new CompoundTag();
                    net.minecraft.world.item.trading.MerchantOffers.CODEC
                            .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, offers)
                            .result()
                            .ifPresent(nbt -> {
                                if (nbt instanceof CompoundTag compoundTag) {
                                    offersTag.merge(compoundTag);
                                }
                            });
                    if (!offersTag.isEmpty()) {
                        villagerOffers = Optional.of(offersTag);
                    }
                }
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.feline.Cat cat) {
            variant = cat.getVariant().unwrapKey().map(key -> "cat|" + key.identifier().toString() + "|" + cat.getCollarColor().getId());
        } else if (entity instanceof net.minecraft.world.entity.animal.cow.MushroomCow mushroomCow) {
            // Must precede the Cow branch: MushroomCow extends Cow but its red/brown look is
            // driven by its own MushroomCow.Variant, not the cow variant registry.
            variant = Optional.of("mooshroom|" + mushroomCow.getVariant().getSerializedName());
        } else if (entity instanceof net.minecraft.world.entity.animal.equine.Horse horse) {
            Object typeVariant = invokePrivate(horse, "getTypeVariant", new Class<?>[0]);
            if (typeVariant instanceof Integer packed) {
                variant = Optional.of("horse|" + packed);
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.equine.Llama llama) {
            variant = Optional.of("llama|" + llama.getVariant().getId() + "|" + (llama.hasChest() ? 1 : 0));
        } else if (entity instanceof net.minecraft.world.entity.animal.equine.AbstractChestedHorse chested && chested.hasChest()) {
            variant = Optional.of("chested|1");
        } else if (entity instanceof net.minecraft.world.entity.animal.axolotl.Axolotl axolotl) {
            variant = Optional.of("axolotl|" + axolotl.getVariant().getId());
        } else if (entity instanceof net.minecraft.world.entity.animal.parrot.Parrot parrot) {
            variant = Optional.of("parrot|" + parrot.getVariant().getId() + (parrot.isOrderedToSit() ? "|sit" : ""));
        } else if (entity instanceof net.minecraft.world.entity.animal.fish.TropicalFish tropicalFish) {
            Object packedVariant = invokePrivate(tropicalFish, "getPackedVariant", new Class<?>[0]);
            if (packedVariant instanceof Integer packed) {
                variant = Optional.of("tropicalfish|" + packed);
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.fish.Salmon salmon) {
            variant = Optional.of("salmon|" + salmon.getVariant().getSerializedName());
        } else if (entity instanceof net.minecraft.world.entity.animal.fish.Pufferfish pufferfish) {
            variant = Optional.of("pufferfish|" + pufferfish.getPuffState());
        } else if (entity instanceof net.minecraft.world.entity.animal.cow.Cow cow) {
            variant = cow.getVariant().unwrapKey().map(key -> "cow|" + key.identifier().toString());
        } else if (entity instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken) {
            variant = chicken.getVariant().unwrapKey().map(key -> "chicken|" + key.identifier().toString());
        } else if (entity instanceof net.minecraft.world.entity.animal.wolf.Wolf wolf) {
            Object wolfVariant = invokePrivate(wolf, "getVariant", new Class<?>[0]);
            if (wolfVariant instanceof Holder<?> holder) {
                variant = holder.unwrapKey().map(key -> "wolf|" + key.identifier().toString() + "|" + wolf.getCollarColor().getId());
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.fox.Fox fox) {
            variant = Optional.of("fox|" + fox.getVariant().getId());
        } else if (entity instanceof net.minecraft.world.entity.animal.rabbit.Rabbit rabbit) {
            variant = Optional.of("rabbit|" + rabbit.getVariant().getSerializedName());
        } else if (entity instanceof net.minecraft.world.entity.animal.frog.Frog frog) {
            variant = frog.getVariant().unwrapKey().map(key -> "frog|" + key.identifier().toString());
        } else if (entity instanceof net.minecraft.world.entity.animal.pig.Pig pig) {
            variant = pig.getVariant().unwrapKey().map(key -> "pig|" + key.identifier().toString());
        } else if (entity instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep) {
            variant = Optional.of("sheep|" + sheep.getColor().getId() + "|" + (sheep.isSheared() ? 1 : 0));
        } else if (entity instanceof net.minecraft.world.entity.monster.Shulker shulker) {
            variant = Optional.of("shulker|" + (shulker.getColor() == null ? 16 : shulker.getColor().getId()));
        } else if (entity instanceof net.minecraft.world.entity.animal.nautilus.ZombieNautilus zombieNautilus) {
            variant = zombieNautilus.getVariant().unwrapKey().map(key -> "znautilus|" + key.identifier().toString());
        } else if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand armorStand) {
            // Poses + flags keep posed armour stands recognizable instead of snapping to the default stance.
            StringBuilder sb = new StringBuilder("armorstand|");
            sb.append(armorStand.isSmall() ? 1 : 0).append(armorStand.showArms() ? 1 : 0).append(armorStand.showBasePlate() ? 0 : 1);
            for (net.minecraft.core.Rotations rot : java.util.List.of(armorStand.getHeadPose(), armorStand.getBodyPose(),
                    armorStand.getLeftArmPose(), armorStand.getRightArmPose(), armorStand.getLeftLegPose(), armorStand.getRightLegPose())) {
                sb.append('|').append(rot.x()).append(',').append(rot.y()).append(',').append(rot.z());
            }
            variant = Optional.of(sb.toString());
        } else if (entity instanceof net.minecraft.world.entity.monster.Creeper creeper) {
            variant = Optional.of("creeper|" + (creeper.isPowered() ? 1 : 0));
        } else if (entity instanceof net.minecraft.world.entity.animal.golem.SnowGolem snowGolem) {
            variant = Optional.of("snowgolem|" + (snowGolem.hasPumpkin() ? 1 : 0));
        } else if (entity instanceof net.minecraft.world.entity.monster.EnderMan enderMan) {
            variant = Optional.of(enderMan.getCarriedBlock() == null
                    ? "enderman|none"
                    : "enderman|" + net.minecraft.nbt.NbtUtils.writeBlockState(enderMan.getCarriedBlock()));
        } else if (entity instanceof net.minecraft.world.entity.animal.bee.Bee bee) {
            variant = Optional.of("bee|" + (bee.isAngry() ? 1 : 0) + "|" + (bee.hasNectar() ? 1 : 0));
        } else if (entity instanceof net.minecraft.world.entity.animal.golem.CopperGolem copperGolem) {
            variant = Optional.of("coppergolem|" + copperGolem.getWeatherState().getSerializedName());
        }

        // Orientation: clamp head/body yaw deviations to ±90° from the body yaw. A fleeing mob
        // looks back over its shoulder while being petrified (headYaw ≈ yaw ± 180°), and a
        // player's yBodyRot lags yRot while turning; faithfully freezing such an instant renders
        // the statue with its head/body spun a half-turn relative to the body. Clamping keeps
        // normal looking-around within ±90° intact.
        float yaw = entity.getYRot();
        float headYaw = entity.getYHeadRot();
        float bodyYaw = entity instanceof Player player ? player.yBodyRot : yaw;
        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(headYaw - yaw)) > 90.0f) {
            headYaw = yaw;
        }
        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(bodyYaw - yaw)) > 90.0f) {
            bodyYaw = yaw;
        }

        return new StatueComponent(
                targetType,
                entityId,
                (Optional<UUID>) Optional.of(entity.getUUID()),
                materialType,
                0.0f,
                false,
                entity.level().getGameTime(),
                yaw,
                entity.getXRot(),
                headYaw,
                bodyYaw,
                entity.isCrouching(),
                entity.isSprinting(),
                entity.isSwimming(),
                entity instanceof Player player && player.getAbilities().flying,
                entity.walkAnimation.position(),
                entity.walkAnimation.speed(),
                entity.isBaby(),
                variant,
                Optional.<Integer>empty(),
                (Optional<CompoundTag>) Optional.of(equipment),
                (Optional<Float>) Optional.of((float) entity.getHealth()),
                Optional.<Integer>empty(),
                (Optional<String>) customName,
                (Optional<Boolean>) isTamed,
                (Optional<UUID>) ownerUuid,
                (Optional<String>) playerName,
                (Optional<CompoundTag>) gameProfile,
                (Optional<Integer>) foodLevel,
                (Optional<Float>) saturation,
                (Optional<Integer>) experienceLevel,
                (Optional<Float>) experienceProgress,
                (Optional<Integer>) totalExperience,
                (Optional<Integer>) Optional.of(entity.getAirSupply()),
                (Optional<Float>) Optional.of((float) entity.fallDistance),
                (Optional<String>) Optional.of(displayName),
                entity.hasCustomName(),
                villagerOffers,
                captureEntityNbt(entity)
        );
    }

    /** Builds statue data for a temporary entity of the given type (used by commands). */
    public static StatueComponent createStatueComponent(ServerLevel level, EntityType<?> entityType, MaterialType materialType) {
        Entity entity = entityType.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            return null;
        }
        return entity instanceof LivingEntity living
                ? captureEntityData(living, materialType)
                : captureNonLivingData(entity, materialType);
    }

    /** Creates a statue item carrying the data of a freshly generated entity of the given type. */
    public static ItemStack createStatueItem(ServerLevel level, EntityType<?> entityType, MaterialType materialType) {
        StatueComponent component = createStatueComponent(level, entityType, materialType);
        if (component == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(ModBlocks.STATUE_BLOCK.get().asItem());
        stack.set(ModDataComponents.STATUE_DATA.get(), component);
        return stack;
    }

    private static CompoundTag saveItemStack(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        if (!stack.isEmpty()) {
            ItemStack.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, stack)
                    .result()
                    .ifPresent(nbt -> {
                        if (nbt instanceof CompoundTag compoundTag) {
                            tag.merge(compoundTag);
                        }
                    });
        }
        return tag;
    }

    private static String buildDisplayName(Entity entity, MaterialType materialType) {
        if (entity.hasCustomName()) {
            return materialType.getDisplayPrefix() + " " + entity.getCustomName().getString();
        }
        String entityName = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        entityName = entityName.substring(0, 1).toUpperCase() + entityName.substring(1).replace('_', ' ');
        return materialType.getDisplayPrefix() + " " + entityName;
    }

    /**
     * Re-applies captured per-mob variant data (slime size / villager type+profession+level) onto a
     * living entity. Shared by the client statue renderer and the server-side restoration handler so
     * the profession/scale survives the petrify -> restore cycle. Uses registry-bound holders, since
     * the villager profession layer resolves textures via holder.unwrapKey() (empty for direct holders).
     */
    public static void applyVariant(LivingEntity entity, StatueComponent component) {
        if (component.variant().isEmpty()) {
            return;
        }
        String v = component.variant().get();
        if (entity instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob cubeMob && v.startsWith("size:")) {
            try {
                cubeMob.setSize(Integer.parseInt(v.substring(5)), false);
            } catch (NumberFormatException ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.npc.villager.Villager villager && v.startsWith("villager|")) {
            String[] parts = v.split("\\|");
            if (parts.length >= 4) {
                try {
                    net.minecraft.core.Holder<net.minecraft.world.entity.npc.villager.VillagerType> type =
                            BuiltInRegistries.VILLAGER_TYPE.get(net.minecraft.resources.Identifier.parse(parts[1])).orElse(null);
                    net.minecraft.core.Holder<net.minecraft.world.entity.npc.villager.VillagerProfession> prof =
                            BuiltInRegistries.VILLAGER_PROFESSION.get(net.minecraft.resources.Identifier.parse(parts[2])).orElse(null);
                    int level = Integer.parseInt(parts[3]);
                    int xp = parts.length >= 5 ? Integer.parseInt(parts[4]) : 0;
                    if (type != null && prof != null) {
                        villager.setVillagerData(new net.minecraft.world.entity.npc.villager.VillagerData(type, prof, level));
                        boolean realProfession = !prof.is(net.minecraft.world.entity.npc.villager.VillagerProfession.NONE) && !prof.is(net.minecraft.world.entity.npc.villager.VillagerProfession.NITWIT);
                        villager.setVillagerXp(realProfession ? Math.max(xp, 1) : xp);
                    }
                    // setVillagerData above nulls the offers on a profession change; restore the
                    // snapshot afterwards so the original trade list (uses/stock included) survives.
                    if (!villager.level().isClientSide()) {
                        component.villagerOffers().ifPresent(offersTag ->
                                net.minecraft.world.item.trading.MerchantOffers.CODEC
                                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, offersTag)
                                        .result()
                                        .ifPresent(villager::setOffers));
                    }
                } catch (Exception ignored) {
                }
            }
        } else if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombieVillager zombieVillager && v.startsWith("zombievillager|")) {
            String[] parts = v.split("\\|");
            if (parts.length >= 4) {
                try {
                    net.minecraft.core.Holder<net.minecraft.world.entity.npc.villager.VillagerType> type =
                            BuiltInRegistries.VILLAGER_TYPE.get(net.minecraft.resources.Identifier.parse(parts[1])).orElse(null);
                    net.minecraft.core.Holder<net.minecraft.world.entity.npc.villager.VillagerProfession> prof =
                            BuiltInRegistries.VILLAGER_PROFESSION.get(net.minecraft.resources.Identifier.parse(parts[2])).orElse(null);
                    if (type != null && prof != null) {
                        zombieVillager.setVillagerData(new net.minecraft.world.entity.npc.villager.VillagerData(type, prof, Integer.parseInt(parts[3])));
                        // Floor xp at 1 for real professions: the value is transferred back on
                        // cure, and a cured level-1 villager with 0 xp loses its profession to
                        // ResetProfession without a job site.
                        int xp = parts.length >= 5 ? Integer.parseInt(parts[4]) : 0;
                        boolean realProfession = !prof.is(net.minecraft.world.entity.npc.villager.VillagerProfession.NONE) && !prof.is(net.minecraft.world.entity.npc.villager.VillagerProfession.NITWIT);
                        zombieVillager.setVillagerXp(realProfession ? Math.max(xp, 1) : xp);
                    }
                    // setVillagerData above nulls tradeOffers on a profession change; restore the
                    // snapshot afterwards so the pre-zombification trade list survives.
                    if (!zombieVillager.level().isClientSide()) {
                        component.villagerOffers().ifPresent(offersTag ->
                                net.minecraft.world.item.trading.MerchantOffers.CODEC
                                        .parse(net.minecraft.nbt.NbtOps.INSTANCE, offersTag)
                                        .result()
                                        .ifPresent(zombieVillager::setTradeOffers));
                    }
                } catch (Exception ignored) {
                }
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.feline.Cat cat && v.startsWith("cat|")) {
            try {
                String[] parts = v.split("\\|");
                entity.registryAccess()
                        .lookupOrThrow(net.minecraft.core.registries.Registries.CAT_VARIANT)
                        .get(Identifier.parse(parts[1]))
                        .ifPresent(holder -> invokePrivate(cat, "setVariant", new Class<?>[]{Holder.class}, holder));
                if (parts.length >= 3) {
                    invokePrivate(cat, "setCollarColor", new Class<?>[]{net.minecraft.world.item.DyeColor.class},
                            net.minecraft.world.item.DyeColor.byId(Integer.parseInt(parts[2])));
                }
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.cow.MushroomCow mushroomCow && v.startsWith("mooshroom|")) {
            for (net.minecraft.world.entity.animal.cow.MushroomCow.Variant mushroomVariant : net.minecraft.world.entity.animal.cow.MushroomCow.Variant.values()) {
                if (mushroomVariant.getSerializedName().equals(v.substring(10))) {
                    invokePrivate(mushroomCow, "setVariant", new Class<?>[]{net.minecraft.world.entity.animal.cow.MushroomCow.Variant.class}, mushroomVariant);
                    break;
                }
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.equine.Horse horse && v.startsWith("horse|")) {
            try {
                invokePrivate(horse, "setTypeVariant", new Class<?>[]{int.class}, Integer.parseInt(v.substring(6)));
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.equine.Llama llama && v.startsWith("llama|")) {
            String[] parts = v.split("\\|");
            try {
                invokePrivate(llama, "setVariant", new Class<?>[]{net.minecraft.world.entity.animal.equine.Llama.Variant.class},
                        net.minecraft.world.entity.animal.equine.Llama.Variant.byId(Integer.parseInt(parts[1])));
                if (parts.length >= 3 && parts[2].equals("1")) {
                    llama.setChest(true);
                }
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.equine.AbstractChestedHorse chested && v.equals("chested|1")) {
            chested.setChest(true);
        } else if (entity instanceof net.minecraft.world.entity.animal.axolotl.Axolotl axolotl && v.startsWith("axolotl|")) {
            try {
                invokePrivate(axolotl, "setVariant", new Class<?>[]{net.minecraft.world.entity.animal.axolotl.Axolotl.Variant.class},
                        net.minecraft.world.entity.animal.axolotl.Axolotl.Variant.byId(Integer.parseInt(v.substring(8))));
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.parrot.Parrot parrot && v.startsWith("parrot|")) {
            String[] parts = v.split("\\|");
            try {
                invokePrivate(parrot, "setVariant", new Class<?>[]{net.minecraft.world.entity.animal.parrot.Parrot.Variant.class},
                        net.minecraft.world.entity.animal.parrot.Parrot.Variant.byId(Integer.parseInt(parts[1])));
                if (parts.length >= 3 && parts[2].equals("sit")) {
                    parrot.setOrderedToSit(true);
                }
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.fish.TropicalFish tropicalFish && v.startsWith("tropicalfish|")) {
            try {
                invokePrivate(tropicalFish, "setPackedVariant", new Class<?>[]{int.class}, Integer.parseInt(v.substring(13)));
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.fish.Salmon salmon && v.startsWith("salmon|")) {
            String name = v.substring(7);
            for (net.minecraft.world.entity.animal.fish.Salmon.Variant salmonVariant : net.minecraft.world.entity.animal.fish.Salmon.Variant.values()) {
                if (salmonVariant.getSerializedName().equals(name)) {
                    invokePrivate(salmon, "setVariant", new Class<?>[]{net.minecraft.world.entity.animal.fish.Salmon.Variant.class}, salmonVariant);
                    break;
                }
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.fish.Pufferfish pufferfish && v.startsWith("pufferfish|")) {
            try {
                pufferfish.setPuffState(Integer.parseInt(v.substring(11)));
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.cow.Cow cow && v.startsWith("cow|")) {
            try {
                entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.COW_VARIANT)
                        .get(Identifier.parse(v.substring(4)))
                        .ifPresent(cow::setVariant);
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken && v.startsWith("chicken|")) {
            try {
                entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.CHICKEN_VARIANT)
                        .get(Identifier.parse(v.substring(8)))
                        .ifPresent(chicken::setVariant);
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.wolf.Wolf wolf && v.startsWith("wolf|")) {
            try {
                String[] parts = v.split("\\|");
                entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.WOLF_VARIANT)
                        .get(Identifier.parse(parts[1]))
                        .ifPresent(holder -> invokePrivate(wolf, "setVariant", new Class<?>[]{Holder.class}, holder));
                if (parts.length >= 3) {
                    invokePrivate(wolf, "setCollarColor", new Class<?>[]{net.minecraft.world.item.DyeColor.class},
                            net.minecraft.world.item.DyeColor.byId(Integer.parseInt(parts[2])));
                }
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.fox.Fox fox && v.startsWith("fox|")) {
            try {
                invokePrivate(fox, "setVariant", new Class<?>[]{net.minecraft.world.entity.animal.fox.Fox.Variant.class},
                        net.minecraft.world.entity.animal.fox.Fox.Variant.byId(Integer.parseInt(v.substring(4))));
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.rabbit.Rabbit rabbit && v.startsWith("rabbit|")) {
            try {
                invokePrivate(rabbit, "setVariant", new Class<?>[]{net.minecraft.world.entity.animal.rabbit.Rabbit.Variant.class},
                        net.minecraft.world.entity.animal.rabbit.Rabbit.Variant.byId(Integer.parseInt(v.substring(7))));
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.frog.Frog frog && v.startsWith("frog|")) {
            try {
                entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.FROG_VARIANT)
                        .get(Identifier.parse(v.substring(5)))
                        .ifPresent(holder -> invokePrivate(frog, "setVariant", new Class<?>[]{Holder.class}, holder));
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.pig.Pig pig && v.startsWith("pig|")) {
            try {
                entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PIG_VARIANT)
                        .get(Identifier.parse(v.substring(4)))
                        .ifPresent(holder -> invokePrivate(pig, "setVariant", new Class<?>[]{Holder.class}, holder));
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep && v.startsWith("sheep|")) {
            try {
                String[] parts = v.split("\\|");
                sheep.setColor(net.minecraft.world.item.DyeColor.byId(Integer.parseInt(parts[1])));
                if (parts.length >= 3 && parts[2].equals("1")) {
                    sheep.setSheared(true);
                }
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.monster.Shulker shulker && v.startsWith("shulker|")) {
            try {
                int colorId = Integer.parseInt(v.substring(8));
                net.minecraft.world.item.DyeColor color = colorId >= 16 ? null : net.minecraft.world.item.DyeColor.byId(colorId);
                invokePrivate(shulker, "setVariant", new Class<?>[]{java.util.Optional.class}, java.util.Optional.ofNullable(color));
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.nautilus.ZombieNautilus zombieNautilus && v.startsWith("znautilus|")) {
            try {
                entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ZOMBIE_NAUTILUS_VARIANT)
                        .get(Identifier.parse(v.substring(10)))
                        .ifPresent(zombieNautilus::setVariant);
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand armorStand && v.startsWith("armorstand|")) {
            try {
                String[] parts = v.split("\\|");
                if (parts.length >= 9) {
                    // The item-path dummy entity is cached per type and reused across statues,
                    // so every flag must be written both ways or state leaks between statues.
                    String flags = parts[1];
                    if (flags.length() >= 3) {
                        invokePrivate(armorStand, "setSmall", new Class<?>[]{boolean.class}, flags.charAt(0) == '1');
                        armorStand.setShowArms(flags.charAt(1) == '1');
                        armorStand.setNoBasePlate(flags.charAt(2) == '1');
                    }
                    armorStand.setHeadPose(parseRotations(parts[2]));
                    armorStand.setBodyPose(parseRotations(parts[3]));
                    armorStand.setLeftArmPose(parseRotations(parts[4]));
                    armorStand.setRightArmPose(parseRotations(parts[5]));
                    armorStand.setLeftLegPose(parseRotations(parts[6]));
                    armorStand.setRightLegPose(parseRotations(parts[7]));
                }
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.monster.Creeper creeper && v.startsWith("creeper|")) {
            // DATA_IS_POWERED has no public setter; write through the synched-data accessor via reflection.
            Object accessor = readPrivateField(creeper, "DATA_IS_POWERED");
            if (accessor instanceof net.minecraft.network.syncher.EntityDataAccessor<?> dataAccessor) {
                @SuppressWarnings("unchecked")
                net.minecraft.network.syncher.EntityDataAccessor<Boolean> poweredAccessor =
                        (net.minecraft.network.syncher.EntityDataAccessor<Boolean>) dataAccessor;
                creeper.getEntityData().set(poweredAccessor, v.endsWith("|1"));
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.golem.SnowGolem snowGolem && v.startsWith("snowgolem|")) {
            snowGolem.setPumpkin(v.endsWith("|1"));
        } else if (entity instanceof net.minecraft.world.entity.monster.EnderMan enderMan && v.startsWith("enderman|")) {
            if (v.equals("enderman|none")) {
                enderMan.setCarriedBlock(null);
            } else {
                try {
                    CompoundTag blockTag = net.minecraft.nbt.TagParser.parseCompoundFully(v.substring(9));
                    net.minecraft.world.level.block.state.BlockState carried = net.minecraft.nbt.NbtUtils.readBlockState(
                            entity.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), blockTag);
                    if (!carried.isAir()) {
                        enderMan.setCarriedBlock(carried);
                    }
                } catch (Exception ignored) {
                }
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.bee.Bee bee && v.startsWith("bee|")) {
            try {
                String[] parts = v.split("\\|");
                if (parts.length >= 2) {
                    // Anger is derived from the anger-end-time entity data: push it far into the
                    // future for angry bees, back to the default for calm ones (dummy reuse).
                    bee.setPersistentAngerEndTime(parts[1].equals("1")
                            ? bee.level().getGameTime() + 20L * 60L * 60L * 24L
                            : -1L);
                }
                if (parts.length >= 3) {
                    invokePrivate(bee, "setHasNectar", new Class<?>[]{boolean.class}, parts[2].equals("1"));
                }
            } catch (Exception ignored) {
            }
        } else if (entity instanceof net.minecraft.world.entity.animal.golem.CopperGolem copperGolem && v.startsWith("coppergolem|")) {
            // DATA_WEATHER_STATE has no public setter; write through the synched-data accessor via reflection.
            Object accessor = readPrivateField(copperGolem, "DATA_WEATHER_STATE");
            if (accessor instanceof net.minecraft.network.syncher.EntityDataAccessor<?> dataAccessor) {
                for (net.minecraft.world.level.block.WeatheringCopper.WeatherState weatherState
                        : net.minecraft.world.level.block.WeatheringCopper.WeatherState.values()) {
                    if (weatherState.getSerializedName().equals(v.substring(12))) {
                        @SuppressWarnings("unchecked")
                        net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.level.block.WeatheringCopper.WeatherState> weatherAccessor =
                                (net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.level.block.WeatheringCopper.WeatherState>) dataAccessor;
                        copperGolem.getEntityData().set(weatherAccessor, weatherState);
                        break;
                    }
                }
            }
        }
    }

    private static net.minecraft.core.Rotations parseRotations(String packed) {
        String[] xyz = packed.split(",");
        return new net.minecraft.core.Rotations(Float.parseFloat(xyz[0]), Float.parseFloat(xyz[1]), Float.parseFloat(xyz[2]));
    }

    private static final java.util.Map<String, java.lang.reflect.Method> VARIANT_METHOD_CACHE = new java.util.HashMap<>();
    private static final java.util.Map<String, java.lang.reflect.Field> VARIANT_FIELD_CACHE = new java.util.HashMap<>();

    private static Object readPrivateField(Object target, String fieldName) {
        try {
            String key = target.getClass().getName() + "#" + fieldName;
            java.lang.reflect.Field field = VARIANT_FIELD_CACHE.get(key);
            if (field == null && !VARIANT_FIELD_CACHE.containsKey(key)) {
                for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    try {
                        field = c.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        break;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                VARIANT_FIELD_CACHE.put(key, field);
            }
            return field == null ? null : field.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    // Variant setters are private in vanilla (only reachable through data components), so the
    // dummy entity / restored mob needs reflection. Results are cached per class+method.
    private static Object invokePrivate(Object target, String methodName, Class<?>[] params, Object... args) {
        try {
            String key = target.getClass().getName() + "#" + methodName;
            java.lang.reflect.Method method = VARIANT_METHOD_CACHE.get(key);
            if (method == null && !VARIANT_METHOD_CACHE.containsKey(key)) {
                for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    try {
                        method = c.getDeclaredMethod(methodName, params);
                        method.setAccessible(true);
                        break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                VARIANT_METHOD_CACHE.put(key, method);
            }
            return method == null ? null : method.invoke(target, args);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Applies the captured equipment onto a (cached, reused) render dummy with a two-way
     * overwrite: every slot is cleared first and only the recorded slots are filled again,
     * so no state leaks between statues sharing one cached entity.
     */
    public static void applyEquipment(LivingEntity entity, Optional<CompoundTag> equipment) {
        net.minecraft.world.entity.EquipmentSlot[] slots = {
                net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET,
                net.minecraft.world.entity.EquipmentSlot.BODY,
                net.minecraft.world.entity.EquipmentSlot.SADDLE
        };
        for (net.minecraft.world.entity.EquipmentSlot slot : slots) {
            entity.setItemSlot(slot, ItemStack.EMPTY);
        }
        equipment.ifPresent(tag -> restoreEquipment(entity, tag));
    }

    public static void restoreEquipment(LivingEntity entity, CompoundTag equipment) {
        if (equipment == null) {
            return;
        }
        restoreSlot(entity, equipment, "mainHand", net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        restoreSlot(entity, equipment, "offHand", net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        restoreSlot(entity, equipment, "helmet", net.minecraft.world.entity.EquipmentSlot.HEAD);
        restoreSlot(entity, equipment, "chestplate", net.minecraft.world.entity.EquipmentSlot.CHEST);
        restoreSlot(entity, equipment, "leggings", net.minecraft.world.entity.EquipmentSlot.LEGS);
        restoreSlot(entity, equipment, "boots", net.minecraft.world.entity.EquipmentSlot.FEET);
        restoreSlot(entity, equipment, "body", net.minecraft.world.entity.EquipmentSlot.BODY);
        restoreSlot(entity, equipment, "saddle", net.minecraft.world.entity.EquipmentSlot.SADDLE);
    }

    private static void restoreSlot(LivingEntity entity, CompoundTag equipment, String key,
                                    net.minecraft.world.entity.EquipmentSlot slot) {
        if (equipment.contains(key)) {
            CompoundTag itemTag = equipment.getCompoundOrEmpty(key);
            // Item components reference registries, so the parse needs the registry context;
            // a bare NbtOps silently fails and drops the item (e.g. the pig / strider saddle).
            ItemStack stack = ItemStack.CODEC.parse(
                            entity.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
                            itemTag)
                    .result()
                    .orElse(ItemStack.EMPTY);
            entity.setItemSlot(slot, stack);
        }
    }

    public static void materializeBlocks(ServerLevel level, Vec3 center, double radius, MaterialType materialType) {
        MaterializedBlockSavedData savedData = MaterializedBlockSavedData.get(level);
        BlockPos centerPos = BlockPos.containing(center);
        int r = (int) Math.ceil(radius);

        for (BlockPos pos : BlockPos.betweenClosed(centerPos.offset(-r, -r, -r), centerPos.offset(r, r, r))) {
            if (center.distanceTo(Vec3.atCenterOf(pos)) > radius) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(ModBlocks.STATUE_BLOCK.get())) {
                continue;
            }
            if (savedData.getBlockData(pos).isPresent()) {
                continue;
            }

            Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            savedData.setBlockData(pos, new MaterializedBlockSavedData.BlockMaterializationData(
                    blockId, materialType, 0.0f, false, level.getGameTime()));
        }
    }

    private static void spawnParticles(ServerLevel level, Vec3 pos, MaterialType materialType) {
        int color = materialType.getColor();
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(color, 1.0f),
                pos.x, pos.y + 0.5, pos.z, 20, 0.3, 0.5, 0.3, 0.05);
    }
}
