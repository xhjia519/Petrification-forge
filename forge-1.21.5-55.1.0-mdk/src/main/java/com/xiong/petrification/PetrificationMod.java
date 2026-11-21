package com.xiong.petrification;

import com.mojang.logging.LogUtils;
import com.xiong.petrification.handler.ModEventHandlers;
import com.xiong.petrification.handler.ModRecipeHandler;
import com.xiong.petrification.network.ModNetwork;
import com.xiong.petrification.registry.ModBlockEntities;
import com.xiong.petrification.registry.ModBlocks;
import com.xiong.petrification.registry.ModCreativeTabs;
import com.xiong.petrification.registry.ModDataComponents;
import com.xiong.petrification.registry.ModEffects;
import com.xiong.petrification.registry.ModItems;
import com.xiong.petrification.registry.ModMenus;
import com.xiong.petrification.registry.ModPotions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(PetrificationMod.MODID)
public final class PetrificationMod {
    public static final String MODID = "petrification";
    private static final Logger LOGGER = LogUtils.getLogger();

    public PetrificationMod(FMLJavaModLoadingContext context) {
        var modBus = context.getModEventBus();

        ModDataComponents.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModEffects.register(modBus);
        ModPotions.register(modBus);
        ModCreativeTabs.register(modBus);

        ModRecipeHandler.register(modBus);
        ModRecipeHandler.registerBrewing();

        modBus.addListener(this::commonSetup);
        modBus.addListener(ModCreativeTabs::addCreative);

        context.registerConfig(ModConfig.Type.COMMON, ModConfigSpecHolder.SPEC);

        ModEventHandlers.register();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.xiong.petrification.client.ClientSetup.register(modBus);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
        LOGGER.info("Petrification mod initialized");
    }
}
