package com.xiong.petrification.registry;

import com.xiong.petrification.platform.Platform;
import com.xiong.petrification.platform.Registrar;
import com.xiong.petrification.platform.RegistryEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ModCreativeTabs {
    /**
     * 原版创造标签页的键。
     * 原版 {@code CreativeModeTabs} 的常量是 private，这里按同一标识重建，便于在两个加载器里比较。
     */
    public static final ResourceKey<CreativeModeTab> VANILLA_BUILDING_BLOCKS = vanillaTab("building_blocks");
    public static final ResourceKey<CreativeModeTab> VANILLA_TOOLS_AND_UTILITIES = vanillaTab("tools_and_utilities");
    public static final ResourceKey<CreativeModeTab> VANILLA_INGREDIENTS = vanillaTab("ingredients");

    public static final Registrar<CreativeModeTab> CREATIVE_TABS = Platform.registrar(Registries.CREATIVE_MODE_TAB);

    /**
     * 本模组的创造标签页。
     * <p>
     * 原版 {@code CreativeModeTab.Output} 与 {@code TabVisibility} 是 protected 内部类型，
     * 公共模块（只编译于原版 jar）无法直接书写 displayItems 的 lambda，因此由加载器模块
     * 调用 {@link #registerTab(Supplier)} 传入构建好的标签页；条目内容统一由
     * {@link #fillTabContents(Consumer)} 提供，避免两侧重复维护物品清单。
     */
    public static RegistryEntry<CreativeModeTab> PETRIFICATION_TAB;

    /** 标签页标题。 */
    public static Component tabTitle() {
        return Component.translatable("itemGroup.petrification");
    }

    /** 标签页图标。 */
    public static ItemStack tabIcon() {
        return createPotionStack(ModPotions.COPPER);
    }

    /** 由加载器模块在 bootstrap 之前调用，注册本模组的创造标签页。 */
    public static void registerTab(Supplier<CreativeModeTab> factory) {
        PETRIFICATION_TAB = CREATIVE_TABS.register("petrification", factory);
    }

    /** 本模组标签页的全部条目（加载器在 displayItems 中回调）。 */
    public static void fillTabContents(Consumer<ItemStack> output) {
        output.accept(new ItemStack(ModItems.STATUE_BLOCK_ITEM.get()));
        output.accept(new ItemStack(ModItems.RESTORATION_POWDER.get()));
        // 六种稀有材料粉末
        output.accept(new ItemStack(ModItems.TURTLE_SCUTE_POWDER.get()));
        output.accept(new ItemStack(ModItems.PRISMARINE_SHARD_POWDER.get()));
        output.accept(new ItemStack(ModItems.SULFUR_POWDER.get()));
        output.accept(new ItemStack(ModItems.SHULKER_SHELL_POWDER.get()));
        output.accept(new ItemStack(ModItems.LAPIS_LAZULI_POWDER.get()));
        output.accept(new ItemStack(ModItems.AMETHYST_POWDER.get()));
        // 建筑压缩模块
        output.accept(new ItemStack(ModItems.STRUCTURE_CORE.get()));
        output.accept(new ItemStack(ModItems.MINI_STRUCTURE_BLOCK_ITEM.get()));
        // 石化核心
        output.accept(new ItemStack(ModItems.PETRIFICATION_CORE_BLOCK_ITEM.get()));
        // 还原方块
        output.accept(new ItemStack(ModItems.RESTORATION_BLOCK_ITEM.get()));
        // 全部药水变体（普通 / 喷溅 / 滞留 / 药箭）
        addPotionVariants(output, ModPotions.STONE);
        addPotionVariants(output, ModPotions.IRON);
        addPotionVariants(output, ModPotions.COPPER);
        addPotionVariants(output, ModPotions.GOLD);
        addPotionVariants(output, ModPotions.DIAMOND);
        addPotionVariants(output, ModPotions.NETHERITE);
        addPotionVariants(output, ModPotions.RESTORATION);
    }

    private static ResourceKey<CreativeModeTab> vanillaTab(String path) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", path));
    }

    private static ItemStack potionStack(net.minecraft.world.item.Item item, RegistryEntry<Potion> potion) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion.holder()));
        return stack;
    }

    private static ItemStack createPotionStack(RegistryEntry<Potion> potion) {
        return potionStack(Items.POTION, potion);
    }

    private static void addPotionVariants(Consumer<ItemStack> output, RegistryEntry<Potion> potion) {
        output.accept(createPotionStack(potion));
        output.accept(potionStack(Items.SPLASH_POTION, potion));
        output.accept(potionStack(Items.LINGERING_POTION, potion));
        output.accept(potionStack(Items.TIPPED_ARROW, potion));
    }

    private ModCreativeTabs() {}

    public static void register() {
        CREATIVE_TABS.bind();
    }

    /**
     * 把模组物品补充进原版创造标签页。
     * 各加载器的 BuildCreativeModeTabContentsEvent#accept 签名不同，因此只暴露通用的 ItemLike 消费者。
     */
    public static void fillVanillaTab(ResourceKey<CreativeModeTab> tabKey, Consumer<ItemLike> acceptor) {
        if (tabKey.equals(VANILLA_BUILDING_BLOCKS)) {
            acceptor.accept(ModItems.STATUE_BLOCK_ITEM.get());
            // 微缩建筑方块也放入建筑方块标签页，方便玩家查看
            acceptor.accept(ModItems.MINI_STRUCTURE_BLOCK_ITEM.get());
            // 石化核心属于功能性方块
            acceptor.accept(ModItems.PETRIFICATION_CORE_BLOCK_ITEM.get());
            // 还原方块也属于功能性方块
            acceptor.accept(ModItems.RESTORATION_BLOCK_ITEM.get());
        }
        if (tabKey.equals(VANILLA_TOOLS_AND_UTILITIES)) {
            // 压缩核心属于工具类道具
            acceptor.accept(ModItems.STRUCTURE_CORE.get());
        }
        if (tabKey.equals(VANILLA_INGREDIENTS)) {
            acceptor.accept(ModItems.RESTORATION_POWDER.get());
            // 将稀有材料粉末也放入原料标签页，方便玩家查看
            acceptor.accept(ModItems.TURTLE_SCUTE_POWDER.get());
            acceptor.accept(ModItems.PRISMARINE_SHARD_POWDER.get());
            acceptor.accept(ModItems.SULFUR_POWDER.get());
            acceptor.accept(ModItems.SHULKER_SHELL_POWDER.get());
            acceptor.accept(ModItems.LAPIS_LAZULI_POWDER.get());
            acceptor.accept(ModItems.AMETHYST_POWDER.get());
        }
    }
}
