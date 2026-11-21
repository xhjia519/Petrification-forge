package com.xiong.petrification.platform;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/** 菜单的网络构造工厂，语义与 Forge IContainerFactory / NeoForge 对应接口一致。 */
@FunctionalInterface
public interface MenuFactory<M extends AbstractContainerMenu> {
    M create(int containerId, Inventory playerInventory, FriendlyByteBuf buffer);
}
