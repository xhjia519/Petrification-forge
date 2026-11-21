package com.xiong.petrification.block;

import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.registry.ModBlockEntities;
import com.xiong.petrification.util.PistonMoveData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.NbtOps;
import org.jetbrains.annotations.Nullable;

public class StatueBlockEntity extends BlockEntity {
    private static final int OXIDATION_INTERVAL = 200;
    private int tickCounter;

    @Nullable
    private StatueComponent statueData;

    public StatueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATUE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, StatueBlockEntity blockEntity) {
        if (level.isClientSide() || !ModConfigSpecHolder.enableOxidation) {
            return;
        }

        blockEntity.tickCounter++;
        if (blockEntity.tickCounter < OXIDATION_INTERVAL) {
            return;
        }
        blockEntity.tickCounter = 0;

        StatueComponent component = blockEntity.statueData;
        if (component == null || component.materialType() != MaterialType.COPPER) {
            return;
        }
        if (component.isWaxed() || component.oxidation() >= ModConfigSpecHolder.maxOxidation) {
            return;
        }

        float newOxidation = (float) Math.min(ModConfigSpecHolder.maxOxidation,
                component.oxidation() + (float) ModConfigSpecHolder.oxidationRate);
        blockEntity.setStatueData(component.withOxidation(newOxidation));
    }

    @Nullable
    public StatueComponent getStatueData() {
        return statueData;
    }

    public void setStatueData(@Nullable StatueComponent data) {
        this.statueData = data;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (statueData != null) {
            tag.put("StatueData", StatueComponent.CODEC.encodeStart(NbtOps.INSTANCE, statueData).result().orElseThrow());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        statueData = StatueComponent.CODEC.parse(NbtOps.INSTANCE, tag.get("StatueData")).result().orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Include the saved statue data so the client-side block entity receives it,
        // otherwise the renderer sees a null component and draws nothing.
        return saveWithoutMetadata(registries);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 被活塞推动后恢复雕像数据（PistonEvent.Pre 已保存 NBT）
        PistonMoveData.restoreIfMoved(this);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
