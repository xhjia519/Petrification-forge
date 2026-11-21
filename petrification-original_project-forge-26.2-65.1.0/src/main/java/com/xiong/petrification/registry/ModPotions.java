package com.xiong.petrification.registry;

import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.effect.MaterializationEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModPotions {
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(ForgeRegistries.POTIONS, PetrificationMod.MODID);

    public static final RegistryObject<Potion> STONE = register("stone", ModEffects.STONE_EFFECT);
    public static final RegistryObject<Potion> IRON = register("iron", ModEffects.IRON_EFFECT);
    public static final RegistryObject<Potion> COPPER = register("copper", ModEffects.COPPER_EFFECT);
    public static final RegistryObject<Potion> GOLD = register("gold", ModEffects.GOLD_EFFECT);
    public static final RegistryObject<Potion> DIAMOND = register("diamond", ModEffects.DIAMOND_EFFECT);
    public static final RegistryObject<Potion> NETHERITE = register("netherite", ModEffects.NETHERITE_EFFECT);

    public static final RegistryObject<Potion> RESTORATION = POTIONS.register("restoration",
            () -> new Potion("restoration", new MobEffectInstance(
                    net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getHolder(ModEffects.RESTORATION_EFFECT.get()).orElseThrow(), 1)));

    private static RegistryObject<Potion> register(String name, RegistryObject<MaterializationEffect> effect) {
        return POTIONS.register(name, () -> new Potion(name, new MobEffectInstance(
                net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getHolder(effect.get()).orElseThrow(), 1)));
    }

    private ModPotions() {}

    public static void register(BusGroup modBusGroup) {
        POTIONS.register(modBusGroup);
    }
}
