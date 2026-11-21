package com.xiong.petrification.registry;

import com.xiong.petrification.PetrificationMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PetrificationMod.MODID);

    public static final RegistryObject<CreativeModeTab> PETRIFICATION_TAB = CREATIVE_TABS.register("petrification",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.petrification"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> createPotionStack(ModPotions.COPPER))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.STATUE_BLOCK_ITEM.get());
                        output.accept(ModItems.RESTORATION_POWDER.get());
                        // 六种稀有材料粉末
                        output.accept(ModItems.TURTLE_SCUTE_POWDER.get());
                        output.accept(ModItems.PRISMARINE_SHARD_POWDER.get());
                        output.accept(ModItems.SULFUR_POWDER.get());
                        output.accept(ModItems.SHULKER_SHELL_POWDER.get());
                        output.accept(ModItems.LAPIS_LAZULI_POWDER.get());
                        output.accept(ModItems.AMETHYST_POWDER.get());
                        // 建筑压缩模块
                        output.accept(ModItems.STRUCTURE_CORE.get());
                        output.accept(ModItems.MINI_STRUCTURE_BLOCK_ITEM.get());
                        // 石化核心
                        output.accept(ModItems.PETRIFICATION_CORE_BLOCK_ITEM.get());
                        // 还原方块
                        output.accept(ModItems.RESTORATION_BLOCK_ITEM.get());
                        // Add all 6 potion types (normal, splash, lingering)
                        addPotionVariants(output, ModPotions.STONE);
                        addPotionVariants(output, ModPotions.IRON);
                        addPotionVariants(output, ModPotions.COPPER);
                        addPotionVariants(output, ModPotions.GOLD);
                        addPotionVariants(output, ModPotions.DIAMOND);
                        addPotionVariants(output, ModPotions.NETHERITE);
                        addPotionVariants(output, ModPotions.RESTORATION);
                    })
                    .build());

    private static ItemStack createPotionStack(RegistryObject<net.minecraft.world.item.alchemy.Potion> potion) {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new PotionContents(potion.getHolder().orElseThrow()));
        return stack;
    }

    private static ItemStack createSplashPotionStack(RegistryObject<net.minecraft.world.item.alchemy.Potion> potion) {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new PotionContents(potion.getHolder().orElseThrow()));
        return stack;
    }

    private static ItemStack createLingeringPotionStack(RegistryObject<net.minecraft.world.item.alchemy.Potion> potion) {
        ItemStack stack = new ItemStack(Items.LINGERING_POTION);
        stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new PotionContents(potion.getHolder().orElseThrow()));
        return stack;
    }

    private static ItemStack createTippedArrowStack(RegistryObject<net.minecraft.world.item.alchemy.Potion> potion) {
        ItemStack stack = new ItemStack(Items.TIPPED_ARROW);
        stack.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new PotionContents(potion.getHolder().orElseThrow()));
        return stack;
    }

    private static void addPotionVariants(CreativeModeTab.Output output,
                                          RegistryObject<net.minecraft.world.item.alchemy.Potion> potion) {
        output.accept(createPotionStack(potion));
        output.accept(createSplashPotionStack(potion));
        output.accept(createLingeringPotionStack(potion));
        output.accept(createTippedArrowStack(potion));
    }

    private ModCreativeTabs() {}

    public static void register(IEventBus modBus) {
        CREATIVE_TABS.register(modBus);
    }

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModItems.STATUE_BLOCK_ITEM);
            // 微缩建筑方块也放入建筑方块标签页，方便玩家查看
            event.accept(ModItems.MINI_STRUCTURE_BLOCK_ITEM);
            // 石化核心属于功能性方块
            event.accept(ModItems.PETRIFICATION_CORE_BLOCK_ITEM);
            // 还原方块也属于功能性方块
            event.accept(ModItems.RESTORATION_BLOCK_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            // 压缩核心属于工具类道具
            event.accept(ModItems.STRUCTURE_CORE);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.RESTORATION_POWDER);
            // 将稀有材料粉末也放入原料标签页，方便玩家查看
            event.accept(ModItems.TURTLE_SCUTE_POWDER);
            event.accept(ModItems.PRISMARINE_SHARD_POWDER);
            event.accept(ModItems.SULFUR_POWDER);
            event.accept(ModItems.SHULKER_SHELL_POWDER);
            event.accept(ModItems.LAPIS_LAZULI_POWDER);
            event.accept(ModItems.AMETHYST_POWDER);
        }
    }
}
