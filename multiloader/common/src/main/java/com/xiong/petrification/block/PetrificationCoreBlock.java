package com.xiong.petrification.block;

import com.mojang.serialization.MapCodec;
import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 石化核心方块：右键打开专用 GUI（燃料槽 + 范围滑块 + 白名单 + 玩家开关）。
 * 激活后由方块实体持续石化范围内生物；本方块只负责渲染外观与打开菜单。
 */
public class PetrificationCoreBlock extends BaseEntityBlock {
    public static final MapCodec<PetrificationCoreBlock> CODEC = simpleCodec(PetrificationCoreBlock::new);

    public PetrificationCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PetrificationCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.PETRIFICATION_CORE_BLOCK_ENTITY.get(),
                PetrificationCoreBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        serverPlayer.openMenu(getMenuProvider(state, level, pos));
        return InteractionResult.CONSUME;
    }

    /** 玩家破坏方块：掉落容器内剩余燃料（仅服务端；创造模式不掉，与原版容器一致）。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.preventsBlockDrops()
                && level.getBlockEntity(pos) instanceof PetrificationCoreBlockEntity core) {
            core.dropContents(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((containerId, playerInventory, player) ->
                new com.xiong.petrification.menu.PetrificationCoreMenu(containerId, playerInventory, pos),
                Component.translatable("container.petrification.petrification_core"));
    }
}
