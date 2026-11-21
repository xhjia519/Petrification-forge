package com.xiong.petrification.platform;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

/**
 * 与加载器无关的注册项句柄。
 * <p>各加载器分别用 DeferredRegister / RegistryObject（Forge）或
 * DeferredRegister / DeferredHolder（NeoForge）实现。</p>
 */
public interface RegistryEntry<T> extends Supplier<T> {
    /** 注册名（含命名空间）。 */
    Identifier id();

    /** 注册后的 Holder，仅在注册完成后可用。 */
    Holder<T> holder();

    boolean isPresent();
}
