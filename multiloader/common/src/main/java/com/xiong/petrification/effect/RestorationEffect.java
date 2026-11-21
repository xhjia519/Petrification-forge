package com.xiong.petrification.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Instantaneous beneficial effect backing the restoration potion. The actual
 * statue restoration is performed area-based when the potion splashes (see
 * ModEventHandlers.onPotionSplash), mirroring the materialization potions.
 */
public class RestorationEffect extends MobEffect {
    public RestorationEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean isInstantaneous() {
        return true;
    }
}
