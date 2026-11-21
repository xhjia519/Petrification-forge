package com.xiong.petrification.client;

import com.xiong.petrification.registry.ModBlockEntities;
import com.xiong.petrification.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class ClientSetup {
    private ClientSetup() {}

    /** 懒初始化只执行一次（幂等），首次由 Screen.init 在渲染线程触发。 */
    private static boolean hudHotbarHidingAttempted;

    public static void register(BusGroup modBusGroup) {
        EntityRenderersEvent.RegisterRenderers.getBus(modBusGroup).addListener(ClientSetup::registerRenderers);
        RegisterColorHandlersEvent.Block.getBus(modBusGroup).addListener(ClientSetup::registerBlockColors);
        registerSpecialItemRenderer();
        // GUI 注册需在注册表填充后执行（mod 构造时 MenuType 尚不可用）
        FMLClientSetupEvent.getBus(modBusGroup).addListener(event -> event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.xiong.petrification.registry.ModMenus.PETRIFICATION_CORE.get(),
                    PetrificationCoreScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.xiong.petrification.registry.ModMenus.RESTORATION.get(),
                    RestorationScreen::new);
        }));
    }

    /**
     * 隐藏 HUD 快捷栏（打开石化核心/还原 GUI 时）。必须由 Screen.init 触发：
     * ForgeLayeredDraw.init 在 Gui 构造时才填充子栈（subLayerStacks），
     * FMLClientSetupEvent 时机太早，getChild 会返回 null 导致静默失败。
     */
    public static void ensureHudHotbarHiddenOnCoreGui() {
        if (hudHotbarHidingAttempted) {
            return;
        }
        hudHotbarHidingAttempted = true;
        hideHudHotbarOnCoreGui();
    }

    /**
     * 26.2 打开 GUI 时 HUD 快捷栏仍会渲染：Hud.extractRenderState 走 ForgeLayeredDraw 层系统，
     * 层提取不检查 screen，也不读 GuiRenderState.isHudHidden（该字段只控制手持/准星/屏幕效果），
     * 全屏 GUI 底部的快捷栏与 HUD 快捷栏重叠成"重影"。
     *
     * 反编译确认的烘焙机制：ForgeLayeredDraw.init 结尾只对根调用 resolveLayers()，其中
     * resolveNested() 遍历 order 烘焙、随后 order.clear()——根 order 被清空，而 HOTBAR_AND_DECOS
     * 等子栈的 order 完整保留（其 resolveNested 由根递归触发，不清 order）；bakedLayers 只追加
     * 不清空。因此：对根 addConditionTo 后再 resolveLayers 时根 order 为空会空转，条件层永远
     * 进不了烘焙结果（上一版修复无效的原因）。正确做法：对子栈加条件 → 反射清空子栈
     * bakedLayers → 对子栈 resolveLayers() 重新烘焙（其 order 保留完整层列表）。层条件在每帧
     * 提取时实时求值，关闭 GUI 后自动恢复。
     */
    private static void hideHudHotbarOnCoreGui() {
        try {
            Field instanceField = ForgeLayeredDraw.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            ForgeLayeredDraw root = (ForgeLayeredDraw) instanceField.get(null);
            if (root == null) {
                return;
            }
            BooleanSupplier hideOnCoreGui =
                    () -> !(Minecraft.getInstance().screen instanceof PetrificationCoreScreen
                            || Minecraft.getInstance().screen instanceof RestorationScreen);
            // 1.21.10 的 ForgeLayeredDraw 尚无 ITEM_HOTBAR/SPECTATOR_HOTBAR 子层常量，
            // 直接对根层的 HOTBAR_AND_DECOS 添加条件即可隐藏快捷栏与装饰（打开核心/还原 GUI 时）。
            root.addConditionTo(ForgeLayeredDraw.HOTBAR_AND_DECOS, hideOnCoreGui);
            ForgeLayeredDraw hotbarStack = root.getChild(ForgeLayeredDraw.HOTBAR_AND_DECOS);
            if (hotbarStack != null) {
                Field bakedField = ForgeLayeredDraw.class.getDeclaredField("bakedLayers");
                bakedField.setAccessible(true);
                ((List<?>) bakedField.get(hotbarStack)).clear();
                hotbarStack.resolveLayers();
            }
            com.mojang.logging.LogUtils.getLogger().info(
                    "[Core] HUD hotbar layers rebuilt with hide-on-core-gui condition");
        } catch (Throwable e) {
            com.mojang.logging.LogUtils.getLogger().warn("[Core] Failed to hide HUD hotbar while core GUI open", e);
        }
    }

    /**
     * Registers the statue item renderer into the private SpecialModelRenderers registry so the
     * statue item renders as the mob model in hand / GUI. Runs on the client before model baking.
     */
    private static void registerSpecialItemRenderer() {
        try {
            Class<?> registryClass = Class.forName("net.minecraft.client.renderer.special.SpecialModelRenderers");
            java.lang.reflect.Field field = registryClass.getDeclaredField("ID_MAPPER");
            field.setAccessible(true);
            Object mapper = field.get(null);
            java.lang.reflect.Method put = null;
            for (java.lang.reflect.Method m : mapper.getClass().getMethods()) {
                if (m.getName().equals("put") && m.getParameterCount() == 2) {
                    put = m;
                    break;
                }
            }
            if (put != null) {
                put.setAccessible(true);
                put.invoke(mapper,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("petrification", "statue"),
                        StatueSpecialRenderer.Unbaked.MAP_CODEC);
            }
        } catch (Throwable e) {
            com.mojang.logging.LogUtils.getLogger().error("[Statue] Failed to register special item renderer", e);
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.STATUE_BLOCK_ENTITY.get(), StatueBlockEntityRenderer::new);
        // 微缩建筑方块渲染器：以微缩比例渲染压缩前的建筑原貌
        event.registerBlockEntityRenderer(ModBlockEntities.MINI_STRUCTURE_BLOCK_ENTITY.get(), MiniStructureRenderer::new);
        // 石化核心光波为服务端粒子效果（见 PetrificationCoreBlockEntity.tickPulse），无需方块实体渲染器
        // 26.1.2 尚无原版硫怪(SulfurCube)，故不注册其渲染器覆盖
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        try {
            event.register(new StatueBlockTintSource(), ModBlocks.STATUE_BLOCK.get());
        } catch (Throwable e) {
            // BlockColors.createDefault 在 Minecraft 构造早期（注册表填充完成前）就触发本事件，
            // 偶发 "Registry Object not present: petrification:statue_block"，跳过 tint 注册避免崩溃。
            com.mojang.logging.LogUtils.getLogger().warn("[Statue] Failed to register block tint source, skipping", e);
        }
    }
}
