package com.xiong.petrification.registry;

import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.block.MiniStructureBlock;
import com.xiong.petrification.block.PetrificationCoreBlock;
import com.xiong.petrification.block.RestorationBlock;
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

    // 石化核心：燃料激活后持续石化范围内生物（GUI 配置范围/白名单）
    public static final RegistryObject<PetrificationCoreBlock> PETRIFICATION_CORE_BLOCK = BLOCKS.register("petrification_core",
            () -> new PetrificationCoreBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("petrification_core"))
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(3.0f, 9.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 7)));

    // 还原方块：放入还原粉后自动还原范围内雕像（每还原一个消耗 1 粉，GUI 配置范围）
    public static final RegistryObject<RestorationBlock> RESTORATION_BLOCK = BLOCKS.register("restoration_block",
            () -> new RestorationBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("restoration_block"))
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.0f, 9.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 7)));

    private ModBlocks() {}

    public static void register(BusGroup modBusGroup) {
        BLOCKS.register(modBusGroup);
    }
}
