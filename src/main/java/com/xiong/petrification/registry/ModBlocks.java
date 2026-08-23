package com.xiong.petrification.registry;

import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.block.MiniStructureBlock;
import com.xiong.petrification.block.StatueBlock;
import com.xiong.petrification.block.StatueBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, PetrificationMod.MODID);

    public static final RegistryObject<StatueBlock> STATUE_BLOCK = BLOCKS.register("statue_block",
            () -> new StatueBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("statue_block"))
                    .mapColor(MapColor.STONE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    // 微缩建筑方块：放置后渲染建筑微缩模型，右键整栋还原
    public static final RegistryObject<MiniStructureBlock> MINI_STRUCTURE_BLOCK = BLOCKS.register("mini_structure_block",
            () -> new MiniStructureBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("mini_structure_block"))
                    .mapColor(MapColor.WOOD)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    private ModBlocks() {}

    public static void register(BusGroup modBusGroup) {
        BLOCKS.register(modBusGroup);
    }
}
