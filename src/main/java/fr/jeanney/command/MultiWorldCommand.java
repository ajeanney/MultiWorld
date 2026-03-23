package fr.jeanney.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.jeanney.MultiWorld;
import fr.jeanney.Utils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static fr.jeanney.CustomServerWorld.getBaseWorldName;
import static fr.jeanney.MultiWorld.*;
import static net.minecraft.commands.Commands.literal;

public final class MultiWorldCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        LiteralArgumentBuilder<CommandSourceStack> rootCommand = literal("mw")
            .executes(ctx -> {
                showHelp(ctx.getSource());
                return 1;
            });

        rootCommand.then(literal("tp")
            .then(Commands.argument("world", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(getAvailableWorlds(ctx.getSource()), builder))
                .executes(ctx -> {
                    String worldName = StringArgumentType.getString(ctx, "world");
                    return teleportCommand(ctx.getSource(), worldName);
                })
            )
        );

        rootCommand.then(literal("import")
            .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .then(Commands.argument("folderPath", StringArgumentType.string())
                .then(Commands.argument("worldName", StringArgumentType.word())
                    .executes(ctx -> {
                        String folderPath = StringArgumentType.getString(ctx, "folderPath");
                        String worldName = StringArgumentType.getString(ctx, "worldName");
                        return importCommand(ctx.getSource(), folderPath, worldName);
                    })
                )
            )
        );

        rootCommand.then(literal("create")
            .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .then(Commands.argument("worldName", StringArgumentType.word())
                .executes(ctx -> {
                    String worldName = StringArgumentType.getString(ctx, "worldName");
                    long seed = new Random().nextLong();
                    return createCommand(ctx.getSource(), worldName, "normal", "normal", seed);
                })
                .then(Commands.argument("preset", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestPresets(builder))
                    .executes(ctx -> {
                        String worldName = StringArgumentType.getString(ctx, "worldName");
                        String preset = StringArgumentType.getString(ctx, "preset");
                        long seed = new Random().nextLong();
                        return createCommand(ctx.getSource(), worldName, preset, "normal", seed);
                    })
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestModes(builder))
                        .executes(ctx -> {
                            String worldName = StringArgumentType.getString(ctx, "worldName");
                            String preset = StringArgumentType.getString(ctx, "preset");
                            String mode = StringArgumentType.getString(ctx, "mode");
                            long seed = new Random().nextLong();
                            return createCommand(ctx.getSource(), worldName, preset, mode, seed);
                        })
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                            .executes(ctx -> {
                                String worldName = StringArgumentType.getString(ctx, "worldName");
                                String preset = StringArgumentType.getString(ctx, "preset");
                                String mode = StringArgumentType.getString(ctx, "mode");
                                long seed = LongArgumentType.getLong(ctx, "seed");
                                return createCommand(ctx.getSource(), worldName, preset, mode, seed);
                            })
                        )
                        .then(Commands.argument("seedText", StringArgumentType.word())
                            .executes(ctx -> {
                                String worldName = StringArgumentType.getString(ctx, "worldName");
                                String preset = StringArgumentType.getString(ctx, "preset");
                                String mode = StringArgumentType.getString(ctx, "mode");
                                long seed = parseSeed(StringArgumentType.getString(ctx, "seedText"));
                                return createCommand(ctx.getSource(), worldName, preset, mode, seed);
                            })
                        )
                    )
                )
            )
        );

        rootCommand.then(literal("delete")
            .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .then(Commands.argument("worldName", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                    getAvailableCustomWorlds(ctx.getSource()),
                    builder
                ))
                .executes(ctx -> {
                    String worldName = StringArgumentType.getString(ctx, "worldName");
                    return deleteCommand(ctx.getSource(), worldName);
                })
            )
        );

        dispatcher.register(rootCommand);
    }

    private static void showHelp(CommandSourceStack source) {
        boolean isAdmin = Commands.hasPermission(Commands.LEVEL_OWNERS).test(source);

        source.sendSystemMessage(Component.literal("§6MultiWorld Commands:"));
        source.sendSystemMessage(Component.literal("§7/mw tp <world> §8- §7Teleport to a world"));

        if (isAdmin) {
            source.sendSystemMessage(Component.literal("§7/mw import <folder> <name> §8- §7Import world from folder"));
            source.sendSystemMessage(Component.literal("§7/mw create <name> [preset] [mode] [seed] §8- §7Create a new world"));
            source.sendSystemMessage(Component.literal("§7/mw delete <name> §8- §7Delete a world"));
        }
    }

    private static java.util.List<String> getAvailableWorlds(CommandSourceStack source) {
        return source.levels().stream()
                .map(ResourceKey::identifier)
                .filter(id -> id.getNamespace().equals(NAMESPACE) || id.getNamespace().equals("minecraft"))
                .filter(id -> !id.getPath().endsWith("_nether") && !id.getPath().endsWith("_end"))
                .map(Identifier::getPath)
                .filter(path -> !path.equals(getBaseWorldName(getSourceWorldName(source))))
                .collect(Collectors.toList());
    }

    private static java.util.List<String> getAvailableCustomWorlds(CommandSourceStack source) {
        return source.levels().stream()
                .map(ResourceKey::identifier)
                .filter(id -> id.getNamespace().equals(NAMESPACE))
                .filter(id -> !id.getPath().endsWith("_nether") && !id.getPath().endsWith("_end"))
                .map(Identifier::getPath)
                .collect(Collectors.toList());
    }

    private static CompletableFuture<Suggestions> suggestPresets(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(new String[]{"normal", "flat"}, builder);
    }

    private static CompletableFuture<Suggestions> suggestModes(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(new String[]{"normal", "creative"}, builder);
    }

    private static long parseSeed(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return (long) value.hashCode();
        }
    }

    private static int teleportCommand(CommandSourceStack source, String worldString) {
        worldString = getBaseWorldName(worldString);
        ServerLevel worldTarget = source.getServer().overworld();
        if (!worldString.equals("overworld")) {
            ResourceKey<Level> worldKey = ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, worldString)
            );
            worldTarget = source.getServer().getLevel(worldKey);
        }

        if (worldTarget == null) {
            source.sendFailure(Component.literal("World " + worldString + " not found!"));
            return 0;
        }

        String sourceWorldName = getSourceWorldName(source);
        String targetWorldName = worldTarget.dimension().identifier().getPath();

        if (sourceWorldName.equals(targetWorldName)) {
            source.sendFailure(Component.literal("You are already in this world!"));
            return 0;
        }

        ServerPlayer player = source.getPlayer();

        Utils.saveInv(player, sourceWorldName);
        Utils.loadInv(player, targetWorldName);

        player.sendSystemMessage(Component.literal("Teleported to " + worldTarget.dimension().identifier() + " !"));

        return 1;
    }

    private static int importCommand(CommandSourceStack source, String folderPath, String worldName) {
        source.sendSystemMessage(Component.literal("Importing world from " + folderPath + " to " + worldName + "..."));

        try {
            if (!importWorld(source.getServer(), folderPath, worldName)) {
                source.sendFailure(Component.literal("Error importing world: invalid source folder or missing level.dat"));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("World imported successfully!"), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error importing world: " + e.getMessage()));
            LOGGER.error("Error importing world", e);
            return 0;
        }
    }

    private static int createCommand(CommandSourceStack source, String worldName, String preset, String mode, long seed) {
        ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, worldName));
        if (source.getServer().getLevel(worldKey) != null) {
            source.sendFailure(Component.literal("§cA world with this name already exists!"));
            return 0;
        }

        String normalizedPreset = preset.toLowerCase();
        if (!normalizedPreset.equals("normal") && !normalizedPreset.equals("flat")) {
            source.sendFailure(Component.literal("§cInvalid preset. Allowed values: normal, flat"));
            return 0;
        }

        String normalizedMode = mode.toLowerCase();
        if (!normalizedMode.equals("normal") && !normalizedMode.equals("creative")) {
            source.sendFailure(Component.literal("§cInvalid mode. Allowed values: normal, creative"));
            return 0;
        }

        MultiWorld.WorldMode worldMode = MultiWorld.WorldMode.fromString(normalizedMode);

        source.sendSystemMessage(Component.literal("Creating new world '" + worldName + "' with seed: " + seed + " (preset=" + normalizedPreset + ", mode=" + normalizedMode + ")"));

        try {
            createWorldWithDimensions(source.getServer(), worldName, seed, null, normalizedPreset, worldMode);
            source.sendSuccess(() -> Component.literal("§aWorld created successfully! Use §6/mw tp " + worldName + "§a to teleport to it."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError creating world: " + e.getMessage()));
            LOGGER.error("Error creating world", e);
            return 0;
        }
    }

    private static int deleteCommand(CommandSourceStack source, String worldName) {
        ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, worldName));
        if (source.getServer().getLevel(worldKey) == null) {
            source.sendFailure(Component.literal("§cWorld '" + worldName + "' not found!"));
            return 0;
        }

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            String currentWorld = getBaseWorldName(player.level().dimension().identifier().getPath());
            if (currentWorld.equals(worldName)) {
                Utils.saveInv(player, currentWorld);
                Utils.loadInv(player, "overworld");
                player.sendSystemMessage(Component.literal("§cWorld being deleted! You've been teleported to the overworld."));
            }
        }

        source.sendSystemMessage(Component.literal("§eDeleting world '" + worldName + "'..."));

        try {
            deleteWorld(source.getServer(), worldName);
            source.sendSuccess(() -> Component.literal("§aWorld '" + worldName + "' has been successfully deleted."), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError deleting world: " + e.getMessage()));
            LOGGER.error("Error deleting world", e);
            return 0;
        }
    }

    public static @NotNull String getSourceWorldName(CommandSourceStack source) {
        ServerLevel sourceWorld = source.getLevel();
        ResourceKey<Level> sourceWorldKey = sourceWorld.dimension() == Level.END || sourceWorld.dimension() == Level.NETHER ? Level.OVERWORLD : sourceWorld.dimension();
        String sourceWorldName = sourceWorldKey.identifier().getPath();
        return getBaseWorldName(sourceWorldName);
    }
}
