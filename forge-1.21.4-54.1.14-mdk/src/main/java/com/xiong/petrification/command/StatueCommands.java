package com.xiong.petrification.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.xiong.petrification.block.StatueBlockEntity;
import com.xiong.petrification.component.MaterialType;
import com.xiong.petrification.component.StatueComponent;
import com.xiong.petrification.registry.ModBlocks;
import com.xiong.petrification.util.MaterializationHelper;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * /petrification give &lt;entity&gt; [material] [count] - hand out a statue item of the given mob
 * /petrification setblock &lt;pos&gt; &lt;entity&gt; [material] - place a statue block of the given mob
 */
public final class StatueCommands {
    private StatueCommands() {}

    private static final SimpleCommandExceptionType ERROR_FAILED =
            new SimpleCommandExceptionType(Component.translatable("commands.petrification.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("petrification")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(buildGive(context))
                .then(buildSetBlock(context)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildGive(CommandBuildContext context) {
        var entity = Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE));
        entity.suggests(SuggestionProviders.SUMMONABLE_ENTITIES);
        entity.executes(c -> give(c, ResourceArgument.getEntityType(c, "entity"), MaterialType.STONE, 1));

        var material = Commands.argument("material", StringArgumentType.word());
        material.suggests((c, b) -> SharedSuggestionProvider.suggest(materialNames(), b));
        material.executes(c -> give(c, ResourceArgument.getEntityType(c, "entity"), material(c), 1));

        var count = Commands.argument("count", IntegerArgumentType.integer(1, 16));
        count.executes(c -> give(c, ResourceArgument.getEntityType(c, "entity"),
                material(c), IntegerArgumentType.getInteger(c, "count")));

        return Commands.literal("give").then(entity.then(material.then(count)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetBlock(CommandBuildContext context) {
        var pos = Commands.argument("pos", BlockPosArgument.blockPos());

        var entity = Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE));
        entity.suggests(SuggestionProviders.SUMMONABLE_ENTITIES);
        entity.executes(c -> setBlock(c, BlockPosArgument.getLoadedBlockPos(c, "pos"),
                ResourceArgument.getEntityType(c, "entity"), MaterialType.STONE));

        var material = Commands.argument("material", StringArgumentType.word());
        material.suggests((c, b) -> SharedSuggestionProvider.suggest(materialNames(), b));
        material.executes(c -> setBlock(c, BlockPosArgument.getLoadedBlockPos(c, "pos"),
                ResourceArgument.getEntityType(c, "entity"), material(c)));

        return Commands.literal("setblock").then(pos.then(entity.then(material)));
    }

    private static String[] materialNames() {
        return Arrays.stream(MaterialType.values()).map(MaterialType::getSerializedName).toArray(String[]::new);
    }

    private static MaterialType material(CommandContext<CommandSourceStack> context) {
        return MaterialType.fromEffectId(StringArgumentType.getString(context, "material"));
    }

    private static int give(CommandContext<CommandSourceStack> context, Holder.Reference<EntityType<?>> typeRef,
                            MaterialType material, int count) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        ItemStack stack = MaterializationHelper.createStatueItem(level, typeRef.value(), material);
        if (stack.isEmpty()) {
            throw ERROR_FAILED.create();
        }
        stack.setCount(count);

        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.petrification.give.success",
                typeRef.value().getDescription(), count), true);
        return count;
    }

    private static int setBlock(CommandContext<CommandSourceStack> context, BlockPos pos,
                                Holder.Reference<EntityType<?>> typeRef, MaterialType material) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        StatueComponent component = MaterializationHelper.createStatueComponent(level, typeRef.value(), material);
        if (component == null) {
            throw ERROR_FAILED.create();
        }

        level.setBlock(pos, ModBlocks.STATUE_BLOCK.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof StatueBlockEntity blockEntity) {
            blockEntity.setStatueData(component);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.petrification.setblock.success",
                typeRef.value().getDescription(), pos.toShortString()), true);
        return 1;
    }
}
