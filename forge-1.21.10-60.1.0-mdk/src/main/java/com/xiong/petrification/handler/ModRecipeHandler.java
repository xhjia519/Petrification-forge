package com.xiong.petrification.handler;

import com.mojang.serialization.MapCodec;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.registry.ModDataComponents;
import com.xiong.petrification.registry.ModItems;
import com.xiong.petrification.registry.ModPotions;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Recipe;
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

    public static final RegistryObject<RecipeSerializer<RestorationPowderRecipe>> RESTORATION_POWDER =
            RECIPE_SERIALIZERS.register("restoration_powder", () -> RestorationPowderRecipe.SERIALIZER);

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

    /** 1.21.11 的 RecipeSerializer 是接口：用匿名实现组装 codec + streamCodec。 */
    private static <T extends Recipe<?>> RecipeSerializer<T> codecSerializer(
            MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> stream) {
        return new RecipeSerializer<T>() {
            @Override
            public MapCodec<T> codec() {
                return codec;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
                return stream;
            }
        };
    }

    public static class StatueDecomposeRecipe extends CustomRecipe {
        public static final StatueDecomposeRecipe INSTANCE = new StatueDecomposeRecipe();
        public static final MapCodec<StatueDecomposeRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, StatueDecomposeRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        public static final RecipeSerializer<StatueDecomposeRecipe> SERIALIZER = codecSerializer(MAP_CODEC, STREAM_CODEC);

        private StatueDecomposeRecipe() {
            super(CraftingBookCategory.MISC);
        }

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
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
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
        public static final RecipeSerializer<StatueComposeRecipe> SERIALIZER = codecSerializer(MAP_CODEC, STREAM_CODEC);

        private StatueComposeRecipe() {
            super(CraftingBookCategory.MISC);
        }

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
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
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

    /**
     * 还原粉配方：六种粉末 + 红石 + 火药 + 下界之星无序合成，产出 8 个还原粉。
     * 下界之星作为催化剂，合成后不消耗（本版本的 Ingredient JSON 不支持 remainders 字段，
     * 因此仿照上面两个雕像配方，用自定义配方重写 getRemainingItems 来保留下界之星）。
     */
    public static class RestorationPowderRecipe extends CustomRecipe {
        public static final RestorationPowderRecipe INSTANCE = new RestorationPowderRecipe();
        public static final MapCodec<RestorationPowderRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, RestorationPowderRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        public static final RecipeSerializer<RestorationPowderRecipe> SERIALIZER = codecSerializer(MAP_CODEC, STREAM_CODEC);

        private RestorationPowderRecipe() {
            super(CraftingBookCategory.MISC);
        }

        // 无序合成：九种材料各一个，位置不限
        @Override
        public boolean matches(CraftingInput input, Level level) {
            if (input.ingredientCount() != 9) {
                return false;
            }
            // 统计合成栏中每种所需材料的数量，全部恰好为 1 才算匹配
            int turtleScute = 0, sulfur = 0, prismarine = 0, lapis = 0, shulker = 0, amethyst = 0;
            int netherStar = 0, redstone = 0, gunpowder = 0;
            for (int i = 0; i < input.size(); i++) {
                ItemStack stack = input.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (stack.is(ModItems.TURTLE_SCUTE_POWDER.get())) turtleScute++;
                else if (stack.is(ModItems.SULFUR_POWDER.get())) sulfur++;
                else if (stack.is(ModItems.PRISMARINE_SHARD_POWDER.get())) prismarine++;
                else if (stack.is(ModItems.LAPIS_LAZULI_POWDER.get())) lapis++;
                else if (stack.is(ModItems.SHULKER_SHELL_POWDER.get())) shulker++;
                else if (stack.is(ModItems.AMETHYST_POWDER.get())) amethyst++;
                else if (stack.is(Items.NETHER_STAR)) netherStar++;
                else if (stack.is(Items.REDSTONE)) redstone++;
                else if (stack.is(Items.GUNPOWDER)) gunpowder++;
                else return false; // 出现任何无关材料，直接不匹配
            }
            return turtleScute == 1 && sulfur == 1 && prismarine == 1 && lapis == 1
                    && shulker == 1 && amethyst == 1 && netherStar == 1
                    && redstone == 1 && gunpowder == 1;
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            return new ItemStack(ModItems.RESTORATION_POWDER.get(), 8);
        }

        // 下界之星作为催化剂保留在合成栏中，其余材料正常消耗
        @Override
        public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
            NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < input.size(); slot++) {
                ItemStack stack = input.getItem(slot);
                if (stack.is(Items.NETHER_STAR)) {
                    result.set(slot, stack.copy());
                }
            }
            return result;
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
