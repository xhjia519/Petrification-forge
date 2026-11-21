package com.xiong.petrification.block;

import com.mojang.serialization.Codec;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.registry.ModBlockEntities;
import com.xiong.petrification.util.MaterializationHelper;
import com.xiong.petrification.util.PistonMoveData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
    /** 粒子光波周期：60 tick（3 秒）一圈，从核心连续向外扩散，无停顿。 */
    private static final int PULSE_TICKS = 60;
    /** 粒子环间距：约每 2 格周长一个粒子，大半径时自动增密，保证圆环连续不断裂。 */
    private static final double RING_PARTICLE_SPACING = 2.0;
    /** 粒子环高度：方块顶面上方，避免被方块本体遮挡不可见。 */
    private static final double RING_Y_OFFSET = 1.05;

    /** 燃料容器（1 格）：持久化在方块内，破坏方块时掉落剩余燃料。 */
    private final SimpleContainer fuelContainer = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            PetrificationCoreBlockEntity.this.setChanged();
        }
    };
    private int radius = 15;
    private final Set<ResourceLocation> whitelist = new HashSet<>();
    private boolean affectPlayers;
    /** 红石控制：开启后仅在收到红石信号时工作（运行开关，不属于领域配置，不触发待支付）。 */
    private boolean redstoneControlled;
    private boolean active;
    private boolean awaitingPayment;
    /** 激活时累计消耗的燃料总数（GUI 消耗显示用，仅服务端累计，经 getUpdateTag 同步）。 */
    private int consumedCount;
    @Nullable
    private MaterialType material;
    private int tickCounter;
    private int pulseTick;

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

    /**
     * 放入燃料：按当前范围半径消耗对应数量作为激活费（范围越大激活费越高），
     * 仅消耗 min(radius, 堆叠数)，剩余留在槽位。由 Menu 调用，仅服务端。
     */
    public boolean payWithFuel(ItemStack fuel) {
        MaterialType type = fuelType(fuel);
        if (type == null) {
            return false;
        }
        int cost = Math.min(radius, fuel.getCount());
        if (cost <= 0) {
            return false;
        }
        fuel.shrink(cost);
        this.material = type;
        this.consumedCount += cost;
        this.active = true;
        this.awaitingPayment = false;
        this.tickCounter = 0;
        setChanged();
        syncToClients();
        return true;
    }

    /** 关闭激活（GUI"关闭激活"按钮）：停止领域，配置与消耗统计保留，无需重新支付即可再次激活。 */
    public void deactivate() {
        this.active = false;
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

    public void setWhitelist(Set<ResourceLocation> ids) {
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
        if (level.isClientSide()) {
            return;
        }
        // 红石控制：开启后无红石信号时暂停工作（保留激活状态与配置，信号恢复即继续）
        if (blockEntity.redstoneControlled && !level.hasNeighborSignal(pos)) {
            return;
        }
        if (!blockEntity.active || blockEntity.awaitingPayment || blockEntity.material == null) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        blockEntity.tickPulse(serverLevel);
        if (++blockEntity.tickCounter < SCAN_INTERVAL) {
            return;
        }
        blockEntity.tickCounter = 0;
        blockEntity.scanAndPetrify();
    }

    /** 粒子光波脉冲：每 60 tick 一圈，每 tick 在当前位置半径上放一圈绿色粒子，连续向外扩散。 */
    private void tickPulse(ServerLevel serverLevel) {
        pulseTick++;
        if (pulseTick >= PULSE_TICKS) {
            pulseTick = 0;
        }
        double t = pulseTick / (double) PULSE_TICKS;
        double r = 0.5 + (radius - 0.5) * t;
        spawnParticleRing(serverLevel, r);
    }

    /** 在方块顶面上方以核心为中心、指定半径处放一圈 HAPPY_VILLAGER（绿色）粒子。 */
    private void spawnParticleRing(ServerLevel serverLevel, double ringRadius) {
        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + RING_Y_OFFSET;
        double cz = worldPosition.getZ() + 0.5;
        int count = Math.max(24, (int) Math.ceil(ringRadius * 2 * Math.PI / RING_PARTICLE_SPACING));
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double px = cx + ringRadius * Math.cos(angle);
            double pz = cz + ringRadius * Math.sin(angle);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, px, cy, pz, 1, 0, 0, 0, 0);
        }
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
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
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

    public Set<ResourceLocation> getWhitelist() {
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

    public int getConsumedCount() {
        return consumedCount;
    }

    public boolean isRedstoneControlled() {
        return redstoneControlled;
    }

    /** 红石控制开关：不属于领域配置变更，切换无需重新支付燃料。 */
    public void setRedstoneControlled(boolean redstoneControlled) {
        this.redstoneControlled = redstoneControlled;
        setChanged();
        syncToClients();
    }

    public SimpleContainer getFuelContainer() {
        return fuelContainer;
    }

    /** 方块被破坏时掉落容器内剩余物品（仅服务端调用，playerWillDestroy 时机方块实体仍存在）。 */
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < fuelContainer.getContainerSize(); i++) {
            ItemStack stack = fuelContainer.getItem(i);
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack);
                fuelContainer.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Nullable
    public MaterialType getMaterial() {
        return material;
    }

    // ===== 持久化与同步（与 StatueBlockEntity 同模式） =====

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("Fuel", ItemStack.OPTIONAL_CODEC, fuelContainer.getItem(0));
        output.store("Radius", Codec.INT, radius);
        output.store("Whitelist", Codec.list(ResourceLocation.CODEC), List.copyOf(whitelist));
        output.store("AffectPlayers", Codec.BOOL, affectPlayers);
        output.store("RedstoneControlled", Codec.BOOL, redstoneControlled);
        output.store("Active", Codec.BOOL, active);
        output.store("AwaitingPayment", Codec.BOOL, awaitingPayment);
        output.store("ConsumedCount", Codec.INT, consumedCount);
        if (material != null) {
            output.store("Material", Codec.STRING, material.getSerializedName());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, input.read("Radius", Codec.INT).orElse(15)));
        whitelist.clear();
        input.read("Whitelist", Codec.list(ResourceLocation.CODEC)).orElse(List.of())
                .forEach(whitelist::add);
        fuelContainer.setItem(0, input.read("Fuel", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        affectPlayers = input.read("AffectPlayers", Codec.BOOL).orElse(false);
        redstoneControlled = input.read("RedstoneControlled", Codec.BOOL).orElse(false);
        active = input.read("Active", Codec.BOOL).orElse(false);
        awaitingPayment = input.read("AwaitingPayment", Codec.BOOL).orElse(false);
        consumedCount = input.read("ConsumedCount", Codec.INT).orElse(0);
        material = input.read("Material", Codec.STRING).map(MaterialType::fromEffectId).orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 被活塞推动后恢复配置与燃料（PistonEvent.Pre 已保存 NBT）
        PistonMoveData.restoreIfMoved(this);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
