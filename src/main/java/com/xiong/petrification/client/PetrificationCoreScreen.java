package com.xiong.petrification.client;

import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import com.xiong.petrification.menu.PetrificationCoreMenu;
import com.xiong.petrification.network.CoreSetAffectPlayersPayload;
import com.xiong.petrification.network.CoreSetRadiusPayload;
import com.xiong.petrification.network.CoreSetWhitelistPayload;
import com.xiong.petrification.network.ModNetwork;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 石化核心 GUI（Create 风格：深色面板 + 宽松布局 + 自绘滑块）：
 * 第一排燃料槽 / 第二排半径滑块 / 第三排状态行 / 第四排白名单搜索与勾选列表 / 玩家开关。
 * 26.2 提取式渲染：extractContents 内自绘，槽位物品由父类负责。
 */
public class PetrificationCoreScreen extends AbstractContainerScreen<PetrificationCoreMenu> {
    private static final int PANEL_COLOR = 0xC0101010;
    private static final int PANEL_BORDER = 0xFF2B2B2B;
    private static final int SLIDER_TRACK = 0xFF3A3A3A;
    private static final int SLIDER_KNOB = 0xFFFFD83D;
    private static final int TEXT_DARK = 0xFF9E9E9E;
    private static final int TEXT_BRIGHT = 0xFFFFFFFF;
    private static final int GREEN = 0xFF55FF55;
    private static final int YELLOW = 0xFFFFFF55;

    private final Font font;
    private EditBox searchBox;
    private final List<EntityType<?>> allTypes = new ArrayList<>();
    private final List<EntityType<?>> filtered = new ArrayList<>();
    private int scrollOffset;
    private int dragRadius = -1; // -1 = 未拖动

    private static final int ROW_HEIGHT = 12;
    private static final int VISIBLE_ROWS = 6;
    private static final int LIST_TOP = 118;
    private static final int LIST_LEFT = 24;
    private static final int LIST_WIDTH = 162;
    private static final int SLIDER_Y = 78;
    private static final int SLIDER_X = 24;
    private static final int SLIDER_WIDTH = 130;

