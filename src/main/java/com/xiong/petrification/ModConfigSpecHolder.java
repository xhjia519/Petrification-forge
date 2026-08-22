package com.xiong.petrification;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = PetrificationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModConfigSpecHolder {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.DoubleValue POTION_RANGE_MULTIPLIER = BUILDER
            .comment("Potion effect range multiplier (1.0 = default)")
            .defineInRange("general.potionRangeMultiplier", 1.0, 0.1, 10.0);

    private static final ForgeConfigSpec.BooleanValue ALLOW_CREATIVE = BUILDER
            .comment("Allow materializing creative mode players")
            .define("general.allowCreativeMaterialization", false);

    private static final ForgeConfigSpec.BooleanValue ALLOW_OP = BUILDER
            .comment("Allow materializing OP players")
            .define("general.allowOpMaterialization", false);

    private static final ForgeConfigSpec.DoubleValue STATUE_HARDNESS = BUILDER
            .comment("Statue block hardness")
            .defineInRange("general.statueBlockHardness", 2.0, 0.1, 100.0);

    private static final ForgeConfigSpec.DoubleValue OXIDATION_RATE = BUILDER
            .comment("Copper oxidation increment per 200 ticks")
            .defineInRange("oxidation.oxidationRate", 0.001, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue MAX_OXIDATION = BUILDER
            .comment("Maximum oxidation value")
            .defineInRange("oxidation.maxOxidation", 1.0, 0.0, 1.0);

    private static final ForgeConfigSpec.BooleanValue ENABLE_OXIDATION = BUILDER
            .comment("Enable copper oxidation")
            .define("oxidation.enableOxidation", true);

    private static final ForgeConfigSpec.IntValue STATUE_RENDER_DISTANCE = BUILDER
            .comment("Statue render distance in blocks")
            .defineInRange("performance.statueRenderDistance", 64, 16, 256);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double potionRangeMultiplier = 1.0;
    public static boolean allowCreativeMaterialization = false;
    public static boolean allowOpMaterialization = false;
    public static double statueBlockHardness = 2.0;
    public static double oxidationRate = 0.001;
    public static double maxOxidation = 1.0;
    public static boolean enableOxidation = true;
    public static int statueRenderDistance = 64;

    private ModConfigSpecHolder() {}

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        potionRangeMultiplier = POTION_RANGE_MULTIPLIER.get();
        allowCreativeMaterialization = ALLOW_CREATIVE.get();
        allowOpMaterialization = ALLOW_OP.get();
        statueBlockHardness = STATUE_HARDNESS.get();
        oxidationRate = OXIDATION_RATE.get();
        maxOxidation = MAX_OXIDATION.get();
        enableOxidation = ENABLE_OXIDATION.get();
        statueRenderDistance = STATUE_RENDER_DISTANCE.get();
    }
}
