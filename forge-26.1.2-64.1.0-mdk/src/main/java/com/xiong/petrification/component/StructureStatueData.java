package com.xiong.petrification.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 建筑压缩数据：存储边界框内全部非空气方块（含水、植物、门、作物与内部方块）。
 * 压缩后的数据挂在微缩建筑方块/物品的数据组件上，放置还原时逐块恢复。
 */
public record StructureStatueData(
        // 建筑尺寸（用于还原定位与微缩渲染的各向异性缩放）
        int width,
        int height,
        int depth,

        // 方块数据（压缩存储，含内部方块/水/植物等全部非空气方块）
        int[] positions,          // 打包坐标: (x << 16) | (y << 8) | z，必须为相对边界框最小坐标的偏移量（0~63）
        int[] paletteIndices,     // 每个方块在 palette 映射表中的下标
        List<BlockState> palette, // BlockState -> 下标的映射表，重复方块只存一次（相当于注册名 ID 压缩）

        // 特殊方块数据：以打包坐标为键，精准对应每个带数据的方块（箱子、熔炉等），
        // 杜绝按下标存取导致的 TileEntity 数据错乱
        Map<Long, CompoundTag> tileEntityData,

        // 元数据
        String structureName,     // 结构注册名（如 "minecraft:village_plains"）或 "player_build"
        long compressedTime,      // 压缩时间戳
        int totalBlocks           // 外表面方块总数（用于统计）
) {
    // 玩家自制建筑的名称标记，展示时翻译为当前语言
    public static final String PLAYER_BUILD = "player_build";

    // int[] 编解码辅助
    private static final Codec<int[]> INT_ARRAY = Codec.INT.listOf()
            .xmap(list -> list.stream().mapToInt(Integer::intValue).toArray(),
                    array -> java.util.Arrays.stream(array).boxed().toList());

    // 带 BlockEntity 数据的方块条目（打包坐标 -> 完整 NBT）
    private record TileEntry(long packedPos, CompoundTag tag) {
        private static final Codec<TileEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("pos").forGetter(TileEntry::packedPos),
                CompoundTag.CODEC.fieldOf("tag").forGetter(TileEntry::tag)
        ).apply(instance, TileEntry::new));
    }

    public static final Codec<StructureStatueData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("width").forGetter(StructureStatueData::width),
            Codec.INT.fieldOf("height").forGetter(StructureStatueData::height),
            Codec.INT.fieldOf("depth").forGetter(StructureStatueData::depth),
            INT_ARRAY.fieldOf("positions").forGetter(StructureStatueData::positions),
            INT_ARRAY.fieldOf("paletteIndices").forGetter(StructureStatueData::paletteIndices),
            BlockState.CODEC.listOf().fieldOf("palette").forGetter(StructureStatueData::palette),
            TileEntry.CODEC.listOf().xmap(
                    entries -> {
                        Map<Long, CompoundTag> map = new HashMap<>();
                        for (TileEntry entry : entries) {
                            map.put(entry.packedPos(), entry.tag());
                        }
                        return map;
                    },
                    map -> {
                        List<TileEntry> entries = new ArrayList<>();
                        map.forEach((pos, tag) -> entries.add(new TileEntry(pos, tag)));
                        return entries;
                    }
            ).fieldOf("tileEntityData").forGetter(StructureStatueData::tileEntityData),
            Codec.STRING.fieldOf("structureName").forGetter(StructureStatueData::structureName),
            Codec.LONG.fieldOf("compressedTime").forGetter(StructureStatueData::compressedTime),
            Codec.INT.fieldOf("totalBlocks").forGetter(StructureStatueData::totalBlocks)
    ).apply(instance, StructureStatueData::new));

    // ===== 打包坐标解码（偏移量 0~63，还原时由锚点定位绝对坐标） =====

    public static int unpackX(int packed) {
        return (packed >> 16) & 0xFF;
    }

    public static int unpackY(int packed) {
        return (packed >> 8) & 0xFF;
    }

    public static int unpackZ(int packed) {
        return packed & 0xFF;
    }

    /** 取第 i 个外表面方块的方块状态（朝向、含水等完整保留）。 */
    public BlockState stateAt(int i) {
        return palette.get(paletteIndices[i]);
    }

    /** 建筑展示名：原版结构直接显示注册名，玩家建筑翻译为当前语言。 */
    public Component getDisplayName() {
        if (PLAYER_BUILD.equals(structureName)) {
            return Component.translatable("structure.petrification.player_build");
        }
        return Component.literal(structureName);
    }
}
