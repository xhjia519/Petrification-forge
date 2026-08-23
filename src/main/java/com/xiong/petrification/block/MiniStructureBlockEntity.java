package com.xiong.petrification.block;

import com.xiong.petrification.component.StructureStatueData;
import com.xiong.petrification.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/** 微缩建筑方块实体：持有压缩数据并同步到客户端供渲染器使用。 */
public class MiniStructureBlockEntity extends BlockEntity {
    @Nullable
    private StructureStatueData structureData;

    public MiniStructureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINI_STRUCTURE_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable
    public StructureStatueData getStructureData() {
        return structureData;
    }

    public void setStructureData(@Nullable StructureStatueData data) {
        this.structureData = data;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (structureData != null) {
            output.store("StructureData", StructureStatueData.CODEC, structureData);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        structureData = input.read("StructureData", StructureStatueData.CODEC).orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // 把压缩数据一并同步给客户端，否则渲染器拿到 null 什么都不画
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
