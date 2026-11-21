package com.xiong.petrification.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 跨加载器的网络门面：公共代码只通过这里发包，具体通道由加载器模块注入。
 */
public final class ModNetwork {
    /** 由加载器模块实现：注册通道 + 完成双向收发。 */
    public interface Backend {
        <T extends CustomPacketPayload> void sendToServer(T payload);

        <T extends CustomPacketPayload> void sendToClient(T payload, ServerPlayer player);
    }

    private static Backend backend;

    private ModNetwork() {}

    /** 加载器模块在模组初始化阶段安装自己的 {@link Backend}。 */
    public static void install(Backend implementation) {
        backend = implementation;
    }

    /** 客户端 → 服务端。 */
    public static <T extends CustomPacketPayload> void sendToServer(T payload) {
        backend.sendToServer(payload);
    }

    /** 服务端 → 指定客户端。 */
    public static <T extends CustomPacketPayload> void sendToClient(T payload, ServerPlayer player) {
        backend.sendToClient(payload, player);
    }
}
