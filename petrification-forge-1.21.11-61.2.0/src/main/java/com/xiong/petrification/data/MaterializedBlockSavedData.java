package com.xiong.petrification.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xiong.petrification.component.MaterialType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MaterializedBlockSavedData extends SavedData {
    private static final String DATA_ID = "materialized_blocks";

    public record BlockMaterializationData(
            Identifier originalBlockId,
            MaterialType materialType,
            float oxidation,
            boolean isWaxed,
            long materializedTime
    ) {
        public static final Codec<BlockMaterializationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("originalBlockId").forGetter(BlockMaterializationData::originalBlockId),
                MaterialType.CODEC.fieldOf("materialType").forGetter(BlockMaterializationData::materialType),
                Codec.FLOAT.optionalFieldOf("oxidation", 0.0f).forGetter(BlockMaterializationData::oxidation),
                Codec.BOOL.optionalFieldOf("isWaxed", false).forGetter(BlockMaterializationData::isWaxed),
                Codec.LONG.optionalFieldOf("materializedTime", 0L).forGetter(BlockMaterializationData::materializedTime)
        ).apply(instance, BlockMaterializationData::new));
    }

    private record BlockEntry(BlockPos pos, BlockMaterializationData data) {
        private static final Codec<BlockEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(BlockEntry::pos),
                BlockMaterializationData.CODEC.fieldOf("data").forGetter(BlockEntry::data)
        ).apply(instance, BlockEntry::new));
    }

    private record Storage(List<BlockEntry> blocks) {
        private static final Codec<Storage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockEntry.CODEC.listOf().fieldOf("blocks").forGetter(Storage::blocks)
        ).apply(instance, Storage::new));
    }

    public static final SavedDataType<MaterializedBlockSavedData> TYPE = new SavedDataType<>(
            DATA_ID,
            MaterializedBlockSavedData::new,
            Storage.CODEC.xmap(
                    storage -> {
                        MaterializedBlockSavedData data = new MaterializedBlockSavedData();
                        for (BlockEntry entry : storage.blocks()) {
                            data.blocks.put(entry.pos(), entry.data());
                        }
                        return data;
                    },
                    data -> {
                        List<BlockEntry> entries = new ArrayList<>();
                        data.blocks.forEach((pos, d) -> entries.add(new BlockEntry(pos, d)));
                        return new Storage(entries);
                    }
            ),
            DataFixTypes.LEVEL
    );

    private final Map<BlockPos, BlockMaterializationData> blocks = new HashMap<>();

    public static MaterializedBlockSavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(TYPE);
    }

    public MaterializedBlockSavedData() {}

    public void setBlockData(BlockPos pos, BlockMaterializationData data) {
        blocks.put(pos.immutable(), data);
        setDirty();
    }

    public Optional<BlockMaterializationData> getBlockData(BlockPos pos) {
        return Optional.ofNullable(blocks.get(pos));
    }

    public void removeBlockData(BlockPos pos) {
        blocks.remove(pos);
        setDirty();
    }

    public Map<BlockPos, BlockMaterializationData> getAllBlocks() {
        return blocks;
    }
}
