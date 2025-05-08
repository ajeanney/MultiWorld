package com.gmail.anthony17j.multiworld.command;

import com.gmail.anthony17j.multiworld.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.stream.Collectors;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getBaseWorldName;
import static com.gmail.anthony17j.multiworld.MultiWorld.*;
import static net.minecraft.server.command.CommandManager.literal;

public final class MultiWorldCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> rootCommand = literal("mw")
            .executes(ctx -> {
                showHelp(ctx.getSource());
                return 1;
            });

        rootCommand.then(literal("tp")
            .then(CommandManager.argument("world", StringArgumentType.word())
                .suggests((ctx, builder) -> CommandSource.suggestMatching(getAvailableWorlds(ctx.getSource()), builder))
                .executes(ctx -> {
                    String worldName = StringArgumentType.getString(ctx, "world");
                    return teleportCommand(ctx.getSource(), worldName);
                })
            )
        );

        rootCommand.then(literal("import")
            .requires(source -> source.hasPermissionLevel(4))
            .then(CommandManager.argument("folderPath", StringArgumentType.string())
                .then(CommandManager.argument("worldName", StringArgumentType.word())
                    .executes(ctx -> {
                        String folderPath = StringArgumentType.getString(ctx, "folderPath");
                        String worldName = StringArgumentType.getString(ctx, "worldName");
                        return importCommand(ctx.getSource(), folderPath, worldName);
                    })
                )
            )
        );

        // Ajout de la sous-commande "create" pour les administrateurs
        rootCommand.then(literal("create")
            .requires(source -> source.hasPermissionLevel(4))
            .then(CommandManager.argument("worldName", StringArgumentType.word())
                .executes(ctx -> {
                    String worldName = StringArgumentType.getString(ctx, "worldName");
                    // Utiliser une seed aléatoire si aucune n'est spécifiée
                    long seed = new Random().nextLong();
                    return createCommand(ctx.getSource(), worldName, seed);
                })
                .then(CommandManager.argument("seed", LongArgumentType.longArg())
                    .executes(ctx -> {
                        String worldName = StringArgumentType.getString(ctx, "worldName");
                        long seed = LongArgumentType.getLong(ctx, "seed");
                        return createCommand(ctx.getSource(), worldName, seed);
                    })
                )
            )
        );

        // Ajout de la sous-commande "delete" pour les administrateurs
        rootCommand.then(literal("delete")
            .requires(source -> source.hasPermissionLevel(4))
            .then(CommandManager.argument("worldName", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    // Suggérer seulement les mondes personnalisés, pas les mondes par défaut
                    return CommandSource.suggestMatching(
                        getAvailableCustomWorlds(ctx.getSource()),
                        builder
                    );
                })
                .executes(ctx -> {
                    String worldName = StringArgumentType.getString(ctx, "worldName");
                    return deleteCommand(ctx.getSource(), worldName);
                })
            )
        );

        dispatcher.register(rootCommand);
    }

    private static void showHelp(ServerCommandSource source) {
        boolean isAdmin = source.hasPermissionLevel(4);

        source.sendMessage(Text.literal("§6MultiWorld Commands:"));
        source.sendMessage(Text.literal("§7/mw tp <world> §8- §7Teleport to a world"));

        if (isAdmin) {
            source.sendMessage(Text.literal("§7/mw import <folder> <name> §8- §7Import world from folder"));
            source.sendMessage(Text.literal("§7/mw create <name> [seed] §8- §7Create a new world"));
            source.sendMessage(Text.literal("§7/mw delete <name> §8- §7Delete a world"));
        }
    }

    private static java.util.List<String> getAvailableWorlds(ServerCommandSource source) {
        return source.getWorldKeys().stream()
                .map(RegistryKey::getValue)
                .filter(id -> id.getNamespace().equals(NAMESPACE) || id.getNamespace().equals("minecraft"))
                .filter(id -> !id.getPath().endsWith("_nether") && !id.getPath().endsWith("_end"))
                .map(Identifier::getPath)
                .filter(path -> !path.equals(getBaseWorldName(getSourceWorldName(source))))
                .collect(Collectors.toList());
    }

    private static java.util.List<String> getAvailableCustomWorlds(ServerCommandSource source) {
        return source.getWorldKeys().stream()
                .map(RegistryKey::getValue)
                .filter(id -> id.getNamespace().equals(NAMESPACE))  // Uniquement les mondes du namespace multiworld
                .filter(id -> !id.getPath().endsWith("_nether") && !id.getPath().endsWith("_end"))  // Pas les dimensions nether/end
                .filter(id -> !id.getPath().equals(CREATIVE_WORLD_NAME))  // Exclure le monde créatif par défaut
                .map(Identifier::getPath)
                .collect(Collectors.toList());
    }

    private static int teleportCommand(ServerCommandSource source, String worldString) {
        worldString = getBaseWorldName(worldString);
        ServerWorld worldTarget = source.getServer().getOverworld();
        if (!worldString.equals("overworld")) {
          Identifier key = Identifier.of(NAMESPACE, worldString);
            RegistryKey<World> worldKey = RegistryKey.of(
                    RegistryKeys.WORLD,
                    key
            );
            worldTarget = source.getServer().getWorld(worldKey);
        }

        if (worldTarget == null) {
            source.sendMessage(Text.literal("World " + worldString + " not found!"));
            return 0;
        }

        String sourceWorldName = getSourceWorldName(source);
        String targetWorldName = worldTarget.getRegistryKey().getValue().getPath();

        if (sourceWorldName.equals(targetWorldName)) {
            source.sendMessage(Text.literal("You are already in this world!"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();

        Utils.saveInv(player, sourceWorldName);
        Utils.loadInv(player, targetWorldName);

        player.sendMessage(Text.literal("Teleported to " + worldTarget.getRegistryKey().getValue().toString() + " !"), false);

        return 1;
    }

    private static int importCommand(ServerCommandSource source, String folderPath, String worldName) {
        source.sendMessage(Text.literal("Importing world from " + folderPath + " to " + worldName + "..."));

        try {
            importWorld(source.getServer(), folderPath, worldName);
            source.sendMessage(Text.literal("World imported successfully!"));
            return 1;
        } catch (Exception e) {
            source.sendMessage(Text.literal("Error importing world: " + e.getMessage()));
            LOGGER.error("Error importing world", e);
            return 0;
        }
    }

    private static int createCommand(ServerCommandSource source, String worldName, long seed) {
        // Vérifier si le monde existe déjà
        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(NAMESPACE, worldName));
        if (source.getServer().getWorld(worldKey) != null) {
            source.sendMessage(Text.literal("§cA world with this name already exists!"));
            return 0;
        }

        source.sendMessage(Text.literal("Creating new world '" + worldName + "' with seed: " + seed));

        try {
            // Créer les mondes (overworld, nether, end) avec la seed spécifiée
            createWorldWithDimensions(source.getServer(), worldName, seed);

            source.sendMessage(Text.literal("§aWorld created successfully! Use §6/mw tp " + worldName + "§a to teleport to it."));
            return 1;
        } catch (Exception e) {
            source.sendMessage(Text.literal("§cError creating world: " + e.getMessage()));
            LOGGER.error("Error creating world", e);
            return 0;
        }
    }

    private static int deleteCommand(ServerCommandSource source, String worldName) {
        // Vérifier si le monde existe
        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(NAMESPACE, worldName));
        if (source.getServer().getWorld(worldKey) == null) {
            source.sendMessage(Text.literal("§cWorld '" + worldName + "' not found!"));
            return 0;
        }

        // Vérifier si des joueurs sont actuellement dans ce monde
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            String currentWorld = getBaseWorldName(player.getWorld().getRegistryKey().getValue().getPath());
            if (currentWorld.equals(worldName)) {
                // Téléporter le joueur au monde overworld
                Utils.saveInv(player, currentWorld);
                Utils.loadInv(player, "overworld");
                player.sendMessage(Text.literal("§cWorld being deleted! You've been teleported to the overworld."), false);
            }
        }

        // Confirmation de suppression
        source.sendMessage(Text.literal("§eDeleting world '" + worldName + "'..."));

        try {
            // Supprimer le monde
            deleteWorld(source.getServer(), worldName);
            source.sendMessage(Text.literal("§aWorld '" + worldName + "' has been successfully deleted."));
            return 1;
        } catch (Exception e) {
            source.sendMessage(Text.literal("§cError deleting world: " + e.getMessage()));
            LOGGER.error("Error deleting world", e);
            return 0;
        }
    }

    public static @NotNull String getSourceWorldName(ServerCommandSource source) {
        ServerWorld sourceWorld = source.getWorld();
        RegistryKey<World> sourceWorldKey = sourceWorld.getRegistryKey() == World.END || sourceWorld.getRegistryKey() == World.NETHER ? World.OVERWORLD : sourceWorld.getRegistryKey();
        String sourceWorldName = sourceWorldKey.getValue().getPath();
        return getBaseWorldName(sourceWorldName);
    }
}
