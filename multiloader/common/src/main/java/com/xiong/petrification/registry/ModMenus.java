package com.xiong.petrification.registry;

import com.xiong.petrification.platform.Platform;
import com.xiong.petrification.platform.Registrar;
import com.xiong.petrification.platform.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import com.xiong.petrification.menu.PetrificationCoreMenu;
import com.xiong.petrification.menu.RestorationMenu;

public final class ModMenus {
    public static final Registrar<MenuType<?>> MENUS = Platform.registrar(Registries.MENU);

    public static final RegistryEntry<MenuType<PetrificationCoreMenu>> PETRIFICATION_CORE =
            MENUS.register("petrification_core",
                    () -> Platform.menuType(PetrificationCoreMenu::fromNetwork));

    public static final RegistryEntry<MenuType<RestorationMenu>> RESTORATION =
            MENUS.register("restoration_block",
                    () -> Platform.menuType(RestorationMenu::fromNetwork));

    private ModMenus() {}

    public static void register() {
        MENUS.bind();
    }
}
