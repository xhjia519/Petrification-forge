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
 * 还原方块：右键打开专用 GUI（还原粉槽 + 范围滑块）。
 * 放置后由方块实体自动还原范围内雕像方块（每还原一个消耗 1 个还原粉）；
 * 本方块只负责渲染外观与打开菜单。
 */
public class RestorationBlock extends BaseEntityBlock {
    public static final MapCodec<RestorationBlock> CODEC = simpleCodec(RestorationBlock::new);

    public RestorationBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RestorationBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.RESTORATION_BLOCK_ENTITY.get(),
                RestorationBlockEntity::serverTick);
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

    /** 玩家破坏方块：掉落容器内剩余还原粉（仅服务端；创造模式不掉，与原版容器一致）。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.preventsBlockDrops()
                && level.getBlockEntity(pos) instanceof RestorationBlockEntity blockEntity) {
            blockEntity.dropContents(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((containerId, playerInventory, player) ->
                new com.xiong.petrification.menu.RestorationMenu(containerId, playerInventory, pos),
                Component.translatable("container.petrification.restoration_block"));
    }
}