    public PetrificationCoreScreen(PetrificationCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 210, 236);
        this.font = this.minecraft.font;
        BuiltInRegistries.ENTITY_TYPE.forEach(allTypes::add);
        allTypes.sort(Comparator.comparing(a -> BuiltInRegistries.ENTITY_TYPE.getKey(a).toString()));
    }

    @Override
    protected void init() {
        super.init();
        this.searchBox = new EditBox(font, leftPos + LIST_LEFT, topPos + 104, LIST_WIDTH, 12,
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
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id != null && (id.toString().contains(q) || id.getPath().contains(q))) {
            return true;
        }
        return Component.translatable(type.getDescriptionId()).getString().toLowerCase().contains(q);
    }

    // ===== 渲染（26.2 提取式） =====

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 面板背景（先画，父类槽位/物品叠在其上）
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_COLOR);
        g.outline(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_BORDER);
        super.extractContents(g, mouseX, mouseY, partialTick);

        PetrificationCoreBlockEntity core = coreEntity();
        int cx = leftPos + imageWidth / 2;
        g.centeredText(font, Component.translatable("block.petrification.petrification_core"), cx, topPos + 8, TEXT_BRIGHT);

        // 燃料槽标签
        g.centeredText(font, Component.translatable("gui.petrification.core.fuel"), cx, topPos + 12, TEXT_DARK);

        // 半径滑块（自绘，Create 风格轨道 + 黄色滑块头 + 刻度）
        int radius = dragRadius >= 0 ? dragRadius : (core != null ? core.getRadius() : 15);
        g.text(font, Component.translatable("gui.petrification.core.radius", radius),
                leftPos + SLIDER_X, topPos + SLIDER_Y - 10, TEXT_DARK);
        g.fill(leftPos + SLIDER_X, topPos + SLIDER_Y, leftPos + SLIDER_X + SLIDER_WIDTH, topPos + SLIDER_Y + 3, SLIDER_TRACK);
        for (int i = 0; i <= 4; i++) {
            int tx = leftPos + SLIDER_X + SLIDER_WIDTH * i / 4;
            g.fill(tx, topPos + SLIDER_Y + 4, tx + 1, topPos + SLIDER_Y + 6, SLIDER_TRACK);
        }
        float t = (radius - PetrificationCoreBlockEntity.MIN_RADIUS)
                / (float) (PetrificationCoreBlockEntity.MAX_RADIUS - PetrificationCoreBlockEntity.MIN_RADIUS);
        int knobX = leftPos + SLIDER_X + (int) (t * (SLIDER_WIDTH - 6)) + 2;
        g.fill(knobX, topPos + SLIDER_Y - 2, knobX + 6, topPos + SLIDER_Y + 5, SLIDER_KNOB);

        // 状态行
        int statusColor;
        Component status;
        if (core == null) {
            statusColor = TEXT_DARK;
            status = Component.translatable("gui.petrification.core.status_inactive");
        } else if (core.isAwaitingPayment()) {
            statusColor = YELLOW;
            status = Component.translatable("gui.petrification.core.status_awaiting");
        } else if (core.isActive() && core.getMaterial() != null) {
            statusColor = GREEN;
            status = Component.translatable("gui.petrification.core.status_active",
                    Component.translatable("material.petrification." + core.getMaterial().getSerializedName()),
                    core.getRadius(), core.getWhitelist().size());
        } else {
            statusColor = TEXT_DARK;
            status = Component.translatable("gui.petrification.core.status_inactive");
        }
        g.centeredText(font, status, cx, topPos + 92, statusColor);

        // 白名单列表（可滚动，scissor 裁剪到列表区域）
        g.text(font, Component.translatable("gui.petrification.core.whitelist"), leftPos + LIST_LEFT, topPos + 96, TEXT_DARK);
        g.enableScissor(leftPos + LIST_LEFT, topPos + LIST_TOP - 2, leftPos + LIST_LEFT + LIST_WIDTH, topPos + LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT);
        Set<Identifier> whitelist = core != null ? core.getWhitelist() : Set.of();
        for (int i = 0; i < VISIBLE_ROWS && scrollOffset + i < filtered.size(); i++) {
            EntityType<?> type = filtered.get(scrollOffset + i);
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            boolean selected = id != null && whitelist.contains(id);
            int y = topPos + LIST_TOP + i * ROW_HEIGHT;
            if (mouseY >= y && mouseY < y + ROW_HEIGHT
                    && mouseX >= leftPos + LIST_LEFT && mouseX < leftPos + LIST_LEFT + LIST_WIDTH) {
                g.fill(leftPos + LIST_LEFT, y, leftPos + LIST_LEFT + LIST_WIDTH, y + ROW_HEIGHT, 0x30303030);
            }
            g.text(font, selected ? "✓ " : "○ ", leftPos + LIST_LEFT, y, selected ? GREEN : TEXT_DARK);
            g.text(font, Component.translatable(type.getDescriptionId()), leftPos + LIST_LEFT + 10, y, TEXT_BRIGHT);
        }
        g.disableScissor();

        // 玩家开关
        boolean affectPlayers = core != null && core.isAffectPlayers();
        int cbX = leftPos + LIST_LEFT;
        int cbY = topPos + LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT + 6;
        g.outline(cbX, cbY, cbX + 10, cbY + 10, TEXT_BRIGHT);
        if (affectPlayers) {
            g.fill(cbX + 2, cbY + 2, cbX + 8, cbY + 8, GREEN);
        }
        g.text(font, Component.translatable("gui.petrification.core.affect_players"), cbX + 14, cbY, TEXT_BRIGHT);
    }

    // ===== 交互 =====

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mx = event.x();
        double my = event.y();
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
        if (mx >= leftPos + LIST_LEFT && mx < leftPos + LIST_LEFT + 10
                && my >= topPos + LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT + 6
                && my < topPos + LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT + 16) {
            PetrificationCoreBlockEntity core = coreEntity();
            if (core != null) {
                ModNetwork.sendToServer(new CoreSetAffectPlayersPayload(!core.isAffectPlayers()));
            }
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && event.x() >= leftPos + SLIDER_X && event.x() <= leftPos + SLIDER_X + SLIDER_WIDTH
                && event.y() >= topPos + SLIDER_Y - 4 && event.y() <= topPos + SLIDER_Y + 8) {
            dragRadius = radiusFromX(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragRadius >= 0 && event.button() == 0) {
            ModNetwork.sendToServer(new CoreSetRadiusPayload(dragRadius));
            dragRadius = -1;
            return true;
        }
        return super.mouseReleased(event);
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
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) {
            return;
        }
        Set<Identifier> next = new LinkedHashSet<>(core.getWhitelist());
        if (!next.remove(id)) {
            next.add(id);
        }
        ModNetwork.sendToServer(new CoreSetWhitelistPayload(List.copyOf(next)));
    }

    private PetrificationCoreBlockEntity coreEntity() {
        if (minecraft.level == null) {
            return null;
        }
        if (minecraft.level.getBlockEntity(menu.getCorePos()) instanceof PetrificationCoreBlockEntity core) {
            return core;
        }
        return null;
    }
}
