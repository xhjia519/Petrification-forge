package com.xiong.petrification.effect;

import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.util.MaterializationHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class MaterializationEffect extends MobEffect {
    private final MaterialType materialType;

    public MaterializationEffect(MobEffectCategory category, int color, MaterialType materialType) {
        super(category, color);
        this.materialType = materialType;
    }

    public MaterialType getMaterialType() {
        return materialType;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        MaterializationHelper.materializeEntity(level, entity, materialType);
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration == 1;
    }

    @Override
    public boolean isInstantaneous() {
        return true;
    }
}
