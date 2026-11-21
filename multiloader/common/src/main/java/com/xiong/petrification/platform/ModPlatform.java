package com.xiong.petrification.platform;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * 加载器抽象。每个加载器模块在模组构造的最开始安装自己的实现，
 * 之后 common 中的所有注册/菜单/环境判断都通过这里完成。
 */
public interface ModPlatform {

    /** 加载器名称，例如 "forge" / "neoforge"。 */
    String name();

    /** 创建一个注册表注册器。 */
    <T> Registrar<T> createRegistrar(ResourceKey<? extends Registry<T>> registry);

    /** 创建带网络数据工厂的菜单类型（两侧加载器 API 不同）。 */
    <M extends AbstractContainerMenu> MenuType<M> createMenuType(MenuFactory<M> factory);

    /** 是否为客户端分发。 */
    boolean isClient();
}
