package com.xiong.petrification.block;

import com.xiong.petrification.component.StructureStatueData;
import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.registry.ModBlockEntities;
import com.xiong.petrification.registry.ModDataComponents;
import com.xiong.petrification.registry.ModItems;
import com.xiong.petrification.util.StructureCompressionHelper;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 微缩建筑方块：放置后由 BlockEntityRenderer 按原版纹理渲染建筑微缩模型，
 * 右键触发整栋建筑的分批还原，挖掘（作为方块）掉落携带数据的物品。
 */
public class MiniStructureBlock extends BaseEntityBlock {
    public static final MapCodec<MiniStructureBlock> CODEC = simpleCodec(MiniStructureBlock::new);

    public MiniStructureBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // 微缩建筑完全由 BlockEntityRenderer 渲染（各向异性缩放的方块模型），不绘制静态立方体
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
        return new MiniStructureBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof MiniStructureBlockEntity blockEntity)) {
            return InteractionResult.PASS;
        }

        StructureStatueData data = blockEntity.getStructureData();
        if (data == null) {
            return InteractionResult.PASS;
        }

        // 先移除微缩方块本体，再从锚点（本方块位置）开始分 Tick 批量还原建筑
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        StructureCompressionHelper.startRestore(serverLevel, data, pos);
        level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 0.6f);
        player.displayClientMessage(Component.translatable("message.petrification.structure_restoring",
                data.getDisplayName()), false);
        return InteractionResult.CONSUME;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // 掉落携带完整压缩数据的微缩建筑物品，保证挖掘无损
        net.minecraft.world.level.block.entity.BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof MiniStructureBlockEntity blockEntity) {
            StructureStatueData data = blockEntity.getStructureData();
            if (data != null) {
                ItemStack stack = new ItemStack(ModItems.MINI_STRUCTURE_BLOCK_ITEM.get());
                stack.set(ModDataComponents.STRUCTURE_STATUE_DATA.get(), data);
                return List.of(stack);
            }
        }
        return super.getDrops(state, params);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof MiniStructureBlockEntity blockEntity) {
            StructureStatueData data = blockEntity.getStructureData();
            if (data != null) {
                ItemStack stack = new ItemStack(ModItems.MINI_STRUCTURE_BLOCK_ITEM.get());
                stack.set(ModDataComponents.STRUCTURE_STATUE_DATA.get(), data);
                return stack;
            }
        }
        return super.getCloneItemStack(level, pos, state);
    }

    /**
     * Ctrl+中键（创造模式选取方块）实际走 Forge 的 5 参扩展方法，而非上面的 3 参方法。
     * 1.21.3 中两者互不委托，必须单独覆写，否则复制出的微缩建筑丢失压缩数据。
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target,
                                       LevelReader level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof MiniStructureBlockEntity blockEntity) {
            StructureStatueData data = blockEntity.getStructureData();
            if (data != null) {
                ItemStack stack = new ItemStack(ModItems.MINI_STRUCTURE_BLOCK_ITEM.get());
                stack.set(ModDataComponents.STRUCTURE_STATUE_DATA.get(), data);
                return stack;
            }
        }
        return super.getCloneItemStack(state, target, level, pos, player);
    }
}
