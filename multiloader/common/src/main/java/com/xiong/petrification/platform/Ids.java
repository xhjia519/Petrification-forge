package com.xiong.petrification.platform;

import com.xiong.petrification.PetrificationMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** 统一构造资源标识与资源键，避免各加载器注册器差异扩散到业务代码。 */
public final class Ids {

    private Ids() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(PetrificationMod.MODID, path);
    }

    public static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String path) {
        return ResourceKey.create(registry, id(path));
    }

    public static ResourceKey<Block> block(String path) {
        return key(Registries.BLOCK, path);
    }

    public static ResourceKey<Item> item(String path) {
        return key(Registries.ITEM, path);
    }
}
