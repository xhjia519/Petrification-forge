package com.xiong.petrification.handler;

import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.block.StatueBlockEntity;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.data.MaterializedBlockSavedData;
import com.xiong.petrification.data.PlayerMaterializationSavedData;
import com.xiong.petrification.effect.MaterializationEffect;
import com.xiong.petrification.effect.RestorationEffect;
import com.xiong.petrification.registry.ModItems;
import com.xiong.petrification.util.MaterializationHelper;
import com.xiong.petrification.util.PistonMoveData;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;

/**
 * common 侧的事件业务逻辑。加载器模块只负责把各自的事件对象解包成这里的参数，
 * 因此本类不再出现任何加载器 API。
 */
public final class ModEventHandlers {
    private ModEventHandlers() {}

    /** MobEffectEvent.Applicable：材料化效果永远允许生效。 */
    public static boolean allowEffect(MobEffectInstance instance) {
        return instance.getEffect().value() instanceof MaterializationEffect;
    }

    /** 投掷药水命中：材料化药水石化范围内生物/方块，还原药水还原范围内雕像。 */
    public static void onPotionImpact(Projectile projectile) {
        if (!(projectile instanceof AbstractThrownPotion potion)) {
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

    /** 右键方块：还原粉 / 蜂巢处理。返回 true 表示已消费该次交互。 */
    public static boolean onRightClickBlock(Player player, Level level, BlockPos pos, ItemStack held) {
        if (held.is(ModItems.RESTORATION_POWDER.get())) {
            return RestorationHandler.useRestorationPowderOnBlock(player, level, pos, held);
        } else if (held.is(Items.HONEYCOMB)) {
            return RestorationHandler.useHoneycombOnBlock(player, level, pos, held);
        }
        return false;
    }

    /** 玩家登录：若上次离线时仍是雕像，则原地复活并恢复玩家数据。 */
    public static void onPlayerLogin(ServerPlayer player) {
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

    public static void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        com.xiong.petrification.command.StatueCommands.register(dispatcher, buildContext);
    }

    /**
     * 活塞推方块前按配置处理：MOVE 保存方块实体数据、DROP 就地掉落、BLOCK 阻止推动。
     *
     * @return true 表示加载器应取消该活塞事件
     */
    public static boolean onPistonPre(ServerLevel level, BlockPos pos, Direction direction,
                                      boolean retracting, PistonStructureResolver resolver) {
        return PistonMoveData.onPistonPre(level, pos, direction, retracting, resolver);
    }

    /** 每 tick 恢复活塞移走的方块实体数据（延迟到临时实例被移除后的最终存活 BE）。 */
    public static void onLevelTick(ServerLevel level) {
        PistonMoveData.onLevelTick(level);
    }

    public static void onBlockBreak(ServerLevel serverLevel, BlockPos pos) {
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
