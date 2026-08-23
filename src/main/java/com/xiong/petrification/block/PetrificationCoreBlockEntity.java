package com.xiong.petrification.block;

import com.mojang.serialization.Codec;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.registry.ModBlockEntities;
import com.xiong.petrification.util.MaterializationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 石化核心方块实体：持有范围/白名单/玩家开关配置与激活状态（服务端权威），
 * 激活时每 20 tick 扫描范围内生物并调用材质化。全量状态经 getUpdateTag 同步客户端，
 * 供 GUI 状态行与球壳光波渲染使用。
 */
public class PetrificationCoreBlockEntity extends BlockEntity {
    public static final int MIN_RADIUS = 5;
    public static final int MAX_RADIUS = 50;
    private static final int SCAN_INTERVAL = 20;

    private int radius = 15;
    private final Set<Identifier> whitelist = new HashSet<>();
    private boolean affectPlayers;
    private boolean active;
    private boolean awaitingPayment;
    @Nullable
    private MaterialType material;
    private int tickCounter;

    public PetrificationCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PETRIFICATION_CORE_BLOCK_ENTITY.get(), pos, state);
    }

    /** 燃料 → 材质映射：MaterialType 的 ingotItem 即燃料集合（石头=圆石，与合成配方一致）。 */
    @Nullable
    public static MaterialType fuelType(ItemStack stack) {
        for (MaterialType type : MaterialType.values()) {
            if (stack.is(type.getIngotItem())) {
                return type;
            }
        }
        return null;
    }

    /** 放入燃料：消耗为激活费，按燃料材质激活（未激活/待支付/已激活换材质均适用）。由 Menu 调用，仅服务端。 */
    public void payWithFuel(ItemStack fuel) {
        MaterialType type = fuelType(fuel);
        if (type == null) {
            return;
        }
        this.material = type;
        this.active = true;
        this.awaitingPayment = false;
        this.tickCounter = 0;
        setChanged();
        syncToClients();
    }

    /** 修改配置：若激活中则停止工作并进入待支付（需新燃料重新支付）。 */
    public void setRadius(int radius) {
        this.radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        invalidateOnSettingChange();
    }

    public void setWhitelist(Set<Identifier> ids) {
        this.whitelist.clear();
        this.whitelist.addAll(ids);
        invalidateOnSettingChange();
    }

    public void setAffectPlayers(boolean affectPlayers) {
        this.affectPlayers = affectPlayers;
        invalidateOnSettingChange();
    }

    private void invalidateOnSettingChange() {
        if (active) {
            active = false;
            awaitingPayment = true;
        }
        setChanged();
        syncToClients();
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ===== 领域逻辑：激活时周期性石化范围内生物 =====

    public static void serverTick(Level level, BlockPos pos, BlockState state, PetrificationCoreBlockEntity blockEntity) {
        if (level.isClientSide() || !blockEntity.active || blockEntity.awaitingPayment || blockEntity.material == null) {
            return;
        }
        if (++blockEntity.tickCounter < SCAN_INTERVAL) {
            return;
        }
        blockEntity.tickCounter = 0;
        blockEntity.scanAndPetrify();
    }

    private void scanAndPetrify() {
        // ponytail: 半径 50 每 1 秒一次 AABB 查询；若未来实体密度过高导致卡顿，可改为按区块分片扫描
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB box = new AABB(worldPosition).inflate(radius);
        List<LivingEntity> targets = serverLevel.getEntities(EntityTypeTest.forClass(LivingEntity.class), box,
                entity -> entity.isAlive() && isAllowed(entity));
        for (LivingEntity target : targets) {
            MaterializationHelper.materializeEntity(serverLevel, target, material);
        }
    }

    private boolean isAllowed(LivingEntity entity) {
        if (entity instanceof Player player) {
            if (!affectPlayers || player.isCreative() || player.isSpectator()) {
                return false;
            }
        }
        if (!whitelist.isEmpty()) {
            Identifier id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (id == null || !whitelist.contains(id)) {
                return false;
            }
        }
        return true;
    }

    // ===== 访问器（GUI / 渲染用） =====

    public int getRadius() {
        return radius;
    }

    public Set<Identifier> getWhitelist() {
        return whitelist;
    }

    public boolean isAffectPlayers() {
        return affectPlayers;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isAwaitingPayment() {
        return awaitingPayment;
    }

    @Nullable
    public MaterialType getMaterial() {
        return material;
    }

    // ===== 持久化与同步（与 StatueBlockEntity 同模式） =====

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Radius", Codec.INT, radius);
        output.store("Whitelist", Codec.list(Identifier.CODEC), List.copyOf(whitelist));
        output.store("AffectPlayers", Codec.BOOL, affectPlayers);
        output.store("Active", Codec.BOOL, active);
        output.store("AwaitingPayment", Codec.BOOL, awaitingPayment);
        if (material != null) {
            output.store("Material", Codec.STRING, material.getSerializedName());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, input.read("Radius", Codec.INT).orElse(15)));
        whitelist.clear();
        input.read("Whitelist", Codec.list(Identifier.CODEC)).orElse(List.of())
                .forEach(whitelist::add);
        affectPlayers = input.read("AffectPlayers", Codec.BOOL).orElse(false);
        active = input.read("Active", Codec.BOOL).orElse(false);
        awaitingPayment = input.read("AwaitingPayment", Codec.BOOL).orElse(false);
        material = input.read("Material", Codec.STRING).map(MaterialType::fromEffectId).orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
