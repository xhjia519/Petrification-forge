package com.xiong.petrification.client;

import com.xiong.petrification.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 客户端初始化中与加载器无关的部分。
 * <p>
 * 渲染器 / 菜单界面 / 事件监听由各加载器模块注册；这里只保留纯反射的公共逻辑，
 * 避免公共模块依赖任何加载器的客户端类。
 */
public final class ClientSetup {
    private ClientSetup() {}

    /** 懒初始化只执行一次（幂等），首次由 Screen.init 在渲染线程触发。 */
    private static boolean hudHotbarHidingAttempted;

    /** 加载器在客户端初始化时注入自己的 HUD 层实现类（Forge / NeoForge 包名不同）。 */
    private static Class<?> layeredDrawClass;

    public static void installHudLayerClass(Class<?> hudLayerClass) {
        layeredDrawClass = hudLayerClass;
    }

    /**
     * 隐藏 HUD 快捷栏（打开石化核心/还原 GUI 时）。
     * <p>
     * 26.2 的 HUD 层系统由加载器提供（Forge 为 ForgeLayeredDraw，NeoForge 为其对应实现），
     * 两个类的成员名称一致但包名不同，因此这里以整体反射的方式处理：加载器只需注入自己的类。
     * 必须由 Screen.init 触发：层子栈在 Gui 构造时才填充，模组初始化阶段 getChild 会返回 null。
     */
    public static void ensureHudHotbarHiddenOnCoreGui() {
        if (hudHotbarHidingAttempted || layeredDrawClass == null) {
            return;
        }
        hudHotbarHidingAttempted = true;
        hideHudHotbarOnCoreGui(layeredDrawClass);
    }

    private static void hideHudHotbarOnCoreGui(Class<?> layeredDrawClass) {
        try {
            Field instanceField = layeredDrawClass.getDeclaredField("instance");
            instanceField.setAccessible(true);
            Object root = instanceField.get(null);
            if (root == null) {
                return;
            }
            BooleanSupplier hideOnCoreGui = () ->
                    !(Minecraft.getInstance().gui.screen() instanceof PetrificationCoreScreen
                            || Minecraft.getInstance().gui.screen() instanceof RestorationScreen);

            Object hotbarStackKey = staticField(layeredDrawClass, "HOTBAR_AND_DECOS");
            Object itemHotbarKey = staticField(layeredDrawClass, "ITEM_HOTBAR");
            Object spectatorHotbarKey = staticField(layeredDrawClass, "SPECTATOR_HOTBAR");
            if (hotbarStackKey == null || itemHotbarKey == null || spectatorHotbarKey == null) {
                return;
            }

            Object hotbarStack = invoke(root, "getChild", new Class<?>[]{Object.class}, hotbarStackKey);
            if (hotbarStack == null) {
                return;
            }
            invoke(hotbarStack, "addConditionTo", new Class<?>[]{Object.class, BooleanSupplier.class},
                    itemHotbarKey, hideOnCoreGui);
            invoke(hotbarStack, "addConditionTo", new Class<?>[]{Object.class, BooleanSupplier.class},
                    spectatorHotbarKey, hideOnCoreGui);

            Field bakedField = layeredDrawClass.getDeclaredField("bakedLayers");
            bakedField.setAccessible(true);
            Object baked = bakedField.get(hotbarStack);
            if (baked instanceof List<?> list) {
                list.clear();
            }
            invoke(hotbarStack, "resolveLayers", new Class<?>[0]);
            com.mojang.logging.LogUtils.getLogger().info(
                    "[Core] HUD hotbar layers rebuilt with hide-on-core-gui condition");
        } catch (Throwable e) {
            com.mojang.logging.LogUtils.getLogger().warn("[Core] Failed to hide HUD hotbar while core GUI open", e);
        }
    }

    private static Object staticField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable e) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Throwable e) {
            // 尝试在父类中查找（部分实现把这些方法定义在基类上）
            for (Class<?> c = target.getClass().getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Method method = c.getDeclaredMethod(methodName, parameterTypes);
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (Throwable ignored) {
                }
            }
            return null;
        }
    }

    /**
     * Registers the statue item renderer into the private SpecialModelRenderers registry so the
     * statue item renders as the mob model in hand / GUI. Runs on the client before model baking.
     */
    public static void registerSpecialItemRenderer() {
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
                        Identifier.fromNamespaceAndPath("petrification", "statue"),
                        StatueSpecialRenderer.Unbaked.MAP_CODEC);
            }
        } catch (Throwable e) {
            com.mojang.logging.LogUtils.getLogger().error("[Statue] Failed to register special item renderer", e);
        }
    }

    /** 雕像方块的颜色源列表（加载器在方块颜色注册事件中调用）。 */
    public static List<StatueBlockTintSource> statueBlockTintSources() {
        return List.of(new StatueBlockTintSource());
    }

    /** 需要注册颜色源的方块。 */
    public static Block statueBlockForTint() {
        return ModBlocks.STATUE_BLOCK.get();
    }
}
