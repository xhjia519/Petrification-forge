package com.xiong.petrification;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = PetrificationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModConfigSpecHolder {
    /** 方块被活塞推动时的行为：MOVE 移动（仅可推不可拉）、DROP 推即掉落、BLOCK 推不动。 */
    public enum PistonBehavior { DROP, MOVE, BLOCK }

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

    private static final ForgeConfigSpec.EnumValue<PistonBehavior> PISTON_BEHAVIOR = BUILDER
            .comment("Behavior of mod blocks when pushed by a piston: MOVE (keeps data, push only), DROP (drops as items), BLOCK (cannot be moved)")
            .defineEnum("general.pistonBehavior", PistonBehavior.MOVE);

    // ===== 建筑压缩模块配置 =====

    private static final ForgeConfigSpec.IntValue STRUCTURE_MAX_SCAN_SIZE = BUILDER
            .comment("Maximum scan radius when detecting a building")
            .defineInRange("structure.maxScanSize", 64, 8, 128);

    private static final ForgeConfigSpec.IntValue STRUCTURE_MIN_BLOCKS = BUILDER
            .comment("Minimum block count required to compress a building")
            .defineInRange("structure.minBlocks", 20, 1, 10000);

    private static final ForgeConfigSpec.IntValue STRUCTURE_CONFIRMATION_TIMEOUT = BUILDER
            .comment("Confirmation timeout in seconds after structure identification")
            .defineInRange("structure.confirmationTimeout", 5, 1, 60);

    private static final ForgeConfigSpec.IntValue STRUCTURE_RESTORE_BATCH_SIZE = BUILDER
            .comment("Blocks restored per tick during structure restoration")
            .defineInRange("structure.restoreBatchSize", 100, 1, 5000);

    private static final ForgeConfigSpec.IntValue STRUCTURE_MAX_TRACKED = BUILDER
            .comment("Maximum number of restored structures tracked")
            .defineInRange("structure.maxTracked", 50, 1, 500);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double potionRangeMultiplier = 1.0;
    public static boolean allowCreativeMaterialization = false;
    public static boolean allowOpMaterialization = false;
    public static double statueBlockHardness = 2.0;
    public static double oxidationRate = 0.001;
    public static double maxOxidation = 1.0;
    public static boolean enableOxidation = true;
    public static int statueRenderDistance = 64;
    public static PistonBehavior pistonBehavior = PistonBehavior.MOVE;
    public static int structureMaxScanSize = 64;
    public static int structureMinBlocks = 20;
    public static int structureConfirmationTimeout = 5;
    public static int structureRestoreBatchSize = 100;
    public static int structureMaxTracked = 50;

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
        pistonBehavior = PISTON_BEHAVIOR.get();
        structureMaxScanSize = STRUCTURE_MAX_SCAN_SIZE.get();
        structureMinBlocks = STRUCTURE_MIN_BLOCKS.get();
        structureConfirmationTimeout = STRUCTURE_CONFIRMATION_TIMEOUT.get();
        structureRestoreBatchSize = STRUCTURE_RESTORE_BATCH_SIZE.get();
        structureMaxTracked = STRUCTURE_MAX_TRACKED.get();
    }
}
