package com.xiong.petrification.handler;

import com.xiong.petrification.block.StatueBlockEntity;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.component.TargetType;
import com.xiong.petrification.data.PlayerMaterializationSavedData;
import com.xiong.petrification.registry.ModBlocks;
import com.xiong.petrification.registry.ModDataComponents;
import com.xiong.petrification.registry.ModItems;
import com.xiong.petrification.util.MaterializationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public final class RestorationHandler {
    private RestorationHandler() {}

    public static boolean restoreBlock(Player player, Level level, BlockPos pos) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        if (!(level.getBlockEntity(pos) instanceof StatueBlockEntity blockEntity)) {
            return false;
        }

        StatueComponent component = blockEntity.getStatueData();
        if (component == null) {
            return false;
        }

        if (component.targetType() == TargetType.PLAYER) {
            return restorePlayerStatue(player, serverLevel, pos, component);
        }

        return restoreBiologicalStatue(serverLevel, pos, component);
    }

    public static boolean restoreFromInventory(Player player, ItemStack powderStack) {
        if (player.level().isClientSide()) {
            return false;
        }

        ItemStack statueStack = findStatueInInventory(player);
        if (statueStack.isEmpty()) {
            return false;
        }

        StatueComponent component = statueStack.get(ModDataComponents.STATUE_DATA.get());
        if (component == null) {
            return false;
        }

        if (component.targetType() == TargetType.PLAYER) {
            Optional<UUID> uuid = component.entityUuid();
            if (uuid.isPresent()) {
                ServerPlayer online = ((ServerLevel) player.level()).getServer().getPlayerList().getPlayer(uuid.get());
                if (online != null) {
                    player.sendSystemMessage(Component.translatable("message.petrification.player_online"));
                    return false;
                }
            }
        }

        Vec3 spawnPos = player.position().add(player.getLookAngle().scale(2));
        if (component.targetType() == TargetType.PLAYER) {
            restorePlayerAt(((ServerLevel) player.level()).getServer().overworld(), spawnPos, component);
        } else {
            restoreBiologicalAt((ServerLevel) player.level(), spawnPos, component);
        }

        statueStack.shrink(1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    private static ItemStack findStatueInInventory(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.STATUE_BLOCK_ITEM.get()) && stack.has(ModDataComponents.STATUE_DATA.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean restorePlayerStatue(Player actor, ServerLevel level, BlockPos pos, StatueComponent component) {
        Optional<UUID> uuidOpt = component.entityUuid();
        if (uuidOpt.isEmpty()) {
            return false;
        }

        UUID uuid = uuidOpt.get();
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(uuid);
        if (online != null) {
            online.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            applyPlayerData(online, component);
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            online.sendSystemMessage(Component.translatable("message.petrification.restored"));
            level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
            return true;
        }

        PlayerMaterializationSavedData savedData = PlayerMaterializationSavedData.get(level);
        if (savedData.hasEntry(uuid)) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            savedData.removeEntry(uuid);
            if (actor != null) {
                actor.sendSystemMessage(Component.translatable("message.petrification.offline_restored"));
            }
            level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
            return true;
        }

        return false;
    }

    private static boolean restoreBiologicalStatue(ServerLevel level, BlockPos pos, StatueComponent component) {
        Vec3 spawnPos = Vec3.atBottomCenterOf(pos);
        restoreBiologicalAt(level, spawnPos, component);
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    /** Restores every statue block within radius of center (used by the splash restoration potion). */
    public static void restoreArea(ServerLevel level, Vec3 center, double radius) {
        int r = (int) Math.ceil(radius);
        BlockPos centerPos = BlockPos.containing(center);

        for (BlockPos pos : BlockPos.betweenClosed(centerPos.offset(-r, -r, -r), centerPos.offset(r, r, r))) {
            if (center.distanceTo(Vec3.atCenterOf(pos)) > radius) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof StatueBlockEntity)) {
                continue;
            }
            StatueComponent component = level.getBlockEntity(pos) instanceof StatueBlockEntity be ? be.getStatueData() : null;
            if (component == null) {
                continue;
            }
            if (component.targetType() == TargetType.PLAYER) {
                restorePlayerStatue(null, level, pos, component);
            } else {
                restoreBiologicalStatue(level, pos, component);
            }
        }
    }

    private static void restoreBiologicalAt(ServerLevel level, Vec3 pos, StatueComponent component) {
        Optional<? extends net.minecraft.core.Holder.Reference<EntityType<?>>> entityRef =
                BuiltInRegistries.ENTITY_TYPE.get(component.entityType());
        if (entityRef.isEmpty()) {
            return;
        }

        EntityType<?> entityType = entityRef.get().value();
        net.minecraft.world.entity.Entity rawEntity = entityType.create(level, EntitySpawnReason.COMMAND);
        if (rawEntity == null) {
            return;
        }

        // Full-NBT restore: tamed/owner, saddle, NoAI and every other vanilla tag survive the
        // petrify -> restore cycle. The captured NBT carries the original position, so it is
        // loaded first and the entity is then moved onto the statue spot.
        component.entityNbt().ifPresent(tag -> MaterializationHelper.loadEntityNbt(rawEntity, tag));
        rawEntity.snapTo(pos.x, pos.y, pos.z, component.yaw(), component.pitch());

        if (!(rawEntity instanceof LivingEntity entity)) {
            // Non-living statue targets (boat / end crystal): the NBT restore already covered
            // their state; the name is re-applied for legacy statues captured without NBT.
            component.customName().ifPresent(name -> rawEntity.setCustomName(Component.literal(name)));
            level.addFreshEntity(rawEntity);
            return;
        }

        entity.setYHeadRot(component.headYaw());
        entity.setSprinting(component.isSprinting());
        entity.setShiftKeyDown(component.isSneaking());
        // Re-apply the captured baby state: dispatched through Mob#setBaby rather than an
        // AgeableMob check, since zombies/husks/drowned and piglins implement their own baby
        // flag without extending AgeableMob. Without this baby mobs (e.g. baby turtles) grow
        // into adults on restore.
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.setBaby(component.isBaby());
        }
        component.health().ifPresent(entity::setHealth);
        component.customName().ifPresent(name -> entity.setCustomName(Component.literal(name)));
        component.equipment().ifPresent(tag -> MaterializationHelper.restoreEquipment(entity, tag));
        MaterializationHelper.applyVariant(entity, component);

        level.addFreshEntity(entity);
    }

    private static void restorePlayerAt(ServerLevel level, Vec3 pos, StatueComponent component) {
        // Player restoration via name lookup is not supported in MC 26.2
        // (getProfileCache has been removed). Rely on UUID-based lookup only.
    }

    public static void applyPlayerDataPublic(ServerPlayer player, StatueComponent component) {
        applyPlayerData(player, component);
    }

    private static void applyPlayerData(ServerPlayer player, StatueComponent component) {
        component.foodLevel().ifPresent(level -> player.getFoodData().setFoodLevel(level));
        component.saturation().ifPresent(sat -> player.getFoodData().setSaturation(sat));
        component.experienceLevel().ifPresent(level -> player.experienceLevel = level);
        component.experienceProgress().ifPresent(progress -> player.experienceProgress = progress);
        component.totalExperience().ifPresent(exp -> player.totalExperience = exp);
        component.air().ifPresent(player::setAirSupply);
        component.fallDistance().ifPresent(dist -> player.fallDistance = dist);
        component.equipment().ifPresent(tag -> MaterializationHelper.restoreEquipment(player, tag));
    }

    public static boolean waxCopper(Player player, Level level, BlockPos pos) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof StatueBlockEntity blockEntity)) {
            return false;
        }

        StatueComponent component = blockEntity.getStatueData();
        if (component == null || component.materialType() != MaterialType.COPPER || component.isWaxed()) {
            return false;
        }

        blockEntity.setStatueData(component.withWaxed(true));
        level.playSound(null, pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0f, 1.0f);
        return true;
    }

    public static boolean waxCopperItem(Player player, ItemStack stack) {
        StatueComponent component = stack.get(ModDataComponents.STATUE_DATA.get());
        if (component == null || component.materialType() != MaterialType.COPPER || component.isWaxed()) {
            return false;
        }
        stack.set(ModDataComponents.STATUE_DATA.get(), component.withWaxed(true));
        player.level().playSound(null, player.blockPosition(), SoundEvents.HONEYCOMB_WAX_ON, SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    public static boolean useRestorationPowderOnBlock(Player player, Level level, BlockPos pos, ItemStack powder) {
        if (restoreBlock(player, level, pos)) {
            if (!player.getAbilities().instabuild) {
                powder.shrink(1);
            }
            return true;
        }
        return false;
    }

    public static boolean useHoneycombOnBlock(Player player, Level level, BlockPos pos, ItemStack honeycomb) {
        if (waxCopper(player, level, pos)) {
            if (!player.getAbilities().instabuild) {
                honeycomb.shrink(1);
            }
            return true;
        }
        return false;
    }
}
