package com.xiong.petrification.network;

import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import com.xiong.petrification.menu.PetrificationCoreMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** C2S：红石控制开关（开启后仅在收到红石信号时工作）。 */
public record CoreSetRedstonePayload(boolean enabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CoreSetRedstonePayload> TYPE =
            CustomPacketPayload.createType("petrification:set_redstone");
    public static final StreamCodec<RegistryFriendlyByteBuf, CoreSetRedstonePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.enabled),
            buf -> new CoreSetRedstonePayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CoreSetRedstonePayload payload, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof PetrificationCoreMenu menu
                    && player.level().getBlockEntity(menu.getCorePos()) instanceof PetrificationCoreBlockEntity core
                    && player.distanceToSqr(core.getBlockPos().getX() + 0.5, core.getBlockPos().getY() + 0.5,
                            core.getBlockPos().getZ() + 0.5) <= 64 * 64) {
                core.setRedstoneControlled(payload.enabled);
            }
        });
        ctx.setPacketHandled(true);
    }
}
