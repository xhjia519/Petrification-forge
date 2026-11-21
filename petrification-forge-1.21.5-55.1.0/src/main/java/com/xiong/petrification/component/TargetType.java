package com.xiong.petrification.component;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum TargetType implements StringRepresentable {
    BIOLOGICAL("BIOLOGICAL"),
    PLAYER("PLAYER");

    public static final Codec<TargetType> CODEC = StringRepresentable.fromEnum(TargetType::values);

    private final String name;

    TargetType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
