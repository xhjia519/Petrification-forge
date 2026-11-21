package com.xiong.petrification.block;

import com.xiong.petrification.component.StructureStatueData;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (structureData != null) {
            tag.put("StructureData", StructureStatueData.CODEC.encodeStart(NbtOps.INSTANCE, structureData).result().orElseThrow());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        structureData = StructureStatueData.CODEC.parse(NbtOps.INSTANCE, tag.get("StructureData")).result().orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // 把压缩数据一并同步给客户端，否则渲染器拿到 null 什么都不画
        return saveWithoutMetadata(registries);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 被活塞推动后恢复结构数据（PistonEvent.Pre 已保存 NBT）
        PistonMoveData.restoreIfMoved(this);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
