package com.xiong.petrification.network;

import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import com.xiong.petrification.menu.PetrificationCoreMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** C2S：滑块设置半径（5~50）。 */
public record CoreSetRadiusPayload(int radius) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CoreSetRadiusPayload> TYPE =
            CustomPacketPayload.createType("petrification:set_radius");
    public static final StreamCodec<RegistryFriendlyByteBuf, CoreSetRadiusPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.radius),
            buf -> new CoreSetRadiusPayload(buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CoreSetRadiusPayload payload, ServerPlayer player) {
        if (player.containerMenu instanceof PetrificationCoreMenu menu
                && player.level().getBlockEntity(menu.getCorePos()) instanceof PetrificationCoreBlockEntity core
                && player.distanceToSqr(core.getBlockPos().getX() + 0.5, core.getBlockPos().getY() + 0.5,
                        core.getBlockPos().getZ() + 0.5) <= 64 * 64) {
            core.setRadius(payload.radius);
        }
    }
}
