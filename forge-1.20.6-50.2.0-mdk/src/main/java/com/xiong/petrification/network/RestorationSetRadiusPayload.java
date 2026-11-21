package com.xiong.petrification.network;

import com.xiong.petrification.menu.RestorationMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** C2S：还原方块滑块设置半径（5~50）。 */
public record RestorationSetRadiusPayload(int radius) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RestorationSetRadiusPayload> TYPE =
            CustomPacketPayload.createType("petrification:set_restoration_radius");
    public static final StreamCodec<RegistryFriendlyByteBuf, RestorationSetRadiusPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.radius),
            buf -> new RestorationSetRadiusPayload(buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RestorationSetRadiusPayload payload, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof RestorationMenu menu) {
                menu.setRadius(payload.radius);
            }
        });
        ctx.setPacketHandled(true);
    }
}
