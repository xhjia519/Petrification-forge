package com.xiong.petrification.registry;

import com.xiong.petrification.block.MiniStructureBlock;
import com.xiong.petrification.block.PetrificationCoreBlock;
import com.xiong.petrification.block.RestorationBlock;
import com.xiong.petrification.block.StatueBlock;
import com.xiong.petrification.platform.Ids;
import com.xiong.petrification.platform.Platform;
import com.xiong.petrification.platform.Registrar;
import com.xiong.petrification.platform.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    public static final Registrar<Block> BLOCKS = Platform.registrar(Registries.BLOCK);

    public static final RegistryEntry<StatueBlock> STATUE_BLOCK = BLOCKS.register("statue_block",
            () -> new StatueBlock(BlockBehaviour.Properties.of()
                    .setId(Ids.block("statue_block"))
                    .mapColor(MapColor.STONE)
                    .strength(2.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .pushReaction(PushReaction.PUSH_ONLY)));

    // 微缩建筑方块：放置后渲染建筑微缩模型，右键整栋还原
    public static final RegistryEntry<MiniStructureBlock> MINI_STRUCTURE_BLOCK = BLOCKS.register("mini_structure_block",
            () -> new MiniStructureBlock(BlockBehaviour.Properties.of()
                    .setId(Ids.block("mini_structure_block"))
                    .mapColor(MapColor.WOOD)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.PUSH_ONLY)));

    // 石化核心：燃料激活后持续石化范围内生物（GUI 配置范围/白名单）
    public static final RegistryEntry<PetrificationCoreBlock> PETRIFICATION_CORE_BLOCK = BLOCKS.register("petrification_core",
            () -> new PetrificationCoreBlock(BlockBehaviour.Properties.of()
                    .setId(Ids.block("petrification_core"))
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(3.0f, 9.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(_ -> 7)
                    .pushReaction(PushReaction.PUSH_ONLY)));

    // 还原方块：放入还原粉后自动还原范围内雕像（每还原一个消耗 1 粉，GUI 配置范围）
    public static final RegistryEntry<RestorationBlock> RESTORATION_BLOCK = BLOCKS.register("restoration_block",
            () -> new RestorationBlock(BlockBehaviour.Properties.of()
                    .setId(Ids.block("restoration_block"))
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.0f, 9.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(_ -> 7)
                    .pushReaction(PushReaction.PUSH_ONLY)));

    private ModBlocks() {}

    public static void register() {
        BLOCKS.bind();
    }
}
