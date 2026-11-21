package com.xiong.petrification.client;

import com.xiong.petrification.block.RestorationBlockEntity;
import com.xiong.petrification.menu.RestorationMenu;
import com.xiong.petrification.network.GuiPosPayload;
import com.xiong.petrification.network.ModNetwork;
import com.xiong.petrification.network.RestorationSetRadiusPayload;
import com.xiong.petrification.network.RestorationSetRedstonePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 还原方块 GUI（320x240 全屏式，左右两栏）：
 * 左栏：还原粉槽 / 粉量状态 / 说明；右栏：范围滑块。底部为玩家背包与快捷栏。
 * 放入还原粉即自动还原范围内雕像（每还原一个消耗 1 粉），无激活概念。
 */
public class RestorationScreen extends AbstractContainerScreen<RestorationMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.parse("petrification:textures/gui/container/restoration_block.png");

    // 配色（浅色调面板 #C6C6C6 上使用亮色文字）
    private static final int SLIDER_TRACK = 0xFF555555;
    private static final int SLIDER_KNOB = 0xFFFFFFFF;
    private static final int TEXT_DARK = 0xFFC6C6C6;
    private static final int TEXT_BRIGHT = 0xFFFFFFFF;
    private static final int GREEN = 0xFF55FF55;

    private Font font;
    private int dragRadius = -1; // -1 = 未拖动
    @Nullable
    private BlockPos blockPos; // 打开瞬间从 hitResult 捕获；菜单不携带方块位置

    // 布局（320x240）
    private static final int COL_LEFT = 8;
    private static final int COL_RIGHT = 164;
    private static final int DUST_LABEL_Y = 20;
    private static final int STATUS_Y = 58;
    private static final int HINT_Y = 72;
    private static final int BTN_X = COL_LEFT;
    private static final int BTN_W = 148;
    private static final int BTN_H = 18;
    private static final int REDSTONE_Y = 92;
    private static final int SLIDER_LABEL_Y = 20;
    private static final int SLIDER_Y = 35;
    private static final int SLIDER_X = COL_RIGHT;
    private static final int SLIDER_WIDTH = 100;

    public RestorationScreen(RestorationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 320;
        this.imageHeight = 240;

    }

    @Override
    protected void init() {
        super.init();
        this.font = this.minecraft.font;
        // 标题/背包标签定位（继承的 1.21.11 布局基于 176x166，这里按 320x240 纹理重排）
        this.titleLabelX = (imageWidth - font.width(title)) / 2;
        this.titleLabelY = 6;
        this.inventoryLabelX = 72;
        this.inventoryLabelY = 154;
        // 隐藏 HUD 快捷栏（ForgeLayeredDraw 烘焙需在 Gui 构造后执行，见 ClientSetup）
        ClientSetup.ensureHudHotbarHiddenOnCoreGui();
        if (blockPos == null) {
            blockPos = detectBlockPos();
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        RestorationBlockEntity blockEntity = blockEntity();
        int dustCount = menu.getDustContainer().getItem(0).getCount();

        // ==== 左栏：粉量 / 状态 / 说明 ====
        g.drawString(font, Component.translatable("gui.petrification.restoration.dust"),
                leftPos + COL_LEFT, topPos + DUST_LABEL_Y, TEXT_DARK);
        Component status = dustCount > 0
                ? Component.translatable("gui.petrification.restoration.status_dust", dustCount)
                : Component.translatable("gui.petrification.restoration.status_empty");
        g.drawString(font, status, leftPos + COL_LEFT, topPos + STATUS_Y, dustCount > 0 ? GREEN : TEXT_DARK);
        g.drawString(font, Component.translatable("gui.petrification.restoration.hint"),
                leftPos + COL_LEFT, topPos + HINT_Y, TEXT_DARK);

        // 红石控制按钮（开启后仅在收到红石信号时自动还原）
        boolean redstone = blockEntity != null && blockEntity.isRedstoneControlled();
        boolean redstoneHovered = mouseX >= leftPos + BTN_X && mouseX < leftPos + BTN_X + BTN_W
                && mouseY >= topPos + REDSTONE_Y && mouseY < topPos + REDSTONE_Y + BTN_H;
        int redstoneColor = redstone ? 0xFF2E7D32 : (redstoneHovered ? 0xFFB4B4B4 : 0xFF9B9B9B);
        g.fill(leftPos + BTN_X, topPos + REDSTONE_Y, leftPos + BTN_X + BTN_W, topPos + REDSTONE_Y + BTN_H, redstoneColor);
        Component redstoneLabel = Component.translatable(redstone
                ? "gui.petrification.restoration.redstone_on" : "gui.petrification.restoration.redstone_off");
        g.drawString(font, redstoneLabel,
                leftPos + BTN_X + (BTN_W - font.width(redstoneLabel)) / 2,
                topPos + REDSTONE_Y + (BTN_H - 8) / 2, TEXT_BRIGHT);

        // ==== 右栏：范围滑块 ====
        int radius = dragRadius >= 0 ? dragRadius : (blockEntity != null ? blockEntity.getRadius() : 15);
        g.drawString(font, Component.translatable("gui.petrification.restoration.radius", radius),
                leftPos + SLIDER_X, topPos + SLIDER_LABEL_Y, TEXT_DARK);
        g.fill(leftPos + SLIDER_X, topPos + SLIDER_Y, leftPos + SLIDER_X + SLIDER_WIDTH, topPos + SLIDER_Y + 3, SLIDER_TRACK);
        float t = (radius - RestorationBlockEntity.MIN_RADIUS)
                / (float) (RestorationBlockEntity.MAX_RADIUS - RestorationBlockEntity.MIN_RADIUS);
        int knobX = leftPos + SLIDER_X + (int) (t * (SLIDER_WIDTH - 8));
        g.fill(knobX, topPos + SLIDER_Y - 3, knobX + 8, topPos + SLIDER_Y + 7, SLIDER_KNOB);
    }

    // ===== 交互 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = mouseX;
        double my = mouseY;
        // 红石控制按钮
        if (mx >= leftPos + BTN_X && mx < leftPos + BTN_X + BTN_W
                && my >= topPos + REDSTONE_Y && my < topPos + REDSTONE_Y + BTN_H) {
            RestorationBlockEntity blockEntity = blockEntity();
            if (blockEntity != null) {
                ModNetwork.sendToServer(new RestorationSetRedstonePayload(!blockEntity.isRedstoneControlled()));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && mouseX >= leftPos + SLIDER_X && mouseX <= leftPos + SLIDER_X + SLIDER_WIDTH
                && mouseY >= topPos + SLIDER_Y - 4 && mouseY <= topPos + SLIDER_Y + 8) {
            dragRadius = radiusFromX(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragRadius >= 0 && button == 0) {
            ModNetwork.sendToServer(new RestorationSetRadiusPayload(dragRadius));
            dragRadius = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int radiusFromX(double mouseX) {
        float t = (float) (mouseX - (leftPos + SLIDER_X)) / SLIDER_WIDTH;
        t = Math.max(0.0f, Math.min(1.0f, t));
        return Math.round(RestorationBlockEntity.MIN_RADIUS
                + t * (RestorationBlockEntity.MAX_RADIUS - RestorationBlockEntity.MIN_RADIUS));
    }

    private RestorationBlockEntity blockEntity() {
        if (minecraft.level == null) {
            return null;
        }
        BlockPos pos = blockPos != null ? blockPos : menu.getBlockPos();
        if (pos == null) {
            return null;
        }
        if (minecraft.level.getBlockEntity(pos) instanceof RestorationBlockEntity blockEntity) {
            return blockEntity;
        }
        return null;
    }

    /** 优先用服务端下发的方块位置（GuiPosPayload），其次取打开 GUI 瞬间的瞄准方块。 */
    private @Nullable BlockPos detectBlockPos() {
        BlockPos networkPos = GuiPosPayload.lastPos();
        if (networkPos != null) {
            return networkPos;
        }
        if (minecraft.hitResult instanceof BlockHitResult hit) {
            return hit.getBlockPos();
        }
        return null;
    }
}
