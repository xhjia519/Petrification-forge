package com.xiong.petrification.network;

import com.xiong.petrification.block.PetrificationCoreBlockEntity;
import com.xiong.petrification.menu.PetrificationCoreMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** C2S：白名单实体集合（空 = 全部石化；上限 32 防滥用）。 */
public record CoreSetWhitelistPayload(List<Identifier> ids) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CoreSetWhitelistPayload> TYPE =
            CustomPacketPayload.createType("petrification:set_whitelist");
    public static final StreamCodec<RegistryFriendlyByteBuf, CoreSetWhitelistPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.ids.size());
                for (Identifier id : payload.ids) {
                    buf.writeUtf(id.toString());
                }
            },
            buf -> {
                int size = Math.min(buf.readVarInt(), 64);
                List<Identifier> ids = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    ids.add(Identifier.parse(buf.readUtf()));
                }
                return new CoreSetWhitelistPayload(ids);
            });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CoreSetWhitelistPayload payload, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof PetrificationCoreMenu menu
                    && player.level().getBlockEntity(menu.getCorePos()) instanceof PetrificationCoreBlockEntity core
                    && player.distanceToSqr(core.getBlockPos().getX() + 0.5, core.getBlockPos().getY() + 0.5,
                            core.getBlockPos().getZ() + 0.5) <= 64 * 64) {
                // 服务端校验：只接受已注册实体类型，去重，上限 32
                Set<Identifier> valid = new LinkedHashSet<>();
                for (Identifier id : payload.ids) {
                    if (valid.size() >= 32) {
                        break;
                    }
                    if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                        valid.add(id);
                    }
                }
                core.setWhitelist(valid);
            }
        });
        ctx.setPacketHandled(true);
    }
}
