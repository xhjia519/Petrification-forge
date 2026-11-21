package com.xiong.petrification.registry;

import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.effect.MaterializationEffect;
import com.xiong.petrification.effect.RestorationEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, PetrificationMod.MODID);

    public static final RegistryObject<MaterializationEffect> STONE_EFFECT = register("stone", MaterialType.STONE);
    public static final RegistryObject<MaterializationEffect> IRON_EFFECT = register("iron", MaterialType.IRON);
    public static final RegistryObject<MaterializationEffect> COPPER_EFFECT = register("copper", MaterialType.COPPER);
    public static final RegistryObject<MaterializationEffect> GOLD_EFFECT = register("gold", MaterialType.GOLD);
    public static final RegistryObject<MaterializationEffect> DIAMOND_EFFECT = register("diamond", MaterialType.DIAMOND);
    public static final RegistryObject<MaterializationEffect> NETHERITE_EFFECT = register("netherite", MaterialType.NETHERITE);

    public static final RegistryObject<RestorationEffect> RESTORATION_EFFECT = EFFECTS.register("restoration",
            () -> new RestorationEffect(MobEffectCategory.BENEFICIAL, 0x55FF55));

    private static RegistryObject<MaterializationEffect> register(String name, MaterialType materialType) {
        return EFFECTS.register(name + "_materialization", () ->
                new MaterializationEffect(MobEffectCategory.NEUTRAL, materialType.getColor(), materialType));
    }

    private ModEffects() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
