package com.xiong.petrification.registry;

import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.item.RestorationPowderItem;
import com.xiong.petrification.item.StatueBlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.bus.BusGroup;
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
                    .stacksTo(16)));

    private ModItems() {}

    public static void register(BusGroup modBusGroup) {
        ITEMS.register(modBusGroup);
    }
}
