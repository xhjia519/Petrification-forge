package com.xiong.petrification.network;

import com.xiong.petrification.menu.RestorationMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** C2S：还原方块红石控制开关（开启后仅在收到红石信号时自动还原）。 */
public record RestorationSetRedstonePayload(boolean enabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RestorationSetRedstonePayload> TYPE =
            CustomPacketPayload.createType("petrification:set_restoration_redstone");
    public static final StreamCodec<RegistryFriendlyByteBuf, RestorationSetRedstonePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.enabled),
            buf -> new RestorationSetRedstonePayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RestorationSetRedstonePayload payload, ServerPlayer player) {
        if (player.containerMenu instanceof RestorationMenu menu) {
            menu.setRedstoneControlled(payload.enabled);
        }
    }
}
