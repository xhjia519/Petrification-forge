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
import net.minecraftforge.eventbus.api.bus.BusGroup;
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
                    .displayItems((_, output) -> {
                        output.accept(ModItems.STATUE_BLOCK_ITEM.get());
                        output.accept(ModItems.RESTORATION_POWDER.get());
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

    public static void register(BusGroup modBusGroup) {
        CREATIVE_TABS.register(modBusGroup);
    }

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModItems.STATUE_BLOCK_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.RESTORATION_POWDER);
        }
    }
}
