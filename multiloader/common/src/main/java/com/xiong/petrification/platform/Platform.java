package com.xiong.petrification.platform;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.Objects;
import java.util.function.Supplier;

/** 加载器实现的全局入口。common 代码只依赖本类，不直接接触任一方加载器 API。 */
public final class Platform {

    private static ModPlatform implementation;

    private Platform() {}

    public static void install(ModPlatform platform) {
        if (implementation != null && implementation != platform) {
            throw new IllegalStateException("Platform already installed: " + implementation.name());
        }
        implementation = Objects.requireNonNull(platform, "platform");
    }

    public static ModPlatform get() {
        if (implementation == null) {
            throw new IllegalStateException("Platform not installed yet; loader entrypoint must call Platform.install() first");
        }
        return implementation;
    }

    public static String name() {
        return get().name();
    }

    public static boolean isClient() {
        return get().isClient();
    }

    public static <T> Registrar<T> registrar(ResourceKey<? extends Registry<T>> registry) {
        return get().createRegistrar(registry);
    }

    public static <M extends AbstractContainerMenu> MenuType<M> menuType(MenuFactory<M> factory) {
        return get().createMenuType(factory);
    }

    /** 延迟取用注册项，避免静态初始化顺序问题。 */
    public static <T> Supplier<T> lazy(RegistryEntry<T> entry) {
        return entry;
    }
}
