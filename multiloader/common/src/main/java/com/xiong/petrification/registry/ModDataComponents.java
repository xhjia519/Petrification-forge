package com.xiong.petrification.registry;

import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.component.StructureStatueData;
import com.xiong.petrification.platform.Platform;
import com.xiong.petrification.platform.Registrar;
import com.xiong.petrification.platform.RegistryEntry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

public final class ModDataComponents {
    public static final Registrar<DataComponentType<?>> DATA_COMPONENTS = Platform.registrar(Registries.DATA_COMPONENT_TYPE);

    public static final RegistryEntry<DataComponentType<StatueComponent>> STATUE_DATA =
            DATA_COMPONENTS.register("statue_data", () -> DataComponentType.<StatueComponent>builder()
                    .persistent(StatueComponent.CODEC)
                    .cacheEncoding()
                    .build());

    // 微缩建筑的压缩数据（外表面方块 + 容器 NBT + 元数据）
    public static final RegistryEntry<DataComponentType<StructureStatueData>> STRUCTURE_STATUE_DATA =
            DATA_COMPONENTS.register("structure_statue_data", () -> DataComponentType.<StructureStatueData>builder()
                    .persistent(StructureStatueData.CODEC)
                    .cacheEncoding()
                    .build());

    private ModDataComponents() {}

    public static void register() {
        DATA_COMPONENTS.bind();
    }
}
