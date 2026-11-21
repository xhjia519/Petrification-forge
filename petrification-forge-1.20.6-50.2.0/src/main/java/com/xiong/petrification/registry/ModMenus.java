package com.xiong.petrification.registry;

import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.menu.PetrificationCoreMenu;
import com.xiong.petrification.menu.RestorationMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, PetrificationMod.MODID);

    public static final RegistryObject<MenuType<PetrificationCoreMenu>> PETRIFICATION_CORE =
            MENUS.register("petrification_core",
                    () -> IForgeMenuType.create((IContainerFactory<PetrificationCoreMenu>) PetrificationCoreMenu::fromNetwork));

    public static final RegistryObject<MenuType<RestorationMenu>> RESTORATION =
            MENUS.register("restoration_block",
                    () -> IForgeMenuType.create((IContainerFactory<RestorationMenu>) RestorationMenu::fromNetwork));

    private ModMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
