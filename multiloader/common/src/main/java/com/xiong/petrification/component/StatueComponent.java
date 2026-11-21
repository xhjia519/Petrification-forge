package com.xiong.petrification.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;
import java.util.UUID;

public record StatueComponent(
        TargetType targetType,
        Identifier entityType,
        Optional<UUID> entityUuid,
        MaterialType materialType,
        float oxidation,
        boolean isWaxed,
        long materializedTime,
        float yaw,
        float pitch,
        float headYaw,
        float bodyYaw,
        boolean isSneaking,
        boolean isSprinting,
        boolean isSwimming,
        boolean isFlying,
        float limbSwing,
        float limbSwingAmount,
        boolean isBaby,
        Optional<String> variant,
        Optional<Integer> color,
        Optional<CompoundTag> equipment,
        Optional<Float> health,
        Optional<Integer> age,
        Optional<String> customName,
        Optional<Boolean> isTamed,
        Optional<UUID> ownerUuid,
        Optional<String> playerName,
        Optional<CompoundTag> gameProfile,
        Optional<Integer> foodLevel,
        Optional<Float> saturation,
        Optional<Integer> experienceLevel,
        Optional<Float> experienceProgress,
        Optional<Integer> totalExperience,
        Optional<Integer> air,
        Optional<Float> fallDistance,
        Optional<String> displayName,
        boolean hasCustomName,
        Optional<CompoundTag> villagerOffers,
        Optional<CompoundTag> entityNbt
) {
    // 38 fields split into 4 groups (max 16 per group), then combined via xmap chain

    // Group A: 7 fields (basic entity info)
    private record GroupA(TargetType targetType, Identifier entityType, Optional<UUID> entityUuid,
                          MaterialType materialType, float oxidation, boolean isWaxed, long materializedTime) {}

    // Group B: 10 fields (pose + limb data)
    private record GroupB(float yaw, float pitch, float headYaw, float bodyYaw,
                          boolean isSneaking, boolean isSprinting, boolean isSwimming, boolean isFlying,
                          float limbSwing, float limbSwingAmount) {}

    // Group C: 10 fields (optional data part 1)
    private record GroupC(boolean isBaby, Optional<String> variant, Optional<Integer> color,
                          Optional<CompoundTag> equipment, Optional<Float> health, Optional<Integer> age,
                          Optional<String> customName, Optional<Boolean> isTamed, Optional<UUID> ownerUuid,
                          Optional<String> playerName) {}

    // Group D: 12 fields (optional data part 2)
    private record GroupD(Optional<CompoundTag> gameProfile, Optional<Integer> foodLevel,
                          Optional<Float> saturation, Optional<Integer> experienceLevel,
                          Optional<Float> experienceProgress, Optional<Integer> totalExperience,
                          Optional<Integer> air, Optional<Float> fallDistance,
                          Optional<String> displayName, boolean hasCustomName,
                          Optional<CompoundTag> villagerOffers, Optional<CompoundTag> entityNbt) {}

    private record Pair1(GroupA a, GroupB b) {}
    private record Pair2(GroupC c, GroupD d) {}
    private record Combined(Pair1 pair1, Pair2 pair2) {}

    private static final Codec<GroupA> GROUP_A = RecordCodecBuilder.create(i -> i.group(
            TargetType.CODEC.fieldOf("targetType").forGetter(GroupA::targetType),
            Identifier.CODEC.fieldOf("entityType").forGetter(GroupA::entityType),
            UUIDUtil.CODEC.optionalFieldOf("entityUuid").forGetter(GroupA::entityUuid),
            MaterialType.CODEC.fieldOf("materialType").forGetter(GroupA::materialType),
            ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("oxidation", 0.0f).forGetter(GroupA::oxidation),
            Codec.BOOL.optionalFieldOf("isWaxed", false).forGetter(GroupA::isWaxed),
            Codec.LONG.optionalFieldOf("materializedTime", 0L).forGetter(GroupA::materializedTime)
    ).apply(i, GroupA::new));

    private static final Codec<GroupB> GROUP_B = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("yaw", 0.0f).forGetter(GroupB::yaw),
            Codec.FLOAT.optionalFieldOf("pitch", 0.0f).forGetter(GroupB::pitch),
            Codec.FLOAT.optionalFieldOf("headYaw", 0.0f).forGetter(GroupB::headYaw),
            Codec.FLOAT.optionalFieldOf("bodyYaw", 0.0f).forGetter(GroupB::bodyYaw),
            Codec.BOOL.optionalFieldOf("isSneaking", false).forGetter(GroupB::isSneaking),
            Codec.BOOL.optionalFieldOf("isSprinting", false).forGetter(GroupB::isSprinting),
            Codec.BOOL.optionalFieldOf("isSwimming", false).forGetter(GroupB::isSwimming),
            Codec.BOOL.optionalFieldOf("isFlying", false).forGetter(GroupB::isFlying),
            Codec.FLOAT.optionalFieldOf("limbSwing", 0.0f).forGetter(GroupB::limbSwing),
            Codec.FLOAT.optionalFieldOf("limbSwingAmount", 0.0f).forGetter(GroupB::limbSwingAmount)
    ).apply(i, GroupB::new));

    private static final Codec<GroupC> GROUP_C = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("isBaby", false).forGetter(GroupC::isBaby),
            Codec.STRING.optionalFieldOf("variant").forGetter(GroupC::variant),
            Codec.INT.optionalFieldOf("color").forGetter(GroupC::color),
            CompoundTag.CODEC.optionalFieldOf("equipment").forGetter(GroupC::equipment),
            Codec.FLOAT.optionalFieldOf("health").forGetter(GroupC::health),
            Codec.INT.optionalFieldOf("age").forGetter(GroupC::age),
            Codec.STRING.optionalFieldOf("customName").forGetter(GroupC::customName),
            Codec.BOOL.optionalFieldOf("isTamed").forGetter(GroupC::isTamed),
            UUIDUtil.CODEC.optionalFieldOf("ownerUuid").forGetter(GroupC::ownerUuid),
            Codec.STRING.optionalFieldOf("playerName").forGetter(GroupC::playerName)
    ).apply(i, GroupC::new));

    private static final Codec<GroupD> GROUP_D = RecordCodecBuilder.create(i -> i.group(
            CompoundTag.CODEC.optionalFieldOf("gameProfile").forGetter(GroupD::gameProfile),
            Codec.INT.optionalFieldOf("foodLevel").forGetter(GroupD::foodLevel),
            Codec.FLOAT.optionalFieldOf("saturation").forGetter(GroupD::saturation),
            Codec.INT.optionalFieldOf("experienceLevel").forGetter(GroupD::experienceLevel),
            Codec.FLOAT.optionalFieldOf("experienceProgress").forGetter(GroupD::experienceProgress),
            Codec.INT.optionalFieldOf("totalExperience").forGetter(GroupD::totalExperience),
            Codec.INT.optionalFieldOf("air").forGetter(GroupD::air),
            Codec.FLOAT.optionalFieldOf("fallDistance").forGetter(GroupD::fallDistance),
            Codec.STRING.optionalFieldOf("displayName").forGetter(GroupD::displayName),
            Codec.BOOL.optionalFieldOf("hasCustomName", false).forGetter(GroupD::hasCustomName),
            CompoundTag.CODEC.optionalFieldOf("villagerOffers").forGetter(GroupD::villagerOffers),
            CompoundTag.CODEC.optionalFieldOf("entityNbt").forGetter(GroupD::entityNbt)
    ).apply(i, GroupD::new));

    private static final Codec<Pair1> PAIR1_CODEC = RecordCodecBuilder.create(i -> i.group(
            GROUP_A.fieldOf("basic").forGetter(Pair1::a),
            GROUP_B.fieldOf("pose").forGetter(Pair1::b)
    ).apply(i, Pair1::new));

    private static final Codec<Pair2> PAIR2_CODEC = RecordCodecBuilder.create(i -> i.group(
            GROUP_C.fieldOf("optA").forGetter(Pair2::c),
            GROUP_D.fieldOf("optB").forGetter(Pair2::d)
    ).apply(i, Pair2::new));

    private static final Codec<Combined> COMBINED_CODEC = RecordCodecBuilder.create(i -> i.group(
            PAIR1_CODEC.fieldOf("part1").forGetter(Combined::pair1),
            PAIR2_CODEC.fieldOf("part2").forGetter(Combined::pair2)
    ).apply(i, Combined::new));

    public static final Codec<StatueComponent> CODEC = COMBINED_CODEC.xmap(
            StatueComponent::fromCombined,
            StatueComponent::toCombined
    );

    private static StatueComponent fromCombined(Combined combined) {
        GroupA a = combined.pair1().a();
        GroupB b = combined.pair1().b();
        GroupC c = combined.pair2().c();
        GroupD d = combined.pair2().d();
        return new StatueComponent(
                a.targetType, a.entityType, (Optional<UUID>) a.entityUuid, a.materialType,
                a.oxidation, a.isWaxed, a.materializedTime,
                b.yaw, b.pitch, b.headYaw, b.bodyYaw,
                b.isSneaking, b.isSprinting, b.isSwimming, b.isFlying,
                b.limbSwing, b.limbSwingAmount, c.isBaby,
                (Optional<String>) c.variant, (Optional<Integer>) c.color,
                (Optional<CompoundTag>) c.equipment, (Optional<Float>) c.health,
                (Optional<Integer>) c.age, (Optional<String>) c.customName,
                (Optional<Boolean>) c.isTamed, (Optional<UUID>) c.ownerUuid,
                (Optional<String>) c.playerName,
                (Optional<CompoundTag>) d.gameProfile, (Optional<Integer>) d.foodLevel,
                (Optional<Float>) d.saturation, (Optional<Integer>) d.experienceLevel,
                (Optional<Float>) d.experienceProgress, (Optional<Integer>) d.totalExperience,
                (Optional<Integer>) d.air, (Optional<Float>) d.fallDistance,
                (Optional<String>) d.displayName, d.hasCustomName,
                (Optional<CompoundTag>) d.villagerOffers,
                (Optional<CompoundTag>) d.entityNbt
        );
    }

    private static Combined toCombined(StatueComponent comp) {
        return new Combined(
                new Pair1(
                        new GroupA(comp.targetType, comp.entityType, comp.entityUuid, comp.materialType,
                                comp.oxidation, comp.isWaxed, comp.materializedTime),
                        new GroupB(comp.yaw, comp.pitch, comp.headYaw, comp.bodyYaw,
                                comp.isSneaking, comp.isSprinting, comp.isSwimming, comp.isFlying,
                                comp.limbSwing, comp.limbSwingAmount)
                ),
                new Pair2(
                        new GroupC(comp.isBaby, comp.variant, comp.color, comp.equipment,
                                comp.health, comp.age, comp.customName, comp.isTamed,
                                comp.ownerUuid, comp.playerName),
                        new GroupD(comp.gameProfile, comp.foodLevel, comp.saturation, comp.experienceLevel,
                                comp.experienceProgress, comp.totalExperience, comp.air, comp.fallDistance,
                                comp.displayName, comp.hasCustomName, comp.villagerOffers, comp.entityNbt)
                )
        );
    }

    public static StatueComponent defaultStatue(MaterialType materialType) {
        return new StatueComponent(
                TargetType.BIOLOGICAL,
                Identifier.parse("minecraft:zombie"),
                Optional.<UUID>empty(),
                materialType,
                0.0f,
                false,
                0L,
                0.0f, 0.0f, 0.0f, 0.0f,
                false, false, false, false,
                0.0f, 0.0f,
                false,
                Optional.<String>empty(),
                Optional.<Integer>empty(),
                Optional.<CompoundTag>empty(),
                Optional.<Float>empty(),
                Optional.<Integer>empty(),
                Optional.<String>empty(),
                Optional.<Boolean>empty(),
                Optional.<UUID>empty(),
                Optional.<String>empty(),
                Optional.<CompoundTag>empty(),
                Optional.<Integer>empty(),
                Optional.<Float>empty(),
                Optional.<Integer>empty(),
                Optional.<Float>empty(),
                Optional.<Integer>empty(),
                Optional.<Integer>empty(),
                Optional.<Float>empty(),
                Optional.of(materialType.getDisplayPrefix() + " Zombie"),
                false,
                Optional.<CompoundTag>empty(),
                Optional.<CompoundTag>empty()
        );
    }

    public Component getDisplayComponent() {
        if (displayName.isPresent()) {
            return Component.literal(displayName.get());
        }
        return Component.translatable("statue.petrification.unnamed");
    }

    public StatueComponent withOxidation(float newOxidation) {
        return new StatueComponent(
                targetType, entityType, entityUuid, materialType, newOxidation, isWaxed, materializedTime,
                yaw, pitch, headYaw, bodyYaw, isSneaking, isSprinting, isSwimming, isFlying,
                limbSwing, limbSwingAmount, isBaby, variant, color, equipment, health, age, customName,
                isTamed, ownerUuid, playerName, gameProfile, foodLevel, saturation, experienceLevel,
                experienceProgress, totalExperience, air, fallDistance, displayName, hasCustomName,
                villagerOffers, entityNbt
        );
    }

    public StatueComponent withWaxed(boolean waxed) {
        return new StatueComponent(
                targetType, entityType, entityUuid, materialType, oxidation, waxed, materializedTime,
                yaw, pitch, headYaw, bodyYaw, isSneaking, isSprinting, isSwimming, isFlying,
                limbSwing, limbSwingAmount, isBaby, variant, color, equipment, health, age, customName,
                isTamed, ownerUuid, playerName, gameProfile, foodLevel, saturation, experienceLevel,
                experienceProgress, totalExperience, air, fallDistance, displayName, hasCustomName,
                villagerOffers, entityNbt
        );
    }
}
