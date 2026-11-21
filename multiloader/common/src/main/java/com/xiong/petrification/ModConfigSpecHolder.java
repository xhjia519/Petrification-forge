package com.xiong.petrification;

/**
 * 跨加载器共享的配置值持有类。
 * <p>
 * 多加载器架构下本类不再直接依赖任何加载器的配置 API：各加载器模块在自己的配置类中
 * 读取平台配置项，然后写入这里的静态字段。公共代码只读取字段值，因此无需任何加载器依赖。
 */
public final class ModConfigSpecHolder {
    /** 方块被活塞推动时的行为：MOVE 移动（仅可推不可拉）、DROP 推即掉落、BLOCK 推不动。 */
    public enum PistonBehavior { DROP, MOVE, BLOCK }

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

    /** 供加载器配置类在配置加载/重载时一次性写入全部配置值。 */
    public static void apply(double potionRangeMultiplier,
                             boolean allowCreativeMaterialization,
                             boolean allowOpMaterialization,
                             double statueBlockHardness,
                             double oxidationRate,
                             double maxOxidation,
                             boolean enableOxidation,
                             int statueRenderDistance,
                             PistonBehavior pistonBehavior,
                             int structureMaxScanSize,
                             int structureMinBlocks,
                             int structureConfirmationTimeout,
                             int structureRestoreBatchSize,
                             int structureMaxTracked) {
        ModConfigSpecHolder.potionRangeMultiplier = potionRangeMultiplier;
        ModConfigSpecHolder.allowCreativeMaterialization = allowCreativeMaterialization;
        ModConfigSpecHolder.allowOpMaterialization = allowOpMaterialization;
        ModConfigSpecHolder.statueBlockHardness = statueBlockHardness;
        ModConfigSpecHolder.oxidationRate = oxidationRate;
        ModConfigSpecHolder.maxOxidation = maxOxidation;
        ModConfigSpecHolder.enableOxidation = enableOxidation;
        ModConfigSpecHolder.statueRenderDistance = statueRenderDistance;
        ModConfigSpecHolder.pistonBehavior = pistonBehavior;
        ModConfigSpecHolder.structureMaxScanSize = structureMaxScanSize;
        ModConfigSpecHolder.structureMinBlocks = structureMinBlocks;
        ModConfigSpecHolder.structureConfirmationTimeout = structureConfirmationTimeout;
        ModConfigSpecHolder.structureRestoreBatchSize = structureRestoreBatchSize;
        ModConfigSpecHolder.structureMaxTracked = structureMaxTracked;
    }
}
