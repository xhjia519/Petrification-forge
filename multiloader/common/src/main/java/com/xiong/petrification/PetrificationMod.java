package com.xiong.petrification;

import com.mojang.logging.LogUtils;
import com.xiong.petrification.handler.ModRecipeHandler;
import com.xiong.petrification.platform.Platform;
import com.xiong.petrification.registry.ModBlockEntities;
import com.xiong.petrification.registry.ModBlocks;
import com.xiong.petrification.registry.ModCreativeTabs;
import com.xiong.petrification.registry.ModDataComponents;
import com.xiong.petrification.registry.ModEffects;
import com.xiong.petrification.registry.ModItems;
import com.xiong.petrification.registry.ModMenus;
import com.xiong.petrification.registry.ModPotions;
import org.slf4j.Logger;

/**
 * 多加载器架构的公共入口（不含任何加载器注解）。
 * <p>
 * 各加载器模块的模组入口在安装好自己的 {@link com.xiong.petrification.platform.ModPlatform}
 * 实现后调用 {@link #bootstrap()}：所有注册器在此一次性绑定，具体注册时机由加载器决定
 * （Forge/NeoForge 的 DeferredRegister 要求在模组构造阶段注册）。
 */
public final class PetrificationMod {
    public static final String MODID = "petrification";
    private static final Logger LOGGER = LogUtils.getLogger();

    private PetrificationMod() {}

    /** 绑定公共模块的全部注册表内容，必须在 {@link Platform#install} 之后调用。 */
    public static void bootstrap() {
        ModDataComponents.register();
        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        ModMenus.register();
        ModEffects.register();
        ModPotions.register();
        ModCreativeTabs.register();
        ModRecipeHandler.register();
        LOGGER.info("Petrification registries bound on platform {}", Platform.name());
    }
}
