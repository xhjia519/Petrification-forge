package com.xiong.petrification.block;

import com.mojang.serialization.Codec;
import com.xiong.petrification.handler.RestorationHandler;
import com.xiong.petrification.registry.ModBlockEntities;
import com.xiong.petrification.util.PistonMoveData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * 还原方块实体：持有还原粉槽（1 格，持久化在方块内）与范围半径配置（服务端权威），
 * 放置后自动还原范围内雕像方块：每还原一个生物消耗 1 个还原粉，粉不足则暂停等待。
 * 槽位容器直接暴露给 Menu（Slot 直连），打开 GUI 时自动还原不中断。
 */
public class RestorationBlockEntity extends BlockEntity {
    public static final int MIN_RADIUS = 5;
    public static final int MAX_RADIUS = 50;
    private static final int SCAN_INTERVAL = 10;
    /** 每周期最多还原的雕像数（粉足够时可一次处理多个，避免大范围逐个排队过慢）。 */
    private static final int MAX_RESTORE_PER_SCAN = 4;

    private final SimpleContainer dustContainer = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            RestorationBlockEntity.this.setChanged();
        }
    };
    /** 红石控制：开启后仅在收到红石信号时工作（运行开关，不属于配置变更）。 */
    private boolean redstoneControlled;
    private int radius = 15;
    private int tickCounter;

    public RestorationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESTORATION_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RestorationBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }
        // 红石控制：开启后无红石信号时暂停还原（信号恢复即继续）
        if (blockEntity.redstoneControlled && !level.hasNeighborSignal(pos)) {
            return;
        }
        if (++blockEntity.tickCounter < SCAN_INTERVAL) {
            return;
        }
        blockEntity.tickCounter = 0;
        blockEntity.scanAndRestore();
    }

    /** 自动还原：粉槽有粉时把范围内雕像逐个还原，每还原一个消耗 1 个还原粉。 */
    private void scanAndRestore() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack dust = dustContainer.getItem(0);
        if (dust.isEmpty()) {
            return;
        }
        int r = radius;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        BlockPos centerPos = BlockPos.containing(center);
        int restored = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(centerPos.offset(-r, -r, -r), centerPos.offset(r, r, r))) {
            if (restored >= MAX_RESTORE_PER_SCAN || dust.isEmpty()) {
                break;
            }
            if (center.distanceTo(Vec3.atCenterOf(candidate)) > (double) r) {
                continue;
            }
            if (!(level.getBlockEntity(candidate) instanceof StatueBlockEntity)) {
                continue;
            }
            if (RestorationHandler.restoreBlock(null, level, candidate)) {
                dust.shrink(1);
                restored++;
            }
        }
        if (restored > 0) {
            setChanged();
        }
    }

    public void setRadius(int radius) {
        this.radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        setChanged();
        syncToClients();
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getRadius() {
        return radius;
    }

    public SimpleContainer getDustContainer() {
        return dustContainer;
    }

    public boolean isRedstoneControlled() {
        return redstoneControlled;
    }

    /** 红石控制开关：不属于配置变更，切换无需额外条件。 */
    public void setRedstoneControlled(boolean redstoneControlled) {
        this.redstoneControlled = redstoneControlled;
        setChanged();
        syncToClients();
    }

    /** 方块被破坏时掉落容器内剩余物品（仅服务端调用，playerWillDestroy 时机方块实体仍存在）。 */
    public void dropContents(Level level, BlockPos pos) {
        ItemStack dust = dustContainer.getItem(0);
        if (!dust.isEmpty()) {
            Block.popResource(level, pos, dust);
            dustContainer.setItem(0, ItemStack.EMPTY);
        }
    }

    // ===== 持久化与同步（与 StatueBlockEntity 同模式） =====

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("RedstoneControlled", Codec.BOOL, redstoneControlled);
        output.store("Radius", Codec.INT, radius);
        output.store("Dust", ItemStack.OPTIONAL_CODEC, dustContainer.getItem(0));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        redstoneControlled = input.read("RedstoneControlled", Codec.BOOL).orElse(false);
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, input.read("Radius", Codec.INT).orElse(15)));
        dustContainer.setItem(0, input.read("Dust", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 被活塞推动后恢复配置与还原粉（PistonEvent.Pre 已保存 NBT）
        PistonMoveData.restoreIfMoved(this);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
