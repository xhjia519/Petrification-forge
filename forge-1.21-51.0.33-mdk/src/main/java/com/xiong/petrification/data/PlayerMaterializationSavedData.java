package com.xiong.petrification.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xiong.petrification.component.StatueComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlayerMaterializationSavedData extends SavedData {
    private static final String DATA_ID = "player_materialization";

    public record Entry(BlockPos statuePos, StatueComponent component) {}

    private record PlayerEntry(UUID uuid, BlockPos statuePos, StatueComponent component) {
        private static final Codec<PlayerEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("uuid").forGetter(PlayerEntry::uuid),
                BlockPos.CODEC.fieldOf("pos").forGetter(PlayerEntry::statuePos),
                StatueComponent.CODEC.fieldOf("component").forGetter(PlayerEntry::component)
        ).apply(instance, PlayerEntry::new));
    }

    private record StorageData(List<PlayerEntry> entries) {
        private static final Codec<StorageData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PlayerEntry.CODEC.listOf().fieldOf("entries").forGetter(StorageData::entries)
        ).apply(instance, StorageData::new));
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static PlayerMaterializationSavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(PlayerMaterializationSavedData::new, PlayerMaterializationSavedData::load,
                        DataFixTypes.LEVEL),
                DATA_ID);
    }

    public static PlayerMaterializationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerMaterializationSavedData data = new PlayerMaterializationSavedData();
        StorageData.CODEC.parse(NbtOps.INSTANCE, tag).result().ifPresent(storage -> {
            for (PlayerEntry entry : storage.entries()) {
                data.entries.put(entry.uuid(), new Entry(entry.statuePos(), entry.component()));
            }
        });
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        List<PlayerEntry> list = new ArrayList<>();
        entries.forEach((uuid, entry) ->
                list.add(new PlayerEntry(uuid, entry.statuePos(), entry.component())));
        return StorageData.CODEC.encodeStart(NbtOps.INSTANCE, new StorageData(list))
                .result().filter(t -> t instanceof CompoundTag).map(t -> (CompoundTag) t).orElse(tag);
    }

    public PlayerMaterializationSavedData() {}

    public void storePlayerBackup(ServerPlayer player, BlockPos pos, StatueComponent component) {
        entries.put(player.getUUID(), new Entry(pos, component));
        setDirty();
    }

    public Optional<Entry> getEntry(UUID uuid) {
        return Optional.ofNullable(entries.get(uuid));
    }

    public void removeEntry(UUID uuid) {
        entries.remove(uuid);
        setDirty();
    }

    public boolean hasEntry(UUID uuid) {
        return entries.containsKey(uuid);
    }
}
