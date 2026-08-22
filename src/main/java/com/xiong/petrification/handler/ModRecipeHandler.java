package com.xiong.petrification.handler;

import com.mojang.serialization.MapCodec;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.registry.ModDataComponents;
import com.xiong.petrification.registry.ModItems;
import com.xiong.petrification.registry.ModPotions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeHandler {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, com.xiong.petrification.PetrificationMod.MODID);

    public static final RegistryObject<RecipeSerializer<StatueDecomposeRecipe>> DECOMPOSE =
            RECIPE_SERIALIZERS.register("statue_decompose", () -> StatueDecomposeRecipe.SERIALIZER);

    public static final RegistryObject<RecipeSerializer<StatueComposeRecipe>> COMPOSE =
            RECIPE_SERIALIZERS.register("statue_compose", () -> StatueComposeRecipe.SERIALIZER);

    public static void register(net.minecraftforge.eventbus.api.bus.BusGroup modBusGroup) {
        RECIPE_SERIALIZERS.register(modBusGroup);
    }

    public static void registerBrewing() {
        BrewingRecipeRegisterEvent.BUS.addListener(ModRecipeHandler::onBrewingRegister);
    }

    private static void onBrewingRegister(BrewingRecipeRegisterEvent event) {
        registerBrewingRecipe(event, Items.COBBLESTONE, ModPotions.STONE);
        registerBrewingRecipe(event, Items.IRON_INGOT, ModPotions.IRON);
        registerBrewingRecipe(event, Items.COPPER_INGOT, ModPotions.COPPER);
        registerBrewingRecipe(event, Items.GOLD_INGOT, ModPotions.GOLD);
        registerBrewingRecipe(event, Items.DIAMOND, ModPotions.DIAMOND);
        registerBrewingRecipe(event, Items.NETHERITE_INGOT, ModPotions.NETHERITE);
        registerBrewingRecipe(event, ModItems.RESTORATION_POWDER.get(), ModPotions.RESTORATION);
    }

    private static void registerBrewingRecipe(BrewingRecipeRegisterEvent event, net.minecraft.world.item.Item ingredient,
                                              RegistryObject<Potion> output) {
        event.getBuilder().addMix(
                Potions.AWKWARD,
                ingredient,
                output.getHolder().orElseThrow()
        );
    }

    public static class StatueDecomposeRecipe extends CustomRecipe {
        public static final StatueDecomposeRecipe INSTANCE = new StatueDecomposeRecipe();
        public static final MapCodec<StatueDecomposeRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, StatueDecomposeRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        public static final RecipeSerializer<StatueDecomposeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

        @Override
        public boolean matches(CraftingInput input, Level level) {
            if (input.ingredientCount() != 1) {
                return false;
            }
            for (int i = 0; i < input.size(); i++) {
                ItemStack stack = input.getItem(i);
                if (!stack.isEmpty()) {
                    return stack.is(ModItems.STATUE_BLOCK_ITEM.get()) && stack.has(ModDataComponents.STATUE_DATA.get());
                }
            }
            return false;
        }

        @Override
        public ItemStack assemble(CraftingInput input) {
            for (int i = 0; i < input.size(); i++) {
                ItemStack stack = input.getItem(i);
                if (!stack.isEmpty() && stack.has(ModDataComponents.STATUE_DATA.get())) {
                    StatueComponent component = stack.get(ModDataComponents.STATUE_DATA.get());
                    return new ItemStack(component.materialType().getIngotItem(), 9);
                }
            }
            return ItemStack.EMPTY;
        }

        public boolean canCraftInDimensions(int width, int height) {
            return width * height >= 1;
        }

        @Override
        public RecipeSerializer<? extends CustomRecipe> getSerializer() {
            return SERIALIZER;
        }
    }

    public static class StatueComposeRecipe extends CustomRecipe {
        public static final StatueComposeRecipe INSTANCE = new StatueComposeRecipe();
        public static final MapCodec<StatueComposeRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, StatueComposeRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        public static final RecipeSerializer<StatueComposeRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

        @Override
        public boolean matches(CraftingInput input, Level level) {
            if (input.ingredientCount() != 9) {
                return false;
            }
            MaterialType found = null;
            for (int i = 0; i < input.size(); i++) {
                ItemStack stack = input.getItem(i);
                if (stack.isEmpty()) {
                    return false;
                }
                MaterialType type = getMaterialFromItem(stack);
                if (type == null) {
                    return false;
                }
                if (found == null) {
                    found = type;
                } else if (found != type) {
                    return false;
                }
            }
            return found != null;
        }

        @Override
        public ItemStack assemble(CraftingInput input) {
            MaterialType type = null;
            for (int i = 0; i < input.size(); i++) {
                ItemStack stack = input.getItem(i);
                type = getMaterialFromItem(stack);
                if (type != null) {
                    break;
                }
            }
            if (type == null) {
                return ItemStack.EMPTY;
            }
            ItemStack result = new ItemStack(ModItems.STATUE_BLOCK_ITEM.get());
            result.set(ModDataComponents.STATUE_DATA.get(), StatueComponent.defaultStatue(type));
            return result;
        }

        private MaterialType getMaterialFromItem(ItemStack stack) {
            for (MaterialType type : MaterialType.values()) {
                if (stack.is(type.getIngotItem())) {
                    return type;
                }
            }
            return null;
        }

        public boolean canCraftInDimensions(int width, int height) {
            return width >= 3 && height >= 3;
        }

        @Override
        public RecipeSerializer<? extends CustomRecipe> getSerializer() {
            return SERIALIZER;
        }
    }
}
