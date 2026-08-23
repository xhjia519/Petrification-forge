package com.xiong.petrification.registry;

import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.block.MiniStructureBlockEntity;
import com.xiong.petrification.block.StatueBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PetrificationMod.MODID);

    public static final RegistryObject<BlockEntityType<StatueBlockEntity>> STATUE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("statue_block_entity", () ->
                    new BlockEntityType<>(StatueBlockEntity::new, Set.of(ModBlocks.STATUE_BLOCK.get())));

    // 微缩建筑方块实体（渲染 + 数据同步）
    public static final RegistryObject<BlockEntityType<MiniStructureBlockEntity>> MINI_STRUCTURE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("mini_structure_block_entity", () ->
                    new BlockEntityType<>(MiniStructureBlockEntity::new, Set.of(ModBlocks.MINI_STRUCTURE_BLOCK.get())));

    private ModBlockEntities() {}

    public static void register(BusGroup modBusGroup) {
        BLOCK_ENTITIES.register(modBusGroup);
    }
}
