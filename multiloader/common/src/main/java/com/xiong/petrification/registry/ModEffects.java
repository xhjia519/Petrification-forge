package com.xiong.petrification.registry;

import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.effect.MaterializationEffect;
import com.xiong.petrification.effect.RestorationEffect;
import com.xiong.petrification.platform.Platform;
import com.xiong.petrification.platform.Registrar;
import com.xiong.petrification.platform.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class ModEffects {
    public static final Registrar<MobEffect> EFFECTS = Platform.registrar(Registries.MOB_EFFECT);

    public static final RegistryEntry<MaterializationEffect> STONE_EFFECT = register("stone", MaterialType.STONE);
    public static final RegistryEntry<MaterializationEffect> IRON_EFFECT = register("iron", MaterialType.IRON);
    public static final RegistryEntry<MaterializationEffect> COPPER_EFFECT = register("copper", MaterialType.COPPER);
    public static final RegistryEntry<MaterializationEffect> GOLD_EFFECT = register("gold", MaterialType.GOLD);
    public static final RegistryEntry<MaterializationEffect> DIAMOND_EFFECT = register("diamond", MaterialType.DIAMOND);
    public static final RegistryEntry<MaterializationEffect> NETHERITE_EFFECT = register("netherite", MaterialType.NETHERITE);

    public static final RegistryEntry<RestorationEffect> RESTORATION_EFFECT = EFFECTS.register("restoration",
            () -> new RestorationEffect(MobEffectCategory.BENEFICIAL, 0x55FF55));

    private static RegistryEntry<MaterializationEffect> register(String name, MaterialType materialType) {
        return EFFECTS.register(name + "_materialization", () ->
                new MaterializationEffect(MobEffectCategory.NEUTRAL, materialType.getColor(), materialType));
    }

    private ModEffects() {}

    public static void register() {
        EFFECTS.bind();
    }
}
