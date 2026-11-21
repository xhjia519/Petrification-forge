package com.xiong.petrification.block;

import com.mojang.serialization.MapCodec;
import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.registry.ModBlockEntities;
import com.xiong.petrification.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StatueBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<StatueBlock> CODEC = simpleCodec(StatueBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public StatueBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // The statue is rendered entirely by the BlockEntityRenderer as the
        // original mob model with a material shader; no static cube is drawn.
        return RenderShape.INVISIBLE;
    }

    /** 活塞行为由配置决定：MOVE 可推（仅推不可拉）、DROP 推即掉落、BLOCK 推不动。 */
    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return switch (ModConfigSpecHolder.pistonBehavior) {
            case DROP -> PushReaction.DESTROY;
            case MOVE -> PushReaction.PUSH_ONLY;
            case BLOCK -> PushReaction.BLOCK;
        };
    }

    @Nullable
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatueBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? null : createTickerHelper(blockEntityType, ModBlockEntities.STATUE_BLOCK_ENTITY.get(), StatueBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof StatueBlockEntity blockEntity)) {
            return InteractionResult.PASS;
        }

        StatueComponent component = blockEntity.getStatueData();
        if (component == null) {
            return InteractionResult.PASS;
        }

        player.displayClientMessage(component.getDisplayComponent(), false);
        return InteractionResult.CONSUME;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        net.minecraft.world.level.block.entity.BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof StatueBlockEntity blockEntity) {
            StatueComponent component = blockEntity.getStatueData();
            if (component != null) {
                ItemStack stack = new ItemStack(ModItems.STATUE_BLOCK_ITEM.get());
                stack.set(com.xiong.petrification.registry.ModDataComponents.STATUE_DATA.get(), component);
                return List.of(stack);
            }
        }
        return super.getDrops(state, params);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        if (includeData && level.getBlockEntity(pos) instanceof StatueBlockEntity blockEntity) {
            StatueComponent component = blockEntity.getStatueData();
            if (component != null) {
                ItemStack stack = new ItemStack(ModItems.STATUE_BLOCK_ITEM.get());
                stack.set(com.xiong.petrification.registry.ModDataComponents.STATUE_DATA.get(), component);
                return stack;
            }
        }
        return super.getCloneItemStack(level, pos, state, includeData);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof StatueBlockEntity blockEntity) {
            StatueComponent component = blockEntity.getStatueData();
            if (component != null && component.materialType() == com.xiong.petrification.component.MaterialType.COPPER) {
                return (int) (component.oxidation() * 15.0f);
            }
        }
        return 0;
    }
}
