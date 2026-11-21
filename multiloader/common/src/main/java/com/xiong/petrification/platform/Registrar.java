package com.xiong.petrification.platform;

import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;

import java.util.function.Supplier;

/** 单个注册表（block/item/effect/...）的注册器，由加载器提供实现。 */
public interface Registrar<T> {

    <R extends T> RegistryEntry<R> register(String name, Supplier<R> factory);

    /**
     * 把延迟注册器挂到加载器的注册总线上。
     * 必须在对应的加载器模组构造阶段调用（Forge/NeoForge 的 DeferredRegister#register）。
     */
    void bind();

    ResourceKey<? extends Registry<T>> registryKey();
}
