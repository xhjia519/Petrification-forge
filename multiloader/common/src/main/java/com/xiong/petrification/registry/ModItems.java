package com.xiong.petrification.registry;

import com.xiong.petrification.item.MiniStructureBlockItem;
import com.xiong.petrification.item.RestorationPowderItem;
import com.xiong.petrification.item.StatueBlockItem;
import com.xiong.petrification.item.StructureCoreItem;
import com.xiong.petrification.platform.Ids;
import com.xiong.petrification.platform.Platform;
import com.xiong.petrification.platform.Registrar;
import com.xiong.petrification.platform.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Registrar<Item> ITEMS = Platform.registrar(Registries.ITEM);

    public static final RegistryEntry<StatueBlockItem> STATUE_BLOCK_ITEM = ITEMS.register("statue_block",
            () -> new StatueBlockItem(new Item.Properties()
                    .setId(Ids.item("statue_block"))
                    .stacksTo(16)));

    public static final RegistryEntry<RestorationPowderItem> RESTORATION_POWDER = ITEMS.register("restoration_powder",
            () -> new RestorationPowderItem(new Item.Properties()
                    .setId(Ids.item("restoration_powder"))
                    .stacksTo(64)));

    // 六种稀有材料粉末，用于合成还原粉
    public static final RegistryEntry<Item> TURTLE_SCUTE_POWDER = ITEMS.register("turtle_scute_powder",
            () -> new Item(new Item.Properties()
                    .setId(Ids.item("turtle_scute_powder"))));
    public static final RegistryEntry<Item> PRISMARINE_SHARD_POWDER = ITEMS.register("prismarine_shard_powder",
            () -> new Item(new Item.Properties()
                    .setId(Ids.item("prismarine_shard_powder"))));
    public static final RegistryEntry<Item> SULFUR_POWDER = ITEMS.register("sulfur_powder",
            () -> new Item(new Item.Properties()
                    .setId(Ids.item("sulfur_powder"))));
    public static final RegistryEntry<Item> SHULKER_SHELL_POWDER = ITEMS.register("shulker_shell_powder",
            () -> new Item(new Item.Properties()
                    .setId(Ids.item("shulker_shell_powder"))));
    public static final RegistryEntry<Item> LAPIS_LAZULI_POWDER = ITEMS.register("lapis_lazuli_powder",
            () -> new Item(new Item.Properties()
                    .setId(Ids.item("lapis_lazuli_powder"))));
    public static final RegistryEntry<Item> AMETHYST_POWDER = ITEMS.register("amethyst_powder",
            () -> new Item(new Item.Properties()
                    .setId(Ids.item("amethyst_powder"))));

    // 建筑压缩核心：右键识别建筑，5 秒内再次右键执行压缩
    public static final RegistryEntry<StructureCoreItem> STRUCTURE_CORE = ITEMS.register("structure_core",
            () -> new StructureCoreItem(new Item.Properties()
                    .setId(Ids.item("structure_core"))
                    .stacksTo(1)));

    // 微缩建筑物品：放置渲染微缩模型，右键还原整栋建筑
    public static final RegistryEntry<MiniStructureBlockItem> MINI_STRUCTURE_BLOCK_ITEM = ITEMS.register("mini_structure_block",
            () -> new MiniStructureBlockItem(new Item.Properties()
                    .setId(Ids.item("mini_structure_block"))
                    .stacksTo(1)));

    // 石化核心方块物品
    public static final RegistryEntry<BlockItem> PETRIFICATION_CORE_BLOCK_ITEM = ITEMS.register("petrification_core",
            () -> new BlockItem(ModBlocks.PETRIFICATION_CORE_BLOCK.get(), new Item.Properties()
                    .setId(Ids.item("petrification_core"))));

    // 还原方块物品
    public static final RegistryEntry<BlockItem> RESTORATION_BLOCK_ITEM = ITEMS.register("restoration_block",
            () -> new BlockItem(ModBlocks.RESTORATION_BLOCK.get(), new Item.Properties()
                    .setId(Ids.item("restoration_block"))));

    private ModItems() {}

    public static void register() {
        ITEMS.bind();
    }
}
