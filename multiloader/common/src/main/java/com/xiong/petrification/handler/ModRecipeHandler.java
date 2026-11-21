package com.xiong.petrification.handler;

import com.mojang.serialization.MapCodec;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.platform.Platform;
import com.xiong.petrification.platform.Registrar;
import com.xiong.petrification.platform.RegistryEntry;
import com.xiong.petrification.registry.ModDataComponents;
import com.xiong.petrification.registry.ModItems;
import com.xiong.petrification.registry.ModPotions;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ModRecipeHandler {
    public static final Registrar<RecipeSerializer<?>> RECIPE_SERIALIZERS = Platform.registrar(Registries.RECIPE_SERIALIZER);

    public static final RegistryEntry<RecipeSerializer<StatueDecomposeRecipe>> DECOMPOSE =
            RECIPE_SERIALIZERS.register("statue_decompose", () -> StatueDecomposeRecipe.SERIALIZER);

    public static final RegistryEntry<RecipeSerializer<StatueComposeRecipe>> COMPOSE =
            RECIPE_SERIALIZERS.register("statue_compose", () -> StatueComposeRecipe.SERIALIZER);

    public static final RegistryEntry<RecipeSerializer<RestorationPowderRecipe>> RESTORATION_POWDER =
            RECIPE_SERIALIZERS.register("restoration_powder", () -> RestorationPowderRecipe.SERIALIZER);

    private ModRecipeHandler() {}

    public static void register() {
        RECIPE_SERIALIZERS.bind();
    }

    /** 酿造配方描述，由各加载器模块用自己的酿造注册 API 落地。 */
    public record BrewingMix(Item ingredient, Holder<Potion> output) {}

    public static List<BrewingMix> brewingMixes() {
        return List.of(
                new BrewingMix(Items.COBBLESTONE, ModPotions.STONE.holder()),
                new BrewingMix(Items.IRON_INGOT, ModPotions.IRON.holder()),
                new BrewingMix(Items.COPPER_INGOT, ModPotions.COPPER.holder()),
                new BrewingMix(Items.GOLD_INGOT, ModPotions.GOLD.holder()),
                new BrewingMix(Items.DIAMOND, ModPotions.DIAMOND.holder()),
                new BrewingMix(Items.NETHERITE_INGOT, ModPotions.NETHERITE.holder()),
                new BrewingMix(ModItems.RESTORATION_POWDER.get(), ModPotions.RESTORATION.holder())
        );
    }

    /** 酿造基底药水。 */
    public static Holder<Potion> brewingBase() {
        return Potions.AWKWARD;
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

    /**
     * 还原粉配方：六种粉末 + 红石 + 火药 + 下界之星无序合成，产出 8 个还原粉。
     * 下界之星作为催化剂，合成后不消耗（本版本的 Ingredient JSON 不支持 remainders 字段，
     * 因此仿照上面两个雕像配方，用自定义配方重写 getRemainingItems 来保留下界之星）。
     */
    public static class RestorationPowderRecipe extends CustomRecipe {
        public static final RestorationPowderRecipe INSTANCE = new RestorationPowderRecipe();
        public static final MapCodec<RestorationPowderRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, RestorationPowderRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
        public static final RecipeSerializer<RestorationPowderRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

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
        public ItemStack assemble(CraftingInput input) {
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
