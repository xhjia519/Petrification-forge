package com.xiong.petrification.client;

import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import com.xiong.petrification.menu.PetrificationCoreMenu;
import com.xiong.petrification.network.CoreActivatePayload;
import com.xiong.petrification.network.CoreDeactivatePayload;
import com.xiong.petrification.network.CoreSetAffectPlayersPayload;
import com.xiong.petrification.network.CoreSetRadiusPayload;
import com.xiong.petrification.network.CoreSetRedstonePayload;
import com.xiong.petrification.network.CoreSetWhitelistPayload;
import com.xiong.petrification.network.GuiPosPayload;
import com.xiong.petrification.network.ModNetwork;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 石化核心 GUI（320x240 全屏式，左右两栏）：
 * 左栏：燃料槽 / 状态行 / 消耗显示 / 激活按钮；右栏：范围滑块 / 白名单搜索与勾选列表 / 玩家开关。
 * 底部为玩家背包与快捷栏。槽位凹槽已绘入背景纹理，blit 的 texW/texH 必须等于纹理实际尺寸。
 */
public class PetrificationCoreScreen extends AbstractContainerScreen<PetrificationCoreMenu> {
    private static final ResourceLocation CORE_BACKGROUND =
            ResourceLocation.parse("petrification:textures/gui/container/petrification_core.png");

    // 配色（浅色调：面板 #C6C6C6 上使用亮色文字，避免深色看不清）
    private static final int SLIDER_TRACK = 0xFF555555;
    private static final int SLIDER_KNOB = 0xFFFFFFFF;
    private static final int TEXT_DARK = 0xFFC6C6C6;
    private static final int TEXT_BRIGHT = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFA0A0A0;
    private static final int GREEN = 0xFF55FF55;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int BTN_BASE = 0xFF9B9B9B;
    private static final int BTN_HOVER = 0xFFB4B4B4;
    private static final int BTN_DISABLED = 0xFFD0D0D0;

    private Font font;
    private EditBox searchBox;
    private final List<EntityType<?>> allTypes = new ArrayList<>();
    private final List<EntityType<?>> filtered = new ArrayList<>();
    private int scrollOffset;
    private int dragRadius = -1; // -1 = 未拖动
    @Nullable
    private BlockPos corePos; // 打开瞬间从 hitResult 捕获；菜单不携带方块位置

    // 布局（320x240）
    private static final int COL_LEFT = 8;
    private static final int COL_RIGHT = 164;
    private static final int COL_RIGHT_END = 312;

    // 左栏：燃料 / 状态 / 消耗 / 激活按钮 / 红石控制
    private static final int FUEL_LABEL_Y = 20;
    private static final int STATUS_Y = 58;
    private static final int CONSUMED_Y = 72;
    private static final int BTN_X = COL_LEFT;
    private static final int BTN_Y = 88;
    private static final int BTN_W = 148;
    private static final int BTN_H = 18;
    private static final int REDSTONE_Y = BTN_Y + BTN_H + 4;

    // 右栏：滑块 / 白名单 / 玩家开关
    private static final int SLIDER_LABEL_Y = 20;
    private static final int SLIDER_Y = 35;
    private static final int SLIDER_X = COL_RIGHT;
    private static final int SLIDER_WIDTH = 100;
    private static final int WHITELIST_LABEL_Y = 58;
    private static final int SEARCH_Y = 70;
    private static final int LIST_TOP = 86;
    private static final int LIST_LEFT = COL_RIGHT;
    private static final int LIST_WIDTH = 140;
    private static final int SCROLLBAR_X = 308;
    private static final int SCROLLBAR_H = 54;
    private static final int ROW_HEIGHT = 12;
    private static final int VISIBLE_ROWS = 3;
    private static final int AFFECT_X = COL_RIGHT;
    private static final int AFFECT_Y = 152;

