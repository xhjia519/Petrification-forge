package com.xiong.petrification.client;

import com.xiong.petrification.registry.ModBlockEntities;
import com.xiong.petrification.registry.ModBlocks;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;

import java.util.List;

public final class ClientSetup {
    private ClientSetup() {}

    public static void register() {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(ClientSetup::registerRenderers);
        RegisterColorHandlersEvent.Block.BUS.addListener(ClientSetup::registerBlockColors);
        registerSpecialItemRenderer();
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
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("petrification", "statue"),
                        StatueSpecialRenderer.Unbaked.MAP_CODEC);
            }
        } catch (Throwable e) {
            com.mojang.logging.LogUtils.getLogger().error("[Statue] Failed to register special item renderer", e);
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.STATUE_BLOCK_ENTITY.get(), StatueBlockEntityRenderer::new);
        // Override the vanilla sulfur cube renderer so swallowed statues show the mob model
        net.minecraft.client.renderer.entity.EntityRenderers.register(
                net.minecraft.world.entity.EntityTypes.SULFUR_CUBE, StatueSulfurCubeRenderer::new);
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                List.of(new StatueBlockTintSource()),
                ModBlocks.STATUE_BLOCK.get()
        );
    }
}
