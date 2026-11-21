package com.xiong.petrification.client;

import com.xiong.petrification.block.StatueBlockEntity;
import com.xiong.petrification.component.StatueComponent;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Provides material-based color tinting for statue blocks.
 * Uses the StatueComponent data from the BlockEntity to determine the tint color.
 * The tint is blended with white (0xFFFFFF) to preserve texture detail while
 * adding the material's characteristic color.
 */
public class StatueBlockTintSource implements BlockColor {
    private static final int WHITE = 0xFFFFFF;
    // Blend ratio: 40% material color + 60% white = subtle tint
    private static final float MATERIAL_WEIGHT = 0.4f;
    private static final float WHITE_WEIGHT = 0.6f;

    @Override
    public int getColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        if (level.getBlockEntity(pos) instanceof StatueBlockEntity blockEntity) {
            StatueComponent component = blockEntity.getStatueData();
            if (component != null) {
                int matColor = component.materialType().getColor();
                return blendColor(matColor, WHITE, MATERIAL_WEIGHT, WHITE_WEIGHT);
            }
        }
        return WHITE;
    }

    private static int blendColor(int colorA, int colorB, float weightA, float weightB) {
        int rA = (colorA >> 16) & 0xFF, gA = (colorA >> 8) & 0xFF, bA = colorA & 0xFF;
        int rB = (colorB >> 16) & 0xFF, gB = (colorB >> 8) & 0xFF, bB = colorB & 0xFF;
        int r = Math.round(rA * weightA + rB * weightB);
        int g = Math.round(gA * weightA + gB * weightB);
        int b = Math.round(bA * weightA + bB * weightB);
        return (r << 16) | (g << 8) | b;
    }
}
