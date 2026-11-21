package com.xiong.petrification.component;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public enum MaterialType implements StringRepresentable {
    STONE("stone", 0x808080, Items.COBBLESTONE, Blocks.COBBLESTONE),
    IRON("iron", 0xA0A0A0, Items.IRON_INGOT, Blocks.IRON_BLOCK),
    COPPER("copper", 0xB87333, Items.COPPER_INGOT, Blocks.RAW_COPPER_BLOCK),
    GOLD("gold", 0xFFD700, Items.GOLD_INGOT, Blocks.GOLD_BLOCK),
    DIAMOND("diamond", 0x00FFFF, Items.DIAMOND, Blocks.DIAMOND_BLOCK),
    NETHERITE("netherite", 0x454545, Items.NETHERITE_INGOT, Blocks.NETHERITE_BLOCK);

    public static final Codec<MaterialType> CODEC = StringRepresentable.fromEnum(MaterialType::values);

    private final String name;
    private final int color;
    private final Item ingotItem;
    private final net.minecraft.world.level.block.Block block;

    MaterialType(String name, int color, Item ingotItem, net.minecraft.world.level.block.Block block) {
        this.name = name;
        this.color = color;
        this.ingotItem = ingotItem;
        this.block = block;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int getColor() {
        return color;
    }

    public Item getIngotItem() {
        return ingotItem;
    }

    public net.minecraft.world.level.block.Block getBlock() {
        return block;
    }

    public String getDisplayPrefix() {
        return switch (this) {
            case STONE -> "Petrified";
            case IRON -> "Iron";
            case COPPER -> "Copper";
            case GOLD -> "Golden";
            case DIAMOND -> "Diamond";
            case NETHERITE -> "Netherite";
        };
    }

    public static MaterialType fromEffectId(String effectId) {
        for (MaterialType type : values()) {
            if (type.name.equals(effectId)) {
                return type;
            }
        }
        return STONE;
    }
}
