package com.xiong.petrification.network;

import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import com.xiong.petrification.menu.PetrificationCoreMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** C2S：是否石化玩家开关。 */
public record CoreSetAffectPlayersPayload(boolean affectPlayers) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CoreSetAffectPlayersPayload> TYPE =
            CustomPacketPayload.createType("petrification:set_affect_players");
    public static final StreamCodec<RegistryFriendlyByteBuf, CoreSetAffectPlayersPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.affectPlayers),
            buf -> new CoreSetAffectPlayersPayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CoreSetAffectPlayersPayload payload, ServerPlayer player) {
        if (player.containerMenu instanceof PetrificationCoreMenu menu
                && player.level().getBlockEntity(menu.getCorePos()) instanceof PetrificationCoreBlockEntity core
                && player.distanceToSqr(core.getBlockPos().getX() + 0.5, core.getBlockPos().getY() + 0.5,
                        core.getBlockPos().getZ() + 0.5) <= 64 * 64) {
            core.setAffectPlayers(payload.affectPlayers);
        }
    }
}
