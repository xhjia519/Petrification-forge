package com.xiong.petrification.registry;

import com.xiong.petrification.platform.Platform;
import com.xiong.petrification.platform.Registrar;
import com.xiong.petrification.platform.RegistryEntry;
import com.xiong.petrification.effect.MaterializationEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

public final class ModPotions {
    public static final Registrar<Potion> POTIONS = Platform.registrar(Registries.POTION);

    public static final RegistryEntry<Potion> STONE = register("stone", ModEffects.STONE_EFFECT);
    public static final RegistryEntry<Potion> IRON = register("iron", ModEffects.IRON_EFFECT);
    public static final RegistryEntry<Potion> COPPER = register("copper", ModEffects.COPPER_EFFECT);
    public static final RegistryEntry<Potion> GOLD = register("gold", ModEffects.GOLD_EFFECT);
    public static final RegistryEntry<Potion> DIAMOND = register("diamond", ModEffects.DIAMOND_EFFECT);
    public static final RegistryEntry<Potion> NETHERITE = register("netherite", ModEffects.NETHERITE_EFFECT);

    public static final RegistryEntry<Potion> RESTORATION = POTIONS.register("restoration",
            () -> new Potion("restoration", new MobEffectInstance(
                    asEffectHolder(ModEffects.RESTORATION_EFFECT), 1)));

    private static RegistryEntry<Potion> register(String name, RegistryEntry<MaterializationEffect> effect) {
        return POTIONS.register(name, () -> new Potion(name, new MobEffectInstance(asEffectHolder(effect), 1)));
    }

    /** 注册项持有的是具体效果类型，MobEffectInstance 需要 Holder&lt;MobEffect&gt;，此处做一次安全的上转型。 */
    @SuppressWarnings("unchecked")
    private static Holder<MobEffect> asEffectHolder(RegistryEntry<? extends MobEffect> entry) {
        return (Holder<MobEffect>) (Holder<?>) entry.holder();
    }

    private ModPotions() {}

    public static void register() {
        POTIONS.bind();
    }
}
