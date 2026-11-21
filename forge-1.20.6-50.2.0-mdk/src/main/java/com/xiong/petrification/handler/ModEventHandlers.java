package com.xiong.petrification.handler;

import com.xiong.petrification.block.StatueBlockEntity;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.data.MaterializedBlockSavedData;
import com.xiong.petrification.data.PlayerMaterializationSavedData;
import com.xiong.petrification.effect.MaterializationEffect;
import com.xiong.petrification.effect.RestorationEffect;
import com.xiong.petrification.registry.ModItems;
import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.util.MaterializationHelper;
import com.xiong.petrification.util.PistonMoveData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import static net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.xiong.petrification.PetrificationMod.MODID)
public final class ModEventHandlers {
    private ModEventHandlers() {}

    public static void register() {
        // Static @SubscribeEvent registration via annotation
    }

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect().value() instanceof MaterializationEffect) {
            event.setResult(Result.ALLOW);
        }
    }

    @SubscribeEvent
    public static void onPotionSplash(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownPotion potion)) {
            return;
        }
        if (!(potion.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack potionItem = potion.getItem();
        PotionContents contents = potionItem.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (MobEffectInstance instance : contents.getAllEffects()) {
            if (instance.getEffect().value() instanceof MaterializationEffect effect) {
                double radius = 3.0 * ModConfigSpecHolder.potionRangeMultiplier;
                MaterializationHelper.materializeArea(serverLevel, potion.position(), radius, effect.getMaterialType());
                MaterializationHelper.materializeBlocks(serverLevel, potion.position(), radius * 0.5, effect.getMaterialType());
            } else if (instance.getEffect().value() instanceof RestorationEffect) {
                double radius = 3.0 * ModConfigSpecHolder.potionRangeMultiplier;
                RestorationHandler.restoreArea(serverLevel, potion.position(), radius);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        if (held.is(ModItems.RESTORATION_POWDER.get())) {
            if (RestorationHandler.useRestorationPowderOnBlock(event.getEntity(), level, pos, held)) {
                event.setUseBlock(Result.DENY);
                event.setUseItem(Result.DENY);
                event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            }
        } else if (held.is(Items.HONEYCOMB)) {
            if (RestorationHandler.useHoneycombOnBlock(event.getEntity(), level, pos, held)) {
                event.setUseBlock(Result.DENY);
                event.setUseItem(Result.DENY);
                event.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        PlayerMaterializationSavedData data = PlayerMaterializationSavedData.get(level);
        data.getEntry(player.getUUID()).ifPresent(entry -> {
            player.teleportTo(entry.statuePos().getX() + 0.5, entry.statuePos().getY(), entry.statuePos().getZ() + 0.5);
            if (level.getBlockEntity(entry.statuePos()) instanceof StatueBlockEntity blockEntity) {
                StatueComponent component = blockEntity.getStatueData();
                if (component != null) {
                    RestorationHandler.applyPlayerDataPublic(player, component);
                }
            }
            level.setBlock(entry.statuePos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            data.removeEntry(player.getUUID());
        });
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        com.xiong.petrification.command.StatueCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    /** 活塞推方块前按配置处理：MOVE 保存方块实体数据、DROP 提前掉落容器内容。 */
    @SubscribeEvent
    public static void onPistonPre(PistonEvent.Pre event) {
        PistonMoveData.onPistonPre(event);
    }

    /** 每 tick 恢复活塞移走的方块实体数据（延迟到临时实例被移除后的最终存活 BE）。 */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent.Post event) {
        if (event.level instanceof ServerLevel serverLevel) {
            PistonMoveData.onLevelTick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos pos = event.getPos();
        MaterializedBlockSavedData blockData = MaterializedBlockSavedData.get(serverLevel);
        blockData.getBlockData(pos).ifPresent(data -> {
            blockData.removeBlockData(pos);
            // Play materialized block break particles
            serverLevel.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_BREAK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.8f);
        });

        // 已还原建筑：方块按正常方式挖掘（"破坏即整体重压缩"机制已移除，
        // 旧配置文件中遗留的 structure.recompressOnBreak 键会被自动忽略）
    }
}
