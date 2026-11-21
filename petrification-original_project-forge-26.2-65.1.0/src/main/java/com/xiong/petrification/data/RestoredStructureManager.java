package com.xiong.petrification.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xiong.petrification.ModConfigSpecHolder;
import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.component.StructureStatueData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 已还原建筑的全局追踪管理器（绑定世界存档）：
 * 还原完成时把建筑边界框与压缩数据注册进来；之后玩家挖掘还原建筑的任意方块时，
 * 通过 find() 命中并整体重新压缩，防止丢方块。
 */
public class RestoredStructureManager extends SavedData {
    private static final Identifier DATA_ID = Identifier.fromNamespaceAndPath(PetrificationMod.MODID, "restored_structures");

    /** 一条已还原建筑的追踪记录：锚点 + 边界框 + 压缩数据快照。 */
    public record RestoredInfo(UUID id, BlockPos anchor, BoundingBox bounds, StructureStatueData data) {
        private static final Codec<RestoredInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                net.minecraft.core.UUIDUtil.CODEC.fieldOf("id").forGetter(RestoredInfo::id),
                BlockPos.CODEC.fieldOf("anchor").forGetter(RestoredInfo::anchor),
                BoundingBox.CODEC.fieldOf("bounds").forGetter(RestoredInfo::bounds),
                StructureStatueData.CODEC.fieldOf("data").forGetter(RestoredInfo::data)
        ).apply(instance, RestoredInfo::new));
    }

    private record Storage(List<RestoredInfo> structures) {
        private static final Codec<Storage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                RestoredInfo.CODEC.listOf().fieldOf("structures").forGetter(Storage::structures)
        ).apply(instance, Storage::new));
    }

    public static final SavedDataType<RestoredStructureManager> TYPE = new SavedDataType<>(
            DATA_ID,
            RestoredStructureManager::new,
            Storage.CODEC.xmap(
                    storage -> {
                        RestoredStructureManager manager = new RestoredStructureManager();
                        for (RestoredInfo info : storage.structures()) {
                            manager.activeStructures.put(info.id(), info);
                        }
                        return manager;
                    },
                    manager -> new Storage(new ArrayList<>(manager.activeStructures.values()))
            ),
            DataFixTypes.LEVEL
    );

    // LinkedHashMap 保持插入顺序，超限时按先进先出移除最旧建筑
    private final Map<UUID, RestoredInfo> activeStructures = new LinkedHashMap<>();

    public static RestoredStructureManager get(ServerLevel level) {
        SavedDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(TYPE);
    }

    public RestoredStructureManager() {}

    /** 还原完成后注册追踪；超过上限时移除最旧的记录，防止存档无限膨胀。 */
    public static void add(ServerLevel level, BlockPos anchor, StructureStatueData data) {
        RestoredStructureManager manager = get(level);
        BoundingBox bounds = new BoundingBox(
                anchor.getX(), anchor.getY(), anchor.getZ(),
                anchor.getX() + data.width() - 1,
                anchor.getY() + data.height() - 1,
                anchor.getZ() + data.depth() - 1);
        UUID id = UUID.randomUUID();
        manager.activeStructures.put(id, new RestoredInfo(id, anchor, bounds, data));
        while (manager.activeStructures.size() > ModConfigSpecHolder.structureMaxTracked) {
            UUID oldest = manager.activeStructures.keySet().iterator().next();
            manager.activeStructures.remove(oldest);
        }
        manager.setDirty();
    }

    /** 查找某个坐标所属的已还原建筑（用于挖掘时的整体重新压缩）。 */
    @Nullable
    public static RestoredInfo find(ServerLevel level, BlockPos pos) {
        for (RestoredInfo info : get(level).activeStructures.values()) {
            if (info.bounds().isInside(pos)) {
                return info;
            }
        }
        return null;
    }

    /** 移除一条追踪记录（建筑被重新压缩或再次压缩成微缩方块时调用）。 */
    public void removeById(UUID id) {
        activeStructures.remove(id);
        setDirty();
    }
}
