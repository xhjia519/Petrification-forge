package com.xiong.petrification.menu;

import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import com.xiong.petrification.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 石化核心菜单：1 个燃料槽 + 标准背包/快捷栏。
 * 燃料放入即被消费（服务端 slotsChanged 检测），驱动方块实体状态机。
 */
public class PetrificationCoreMenu extends AbstractContainerMenu {
    private static final int FUEL_SLOT = 0;
    private final BlockPos corePos;
    private final Player player;
    private final SimpleContainer fuelContainer = new SimpleContainer(1);

    public PetrificationCoreMenu(int containerId, Inventory playerInventory, BlockPos corePos) {
        super(ModMenus.PETRIFICATION_CORE.get(), containerId);
        this.corePos = corePos;
        this.player = playerInventory.player;

        addSlot(new Slot(fuelContainer, FUEL_SLOT, 96, 28));
        addStandardInventorySlots(playerInventory, 8, 148);
        addInventoryHotbarSlots(playerInventory, 8, 206);
    }

    /** IContainerFactory：客户端/服务端从网络包重建菜单。 */
    public static PetrificationCoreMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        return new PetrificationCoreMenu(containerId, playerInventory, buf.readBlockPos());
    }

    public BlockPos getCorePos() {
        return corePos;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container != fuelContainer || player.level().isClientSide()) {
            return;
        }
        ItemStack fuel = fuelContainer.getItem(FUEL_SLOT);
        if (fuel.isEmpty()) {
            return;
        }
        fuelContainer.setItem(FUEL_SLOT, ItemStack.EMPTY); // 一次性消耗
        if (player.level().getBlockEntity(corePos) instanceof PetrificationCoreBlockEntity core) {
            core.payWithFuel(fuel);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
