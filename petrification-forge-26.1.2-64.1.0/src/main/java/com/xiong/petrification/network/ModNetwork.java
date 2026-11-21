package com.xiong.petrification.network;

import com.xiong.petrification.PetrificationMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

public final class ModNetwork {
    /** 主通道：同时承载客户端 → 服务端与服务端 → 客户端方向的载荷。 */
    public static final Channel CHANNEL;
    /** 服务端 → 客户端方向（与 CHANNEL 同一实例，保留以兼容既有调用点）。 */
    public static final Channel CHANNEL_TO_CLIENT;

    static {
        // 注意：整个 play() 中只能 build() 一次！每次 build 都会在同一个
        // NetworkInstance 上新增一个监听器；若 serverbound/clientbound 各 build 一次，
        // 收包时两个监听器会对同一缓冲区重复解码（第一个成功把 readerIndex 前进 8，
        // 第二个再读就越界 → 网络线程崩溃断线）。两个方向的包注册到同一 Channel 即可。
        Channel[] holder = new Channel[1];
        ChannelBuilder.named(Identifier.fromNamespaceAndPath(PetrificationMod.MODID, "main"))
                .networkProtocolVersion(1)
                .payloadChannel()
                .play(protocol -> {
                    PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow = protocol.serverbound();
                    flow.add(CoreSetRadiusPayload.TYPE, CoreSetRadiusPayload.STREAM_CODEC, CoreSetRadiusPayload::handle);
                    flow.add(CoreSetWhitelistPayload.TYPE, CoreSetWhitelistPayload.STREAM_CODEC, CoreSetWhitelistPayload::handle);
                    flow.add(CoreSetAffectPlayersPayload.TYPE, CoreSetAffectPlayersPayload.STREAM_CODEC, CoreSetAffectPlayersPayload::handle);
                    flow.add(CoreActivatePayload.TYPE, CoreActivatePayload.STREAM_CODEC, CoreActivatePayload::handle);
                    flow.add(CoreDeactivatePayload.TYPE, CoreDeactivatePayload.STREAM_CODEC, CoreDeactivatePayload::handle);
                    flow.add(CoreSetRedstonePayload.TYPE, CoreSetRedstonePayload.STREAM_CODEC, CoreSetRedstonePayload::handle);
                    flow.add(RestorationSetRadiusPayload.TYPE, RestorationSetRadiusPayload.STREAM_CODEC, RestorationSetRadiusPayload::handle);
                    flow.add(RestorationSetRedstonePayload.TYPE, RestorationSetRedstonePayload.STREAM_CODEC, RestorationSetRedstonePayload::handle);
                    PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flowToClient = protocol.clientbound();
                    flowToClient.add(GuiPosPayload.TYPE, GuiPosPayload.STREAM_CODEC, GuiPosPayload::handle);
                    // serverbound / clientbound 两个 flow 共享同一 Builder 注册表，
                    // 任意 flow 上 build() 一次即可拿到包含全部消息的 Channel。
                    holder[0] = flow.build();
                });
        CHANNEL = holder[0];
        CHANNEL_TO_CLIENT = holder[0];
    }

    private ModNetwork() {}

    public static void register() {
        // Channel 在类加载静态块中构建；此钩子保留以兼容现有调用点（commonSetup）
    }

    /** 客户端 → 服务端。 */
    public static <T extends CustomPacketPayload> void sendToServer(T payload) {
        CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
    }

    /** 服务端 → 指定客户端。 */
    public static <T extends CustomPacketPayload> void sendToClient(T payload, ServerPlayer player) {
        CHANNEL_TO_CLIENT.send(payload, PacketDistributor.PLAYER.with(player));
    }
}
