package com.xiong.petrification.client;

import com.xiong.petrification.registry.ModBlockEntities;
import com.xiong.petrification.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientSetup {
    private ClientSetup() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientSetup::registerRenderers);
        modBus.addListener(ClientSetup::registerBlockColors);
        // GUI 注册需在注册表填充后执行（mod 构造时 MenuType 尚不可用）
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.xiong.petrification.registry.ModMenus.PETRIFICATION_CORE.get(),
                    PetrificationCoreScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.xiong.petrification.registry.ModMenus.RESTORATION.get(),
                    RestorationScreen::new);
        }));
    }

    /**
     * 保留调用点兼容：1.21.5 打开全屏 GUI 时原版本身就不渲染 HUD 快捷栏
     * （"GUI 打开仍渲染 HUD"是 1.21.9+/26.x 的新行为），无需层条件 hack。
     */
    public static void ensureHudHotbarHiddenOnCoreGui() {
        // no-op on 1.21.5
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
