package com.xiong.petrification.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.component.StatueComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlayerMaterializationSavedData extends SavedData {
    private static final Identifier DATA_ID =
            Identifier.fromNamespaceAndPath(PetrificationMod.MODID, "player_materialization");

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

    public static final SavedDataType<PlayerMaterializationSavedData> TYPE = new SavedDataType<>(
            DATA_ID,
            PlayerMaterializationSavedData::new,
            StorageData.CODEC.xmap(
                    storage -> {
                        PlayerMaterializationSavedData data = new PlayerMaterializationSavedData();
                        for (PlayerEntry entry : storage.entries()) {
                            data.entries.put(entry.uuid(), new Entry(entry.statuePos(), entry.component()));
                        }
                        return data;
                    },
                    data -> {
                        List<PlayerEntry> list = new ArrayList<>();
                        data.entries.forEach((uuid, entry) ->
                                list.add(new PlayerEntry(uuid, entry.statuePos(), entry.component())));
                        return new StorageData(list);
                    }
            ),
            DataFixTypes.LEVEL
    );

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static PlayerMaterializationSavedData get(ServerLevel level) {
        SavedDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(TYPE);
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
