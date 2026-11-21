package com.xiong.petrification.registry;

import com.xiong.petrification.block.MiniStructureBlockEntity;
import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import com.xiong.petrification.block.RestorationBlockEntity;
import com.xiong.petrification.block.StatueBlockEntity;
import com.xiong.petrification.platform.Platform;
import com.xiong.petrification.platform.Registrar;
import com.xiong.petrification.platform.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

public final class ModBlockEntities {
    public static final Registrar<BlockEntityType<?>> BLOCK_ENTITIES = Platform.registrar(Registries.BLOCK_ENTITY_TYPE);

    public static final RegistryEntry<BlockEntityType<StatueBlockEntity>> STATUE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("statue_block_entity", () ->
                    new BlockEntityType<>(StatueBlockEntity::new, Set.of(ModBlocks.STATUE_BLOCK.get())));

    // 微缩建筑方块实体（渲染 + 数据同步）
    public static final RegistryEntry<BlockEntityType<MiniStructureBlockEntity>> MINI_STRUCTURE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("mini_structure_block_entity", () ->
                    new BlockEntityType<>(MiniStructureBlockEntity::new, Set.of(ModBlocks.MINI_STRUCTURE_BLOCK.get())));

    // 石化核心方块实体（状态机 + 范围扫描）
    public static final RegistryEntry<BlockEntityType<PetrificationCoreBlockEntity>> PETRIFICATION_CORE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("petrification_core_block_entity", () ->
                    new BlockEntityType<>(PetrificationCoreBlockEntity::new, Set.of(ModBlocks.PETRIFICATION_CORE_BLOCK.get())));

    // 还原方块实体（还原粉槽 + 范围扫描还原）
    public static final RegistryEntry<BlockEntityType<RestorationBlockEntity>> RESTORATION_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("restoration_block_entity", () ->
                    new BlockEntityType<>(RestorationBlockEntity::new, Set.of(ModBlocks.RESTORATION_BLOCK.get())));

    private ModBlockEntities() {}

    public static void register() {
        BLOCK_ENTITIES.bind();
    }
}
