package com.xiong.petrification.menu;

import com.xiong.petrification.block.RestorationBlockEntity;
import com.xiong.petrification.network.GuiPosPayload;
import com.xiong.petrification.network.ModNetwork;
import com.xiong.petrification.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 还原方块菜单：1 个还原粉槽 + 标准背包/快捷栏（320x240 全屏布局）。
 * 服务端槽位直接绑定方块实体的粉容器（粉持久化在方块内），GUI 打开时自动还原也不中断；
 * 客户端重建时（无 pos）用本地空容器占位，内容由服务端 broadcastChanges 同步。
 */
public class RestorationMenu extends AbstractContainerMenu {
    private static final int DUST_SLOT = 0;
    private final @Nullable BlockPos blockPos;
    private final Player player;
    private SimpleContainer dustContainer = new SimpleContainer(1);

    public RestorationMenu(int containerId, Inventory playerInventory, @Nullable BlockPos blockPos) {
        super(ModMenus.RESTORATION.get(), containerId);
        this.blockPos = blockPos;
        this.player = playerInventory.player;

        // 服务端打开 GUI 时下发方块位置（26.2 菜单不携带额外数据，客户端靠此定位方块实体）
        if (blockPos != null && !player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendToClient(new GuiPosPayload(blockPos), serverPlayer);
        }

        // 服务端：槽位直接绑定方块实体容器，粉不入 Menu 暂存
        if (blockPos != null
                && player.level().getBlockEntity(blockPos) instanceof RestorationBlockEntity blockEntity) {
            dustContainer = blockEntity.getDustContainer();
        }
        addSlot(new Slot(dustContainer, DUST_SLOT, 32, 32));
        // 注意：26.2 的 addStandardInventorySlots 已包含快捷栏（主背包 27 格 + 快捷栏 9 格），
        // 再手动 addInventoryHotbarSlots 会重复添加快捷栏 → 渲染两层错位的快捷栏。
        addStandardInventorySlots(playerInventory, 72, 166);
    }

    /** IContainerFactory：26.2 中原版 Player.openMenu 不携带额外数据（buf 恒为 null）。 */
    public static RestorationMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        return new RestorationMenu(containerId, playerInventory, null);
    }

    public @Nullable BlockPos getBlockPos() {
        return blockPos;
    }

    public SimpleContainer getDustContainer() {
        return dustContainer;
    }

    /** C2S 滑块回调（RestorationSetRadiusPayload）：服务端写回方块实体，客户端 Menu 不处理。 */
    public void setRadius(int radius) {
        if (blockPos == null || player.level().isClientSide()) {
            return;
        }
        if (player.level().getBlockEntity(blockPos) instanceof RestorationBlockEntity blockEntity) {
            blockEntity.setRadius(radius);
        }
    }

    /** C2S 红石控制回调（RestorationSetRedstonePayload）：服务端写回方块实体。 */
    public void setRedstoneControlled(boolean enabled) {
        if (blockPos == null || player.level().isClientSide()) {
            return;
        }
        if (player.level().getBlockEntity(blockPos) instanceof RestorationBlockEntity blockEntity) {
            blockEntity.setRedstoneControlled(enabled);
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