    public PetrificationCoreScreen(PetrificationCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 320;
        this.imageHeight = 240;

        BuiltInRegistries.ENTITY_TYPE.forEach(allTypes::add);
        allTypes.sort(Comparator.comparing(a -> BuiltInRegistries.ENTITY_TYPE.getKey(a).toString()));
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
        if (corePos == null) {
            corePos = detectCorePos();
        }
        this.searchBox = new EditBox(font, leftPos + LIST_LEFT, topPos + SEARCH_Y, LIST_WIDTH, 12,
                Component.translatable("gui.petrification.core.whitelist_search"));
        searchBox.setHint(Component.translatable("gui.petrification.core.whitelist_search"));
        searchBox.setResponder(query -> refreshFiltered(query));
        this.addRenderableWidget(searchBox);
        refreshFiltered("");
    }

    private void refreshFiltered(String query) {
        filtered.clear();
        String q = query.trim().toLowerCase();
        for (EntityType<?> type : allTypes) {
            if (q.isEmpty() || matches(type, q)) {
                filtered.add(type);
            }
        }
        scrollOffset = 0;
    }

    private boolean matches(EntityType<?> type, String q) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id != null && (id.toString().contains(q) || id.getPath().contains(q))) {
            return true;
        }
        return Component.translatable(type.getDescriptionId()).getString().toLowerCase().contains(q);
    }

    // ===== 渲染 =====

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // texW/texH 必须等于纹理实际尺寸（320x240），否则画面被缩放并拉伸边缘
        g.blit(CORE_BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        PetrificationCoreBlockEntity core = coreEntity();

        // ==== 左栏：状态 / 消耗 / 激活按钮 ====
        int statusColor;
        Component status;
        boolean active = core != null && core.isActive() && !core.isAwaitingPayment();
        if (core == null) {
            statusColor = TEXT_DARK;
            status = Component.translatable("gui.petrification.core.status_inactive");
        } else if (core.isAwaitingPayment()) {
            statusColor = YELLOW;
            status = Component.translatable("gui.petrification.core.status_awaiting");
        } else if (active && core.getMaterial() != null) {
            statusColor = GREEN;
            status = Component.translatable("gui.petrification.core.status_active",
                    Component.translatable("material.petrification." + core.getMaterial().getSerializedName()),
                    core.getRadius(), core.getWhitelist().size());
        } else {
            statusColor = TEXT_DARK;
            status = Component.translatable("gui.petrification.core.status_inactive");
        }
        g.drawString(font, status, leftPos + COL_LEFT, topPos + STATUS_Y, statusColor);

        // 消耗显示
        Component consumed = core != null && core.getConsumedCount() > 0 && core.getMaterial() != null
                ? Component.translatable("gui.petrification.core.consumed", core.getConsumedCount(),
                        Component.translatable("material.petrification." + core.getMaterial().getSerializedName()))
                : Component.translatable("gui.petrification.core.consumed_none");
        g.drawString(font, consumed, leftPos + COL_LEFT, topPos + CONSUMED_Y, TEXT_DARK);

        // 激活/关闭激活按钮（Create 风格：灰底 + 白字 + hover 变亮；激活中显示"关闭激活"）
        boolean hovered = mouseX >= leftPos + BTN_X && mouseX < leftPos + BTN_X + BTN_W
                && mouseY >= topPos + BTN_Y && mouseY < topPos + BTN_Y + BTN_H;
        int btnColor = active ? 0xFFB08800 : (hovered ? BTN_HOVER : BTN_BASE);
        g.fill(leftPos + BTN_X, topPos + BTN_Y, leftPos + BTN_X + BTN_W, topPos + BTN_Y + BTN_H, btnColor);
        Component btnLabel = Component.translatable(active
                ? "gui.petrification.core.deactivate" : "gui.petrification.core.activate");
        g.drawString(font, btnLabel,
                leftPos + BTN_X + (BTN_W - font.width(btnLabel)) / 2,
                topPos + BTN_Y + (BTN_H - 8) / 2, TEXT_BRIGHT);

        // 红石控制按钮（开启后仅在收到红石信号时工作）
        boolean redstone = core != null && core.isRedstoneControlled();
        boolean redstoneHovered = mouseX >= leftPos + BTN_X && mouseX < leftPos + BTN_X + BTN_W
                && mouseY >= topPos + REDSTONE_Y && mouseY < topPos + REDSTONE_Y + BTN_H;
        int redstoneColor = redstone ? 0xFF2E7D32 : (redstoneHovered ? BTN_HOVER : BTN_BASE);
        g.fill(leftPos + BTN_X, topPos + REDSTONE_Y, leftPos + BTN_X + BTN_W, topPos + REDSTONE_Y + BTN_H, redstoneColor);
        Component redstoneLabel = Component.translatable(redstone
                ? "gui.petrification.core.redstone_on" : "gui.petrification.core.redstone_off");
        g.drawString(font, redstoneLabel,
                leftPos + BTN_X + (BTN_W - font.width(redstoneLabel)) / 2,
                topPos + REDSTONE_Y + (BTN_H - 8) / 2, TEXT_BRIGHT);

        // ==== 右栏：范围滑块 ====
        int radius = dragRadius >= 0 ? dragRadius : (core != null ? core.getRadius() : 15);
        g.drawString(font, Component.translatable("gui.petrification.core.radius", radius),
                leftPos + SLIDER_X, topPos + SLIDER_LABEL_Y, TEXT_DARK);
        g.fill(leftPos + SLIDER_X, topPos + SLIDER_Y, leftPos + SLIDER_X + SLIDER_WIDTH, topPos + SLIDER_Y + 3, SLIDER_TRACK);
        float t = (radius - PetrificationCoreBlockEntity.MIN_RADIUS)
                / (float) (PetrificationCoreBlockEntity.MAX_RADIUS - PetrificationCoreBlockEntity.MIN_RADIUS);
        int knobX = leftPos + SLIDER_X + (int) (t * (SLIDER_WIDTH - 8));
        g.fill(knobX, topPos + SLIDER_Y - 3, knobX + 8, topPos + SLIDER_Y + 7, SLIDER_KNOB);

        // ==== 右栏：白名单列表（可滚动，scissor 裁剪） ====
        g.drawString(font, Component.translatable("gui.petrification.core.whitelist"), leftPos + LIST_LEFT, topPos + WHITELIST_LABEL_Y, TEXT_DARK);
        g.enableScissor(leftPos + LIST_LEFT, topPos + LIST_TOP - 2, leftPos + LIST_LEFT + LIST_WIDTH, topPos + LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT);
        Set<ResourceLocation> whitelist = core != null ? core.getWhitelist() : Set.of();
        for (int i = 0; i < VISIBLE_ROWS && scrollOffset + i < filtered.size(); i++) {
            EntityType<?> type = filtered.get(scrollOffset + i);
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            boolean selected = id != null && whitelist.contains(id);
            int y = topPos + LIST_TOP + i * ROW_HEIGHT;
            if (mouseY >= y && mouseY < y + ROW_HEIGHT
                    && mouseX >= leftPos + LIST_LEFT && mouseX < leftPos + LIST_LEFT + LIST_WIDTH) {
                g.fill(leftPos + LIST_LEFT, y, leftPos + LIST_LEFT + LIST_WIDTH, y + ROW_HEIGHT, 0x40FFFFFF);
            }
            g.drawString(font, selected ? "✓ " : "○ ", leftPos + LIST_LEFT, y, selected ? GREEN : TEXT_DARK);
            g.drawString(font, Component.translatable(type.getDescriptionId()), leftPos + LIST_LEFT + 10, y, TEXT_BRIGHT);
        }
        g.disableScissor();

        // 滚动条（仅当列表超长）
        int maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
        if (maxScroll > 0) {
            g.fill(leftPos + SCROLLBAR_X, topPos + LIST_TOP, leftPos + SCROLLBAR_X + 4, topPos + LIST_TOP + SCROLLBAR_H, 0x33000000);
            int trackH = SCROLLBAR_H;
            int thumbH = Math.max(8, trackH * VISIBLE_ROWS / filtered.size());
            int thumbY = topPos + LIST_TOP + (trackH - thumbH) * scrollOffset / maxScroll;
            g.fill(leftPos + SCROLLBAR_X, thumbY, leftPos + SCROLLBAR_X + 4, thumbY + thumbH, 0xFF7A7A7A);
        }

        // ==== 右栏：玩家开关 ====
        boolean affectPlayers = core != null && core.isAffectPlayers();
        int cbX = leftPos + AFFECT_X;
        int cbY = topPos + AFFECT_Y;
        drawOutline(g, cbX, cbY, 10, 10, TEXT_DARK);
        if (affectPlayers) {
            g.fill(cbX + 2, cbY + 2, cbX + 8, cbY + 8, GREEN);
        }
        g.drawString(font, Component.translatable("gui.petrification.core.affect_players"), cbX + 14, cbY, TEXT_BRIGHT);
    }

    private static void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    // ===== 交互 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = mouseX;
        double my = mouseY;
        // 激活/关闭激活按钮
        if (mx >= leftPos + BTN_X && mx < leftPos + BTN_X + BTN_W
                && my >= topPos + BTN_Y && my < topPos + BTN_Y + BTN_H) {
            PetrificationCoreBlockEntity core = coreEntity();
            boolean active = core != null && core.isActive() && !core.isAwaitingPayment();
            if (active) {
                ModNetwork.sendToServer(new CoreDeactivatePayload());
            } else {
                ModNetwork.sendToServer(new CoreActivatePayload());
            }
            return true;
        }
        // 红石控制按钮
        if (mx >= leftPos + BTN_X && mx < leftPos + BTN_X + BTN_W
                && my >= topPos + REDSTONE_Y && my < topPos + REDSTONE_Y + BTN_H) {
            PetrificationCoreBlockEntity core = coreEntity();
            if (core != null) {
                ModNetwork.sendToServer(new CoreSetRedstonePayload(!core.isRedstoneControlled()));
            }
            return true;
        }
        // 白名单行点击切换
        if (mx >= leftPos + LIST_LEFT && mx < leftPos + LIST_LEFT + LIST_WIDTH
                && my >= topPos + LIST_TOP && my < topPos + LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT) {
            int row = (int) (my - (topPos + LIST_TOP)) / ROW_HEIGHT;
            int index = scrollOffset + row;
            if (index < filtered.size()) {
                toggleWhitelist(filtered.get(index));
            }
            return true;
        }
        // 玩家开关点击
        if (mx >= leftPos + AFFECT_X && mx < leftPos + AFFECT_X + 10
                && my >= topPos + AFFECT_Y
                && my < topPos + AFFECT_Y + 10) {
            PetrificationCoreBlockEntity core = coreEntity();
            if (core != null) {
                ModNetwork.sendToServer(new CoreSetAffectPlayersPayload(!core.isAffectPlayers()));
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
            ModNetwork.sendToServer(new CoreSetRadiusPayload(dragRadius));
            dragRadius = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX >= leftPos + LIST_LEFT && mouseX < leftPos + LIST_LEFT + LIST_WIDTH
                && mouseY >= topPos + LIST_TOP && mouseY < topPos + LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT) {
            int maxScroll = Math.max(0, filtered.size() - VISIBLE_ROWS);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (deltaY > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private int radiusFromX(double mouseX) {
        float t = (float) (mouseX - (leftPos + SLIDER_X)) / SLIDER_WIDTH;
        t = Math.max(0.0f, Math.min(1.0f, t));
        return Math.round(PetrificationCoreBlockEntity.MIN_RADIUS
                + t * (PetrificationCoreBlockEntity.MAX_RADIUS - PetrificationCoreBlockEntity.MIN_RADIUS));
    }

    private void toggleWhitelist(EntityType<?> type) {
        PetrificationCoreBlockEntity core = coreEntity();
        if (core == null) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) {
            return;
        }
        Set<ResourceLocation> next = new LinkedHashSet<>(core.getWhitelist());
        if (!next.remove(id)) {
            next.add(id);
        }
        ModNetwork.sendToServer(new CoreSetWhitelistPayload(List.copyOf(next)));
    }

    private PetrificationCoreBlockEntity coreEntity() {
        if (minecraft.level == null) {
            return null;
        }
        BlockPos pos = corePos != null ? corePos : menu.getCorePos();
        if (pos == null) {
            return null;
        }
        if (minecraft.level.getBlockEntity(pos) instanceof PetrificationCoreBlockEntity core) {
            return core;
        }
        return null;
    }

    /** 优先用服务端下发的方块位置（GuiPosPayload），其次取打开 GUI 瞬间的瞄准方块。 */
    private @Nullable BlockPos detectCorePos() {
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
