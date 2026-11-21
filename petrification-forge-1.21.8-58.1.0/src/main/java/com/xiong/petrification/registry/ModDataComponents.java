package com.xiong.petrification.registry;

import com.xiong.petrification.PetrificationMod;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.component.StructureStatueData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, PetrificationMod.MODID);

    public static final RegistryObject<DataComponentType<StatueComponent>> STATUE_DATA =
            DATA_COMPONENTS.register("statue_data", () -> DataComponentType.<StatueComponent>builder()
                    .persistent(StatueComponent.CODEC)
                    .cacheEncoding()
                    .build());

    // 微缩建筑的压缩数据（外表面方块 + 容器 NBT + 元数据）
    public static final RegistryObject<DataComponentType<StructureStatueData>> STRUCTURE_STATUE_DATA =
            DATA_COMPONENTS.register("structure_statue_data", () -> DataComponentType.<StructureStatueData>builder()
                    .persistent(StructureStatueData.CODEC)
                    .cacheEncoding()
                    .build());

    private ModDataComponents() {}

    public static void register(BusGroup modBusGroup) {
        DATA_COMPONENTS.register(modBusGroup);
    }
}
