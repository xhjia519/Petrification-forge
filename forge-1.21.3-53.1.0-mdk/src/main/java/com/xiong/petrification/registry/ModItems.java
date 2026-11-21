package com.xiong.petrification.registry;

import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.item.MiniStructureBlockItem;
import com.xiong.petrification.item.RestorationPowderItem;
import com.xiong.petrification.item.StatueBlockItem;
import com.xiong.petrification.item.StructureCoreItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PetrificationMod.MODID);

    public static final RegistryObject<StatueBlockItem> STATUE_BLOCK_ITEM = ITEMS.register("statue_block",
            () -> new StatueBlockItem(new Item.Properties()
                    .setId(ITEMS.key("statue_block"))
                    .stacksTo(16)));

    public static final RegistryObject<RestorationPowderItem> RESTORATION_POWDER = ITEMS.register("restoration_powder",
            () -> new RestorationPowderItem(new Item.Properties()
                    .setId(ITEMS.key("restoration_powder"))
                    .stacksTo(64)));

    // 六种稀有材料粉末，用于合成还原粉
    public static final RegistryObject<Item> TURTLE_SCUTE_POWDER = ITEMS.register("turtle_scute_powder",
            () -> new Item(new Item.Properties()
                    .setId(ITEMS.key("turtle_scute_powder"))));
    public static final RegistryObject<Item> PRISMARINE_SHARD_POWDER = ITEMS.register("prismarine_shard_powder",
            () -> new Item(new Item.Properties()
                    .setId(ITEMS.key("prismarine_shard_powder"))));
    public static final RegistryObject<Item> SULFUR_POWDER = ITEMS.register("sulfur_powder",
            () -> new Item(new Item.Properties()
                    .setId(ITEMS.key("sulfur_powder"))));
    public static final RegistryObject<Item> SHULKER_SHELL_POWDER = ITEMS.register("shulker_shell_powder",
            () -> new Item(new Item.Properties()
                    .setId(ITEMS.key("shulker_shell_powder"))));
    public static final RegistryObject<Item> LAPIS_LAZULI_POWDER = ITEMS.register("lapis_lazuli_powder",
            () -> new Item(new Item.Properties()
                    .setId(ITEMS.key("lapis_lazuli_powder"))));
    public static final RegistryObject<Item> AMETHYST_POWDER = ITEMS.register("amethyst_powder",
            () -> new Item(new Item.Properties()
                    .setId(ITEMS.key("amethyst_powder"))));

    // 建筑压缩核心：右键识别建筑，5 秒内再次右键执行压缩
    public static final RegistryObject<StructureCoreItem> STRUCTURE_CORE = ITEMS.register("structure_core",
            () -> new StructureCoreItem(new Item.Properties()
                    .setId(ITEMS.key("structure_core"))
                    .stacksTo(1)));

    // 微缩建筑物品：放置渲染微缩模型，右键还原整栋建筑
    public static final RegistryObject<MiniStructureBlockItem> MINI_STRUCTURE_BLOCK_ITEM = ITEMS.register("mini_structure_block",
            () -> new MiniStructureBlockItem(new Item.Properties()
                    .setId(ITEMS.key("mini_structure_block"))
                    .stacksTo(1)));

    // 石化核心方块物品
    public static final RegistryObject<BlockItem> PETRIFICATION_CORE_BLOCK_ITEM = ITEMS.register("petrification_core",
            () -> new BlockItem(ModBlocks.PETRIFICATION_CORE_BLOCK.get(), new Item.Properties()
                    .setId(ITEMS.key("petrification_core"))));

    // 还原方块物品
    public static final RegistryObject<BlockItem> RESTORATION_BLOCK_ITEM = ITEMS.register("restoration_block",
            () -> new BlockItem(ModBlocks.RESTORATION_BLOCK.get(), new Item.Properties()
                    .setId(ITEMS.key("restoration_block"))));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
