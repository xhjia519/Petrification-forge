package com.xiong.petrification.menu;

import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import com.xiong.petrification.network.GuiPosPayload;
import com.xiong.petrification.network.ModNetwork;
import com.xiong.petrification.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 石化核心菜单：1 个燃料槽 + 标准背包/快捷栏（320x240 全屏布局）。
 * 燃料槽直连方块实体的持久化容器（燃料属于方块，破坏时掉落）；
 * 点击"激活"按钮（CoreActivatePayload）后服务端一次性消耗，剩余留在槽内。
 */
public class PetrificationCoreMenu extends AbstractContainerMenu {
    private static final int FUEL_SLOT = 0;
    private final @Nullable BlockPos corePos;
    private final Player player;
    private SimpleContainer fuelContainer = new SimpleContainer(1);

    public PetrificationCoreMenu(int containerId, Inventory playerInventory, @Nullable BlockPos corePos) {
        super(ModMenus.PETRIFICATION_CORE.get(), containerId);
        this.corePos = corePos;
        this.player = playerInventory.player;

        // 服务端打开 GUI 时下发方块位置（26.2 菜单不携带额外数据，客户端靠此定位方块实体）
        if (corePos != null && !player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendToClient(new GuiPosPayload(corePos), serverPlayer);
        }

        // 服务端：燃料槽直连方块实体容器（燃料持久化在方块内，破坏方块时掉落）
        if (corePos != null
                && player.level().getBlockEntity(corePos) instanceof PetrificationCoreBlockEntity blockEntity) {
            fuelContainer = blockEntity.getFuelContainer();
        }

        addSlot(new Slot(fuelContainer, FUEL_SLOT, 9, 32));
        // 注意：26.2 的 addStandardInventorySlots 已包含快捷栏（主背包 27 格 + 快捷栏 9 格），
        // 再手动 addInventoryHotbarSlots 会重复添加快捷栏 → 渲染两层错位的快捷栏。
        addStandardInventorySlots(playerInventory, 72, 166);
    }

    /**
     * IContainerFactory：26.2 中原版 Player.openMenu 不携带额外数据（buf 恒为 null），
     * 客户端重建时无方块位置；服务端菜单由 Block.getMenuProvider 直接注入真实 pos。
     */
    public static PetrificationCoreMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        return new PetrificationCoreMenu(containerId, playerInventory, null);
    }

    public @Nullable BlockPos getCorePos() {
        return corePos;
    }

    /** 关闭激活按钮（服务端）：停止核心领域，配置/消耗统计保留，不要求重新支付。 */
    public boolean tryDeactivate() {
        if (player.level().isClientSide() || corePos == null) {
            return false;
        }
        if (player.level().getBlockEntity(corePos) instanceof PetrificationCoreBlockEntity core) {
            core.deactivate();
            return true;
        }
        return false;
    }

    /** 激活按钮（服务端）：按范围半径消耗对应数量燃料作为激活费，剩余留在槽位，非法燃料不消耗。 */
    public boolean tryActivate() {
        if (player.level().isClientSide() || corePos == null) {
            return false;
        }
        ItemStack fuel = fuelContainer.getItem(FUEL_SLOT);
        if (fuel.isEmpty()) {
            return false;
        }
        if (player.level().getBlockEntity(corePos) instanceof PetrificationCoreBlockEntity core
                && core.payWithFuel(fuel)) {
            // payWithFuel 内部已 shrink 消耗量，剩余留在槽位
            broadcastChanges();
            return true;
        }
        return false;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        // 燃料仅暂存槽位；激活由按钮显式触发，不再自动消费
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
