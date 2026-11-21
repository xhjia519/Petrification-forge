package com.xiong.petrification.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C：服务端在玩家打开石化核心/还原 GUI 时下发方块位置
 * （26.2 的 openMenu 不携带额外数据，客户端 Menu 拿不到 pos）。
 * 客户端 Screen 据此定位方块实体，保证按钮/滑块状态显示可靠，
 * 不再依赖打开瞬间的 hitResult 猜测。
 */
public record GuiPosPayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GuiPosPayload> TYPE =
            CustomPacketPayload.createType("petrification:gui_pos");
    public static final StreamCodec<RegistryFriendlyByteBuf, GuiPosPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBlockPos(payload.pos),
            buf -> new GuiPosPayload(buf.readBlockPos()));

    private static BlockPos lastPos;

    /** 客户端：最近一次打开核心/还原 GUI 时服务端下发的方块位置（无则 null）。 */
    public static BlockPos lastPos() {
        return lastPos;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端主线程调用。 */
    public static void handle(GuiPosPayload payload) {
        lastPos = payload.pos;
    }
}
