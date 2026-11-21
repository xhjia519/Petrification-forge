package com.xiong.petrification.network;

import com.xiong.petrification.menu.PetrificationCoreMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** C2S：点击"激活"按钮，服务端消耗燃料槽全部物品并激活核心。 */
public record CoreActivatePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CoreActivatePayload> TYPE =
            CustomPacketPayload.createType("petrification:activate_core");
    public static final StreamCodec<RegistryFriendlyByteBuf, CoreActivatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
            },
            buf -> new CoreActivatePayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CoreActivatePayload payload, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof PetrificationCoreMenu menu) {
                menu.tryActivate();
            }
        });
        ctx.setPacketHandled(true);
    }
}
