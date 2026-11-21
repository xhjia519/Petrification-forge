package com.xiong.petrification.network;

import com.xiong.petrification.menu.PetrificationCoreMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** C2S：点击"关闭激活"按钮，服务端停止核心领域（不要求重新支付，配置保持不变）。 */
public record CoreDeactivatePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CoreDeactivatePayload> TYPE =
            CustomPacketPayload.createType("petrification:deactivate_core");
    public static final StreamCodec<RegistryFriendlyByteBuf, CoreDeactivatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
            },
            buf -> new CoreDeactivatePayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CoreDeactivatePayload payload, ServerPlayer player) {
        if (player.containerMenu instanceof PetrificationCoreMenu menu) {
            menu.tryDeactivate();
        }
    }
}
