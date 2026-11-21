package com.xiong.petrification.network;

import com.xiong.petrification.PetrificationMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

public final class ModNetwork {
    public static final Channel CHANNEL;

    static {
        // PayloadFlow 经 ChannelBuildable 才有 build()；play() 的 consumer 内拿到 flow 后
        // 通过数组逃逸，供后续 send 使用（发送方向：serverbound）。
        Channel[] holder = new Channel[1];
        ChannelBuilder.named(Identifier.fromNamespaceAndPath(PetrificationMod.MODID, "main"))
                .networkProtocolVersion(1)
                .payloadChannel()
                .play(protocol -> {
                    PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow = protocol.serverbound();
                    flow.add(CoreSetRadiusPayload.TYPE, CoreSetRadiusPayload.STREAM_CODEC, CoreSetRadiusPayload::handle);
                    flow.add(CoreSetWhitelistPayload.TYPE, CoreSetWhitelistPayload.STREAM_CODEC, CoreSetWhitelistPayload::handle);
                    flow.add(CoreSetAffectPlayersPayload.TYPE, CoreSetAffectPlayersPayload.STREAM_CODEC, CoreSetAffectPlayersPayload::handle);
                    holder[0] = flow.build();
                });
        CHANNEL = holder[0];
    }

    private ModNetwork() {}

    public static void register() {
        // Channel 在类加载静态块中构建；此钩子保留以兼容现有调用点（commonSetup）
    }

    /** 客户端 → 服务端。 */
    public static <T extends CustomPacketPayload> void sendToServer(T payload) {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }
}
