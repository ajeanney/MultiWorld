package fr.jeanney.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.jeanney.MultiWorld;
import fr.jeanney.PlayerProfileManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fr.jeanney.CustomServerWorld.getBaseWorldName;
import static fr.jeanney.MultiWorld.*;
import static net.minecraft.commands.Commands.literal;

public final class MultiWorldCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess,
            Commands.CommandSelection environment) {
        LiteralArgumentBuilder<CommandSourceStack> rootCommand = literal("mw")
                .executes(ctx -> {
                    showHelp(ctx.getSource());
                    return 1;
                });

        rootCommand.then(literal("tp")
                .then(Commands.argument("world", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider
                                .suggest(getAvailableWorlds(ctx.getSource()), builder))
                        .executes(ctx -> {
                            String worldName = StringArgumentType.getString(ctx, "world");
                            return teleportCommand(ctx.getSource(), worldName);
                        })));

        rootCommand.then(literal("debug")
                .executes(ctx -> debugCommand(ctx.getSource(), null))
                .then(Commands.argument("world", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider
                                .suggest(getDebuggableWorlds(ctx.getSource()), builder))
                        .executes(ctx -> debugCommand(ctx.getSource(), StringArgumentType.getString(ctx, "world")))));

        rootCommand.then(literal("import")
                .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .then(Commands.argument("folderPath", StringArgumentType.string())
                        .suggests((ctx, builder) -> suggestFolderPaths(builder))
                        .then(Commands.argument("worldName", StringArgumentType.word())
                                .executes(ctx -> {
                                    String folderPath = StringArgumentType.getString(ctx, "folderPath");
                                    String worldName = StringArgumentType.getString(ctx, "worldName");
                                    return importCommand(ctx.getSource(), folderPath, worldName);
                                }))));

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
                                                    return createCommand(ctx.getSource(), worldName, preset, mode,
                                                            seed);
                                                }))
                                        .then(Commands.argument("seedText", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String worldName = StringArgumentType.getString(ctx, "worldName");
                                                    String preset = StringArgumentType.getString(ctx, "preset");
                                                    String mode = StringArgumentType.getString(ctx, "mode");
                                                    long seed = parseSeed(
                                                            StringArgumentType.getString(ctx, "seedText"));
                                                    return createCommand(ctx.getSource(), worldName, preset, mode,
                                                            seed);
                                                }))))));

        rootCommand.then(literal("delete")
                .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .then(Commands.argument("worldName", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                getAvailableCustomWorlds(ctx.getSource()),
                                builder))
                        .executes(ctx -> {
                            String worldName = StringArgumentType.getString(ctx, "worldName");
                            return deleteCommand(ctx.getSource(), worldName);
                        })));

        dispatcher.register(rootCommand);
    }

    private static void showHelp(CommandSourceStack source) {
        boolean isAdmin = Commands.hasPermission(Commands.LEVEL_OWNERS).test(source);

        source.sendSystemMessage(Component.literal("§6MultiWorld Commands:"));
        source.sendSystemMessage(Component.literal("§7/mw tp <world> §8- §7Teleport to a world"));
        source.sendSystemMessage(Component.literal("§7/mw debug [world] §8- §7Show world debug information"));

        if (isAdmin) {
            source.sendSystemMessage(Component.literal("§7/mw import <folder> <name> §8- §7Import world from folder"));
            source.sendSystemMessage(
                    Component.literal("§7/mw create <name> [preset] [mode] [seed] §8- §7Create a new world"));
            source.sendSystemMessage(Component.literal("§7/mw delete <name> §8- §7Delete a world"));
        }
    }

    private static int debugCommand(CommandSourceStack source, String worldName) {
        ServerLevel world = worldName == null ? source.getLevel() : resolveWorldByBaseName(source, worldName);
        if (world == null) {
            source.sendFailure(Component.literal("World '" + worldName + "' not found."));
            return 0;
        }

        List<String> lines = collectWorldDebugInfo(source.getServer(), world);
        lines.forEach(line -> source.sendSystemMessage(Component.literal(line)));
        return 1;
    }

    public static List<String> collectWorldDebugInfo(MinecraftServer server, ServerLevel world) {
        List<String> lines = new ArrayList<>();
        String baseWorldName = getBaseWorldName(world.dimension().identifier().getPath());

        lines.add("§6[MW Debug] " + baseWorldName + " (" + world.dimension().identifier() + ")");

        LevelData.RespawnData respawnData = world.getRespawnData();
        if (respawnData != null) {
            BlockPos spawnPos = respawnData.pos();
            lines.add("spawn: X=" + spawnPos.getX() + ", Y=" + spawnPos.getY() + ", Z=" + spawnPos.getZ());
        } else {
            lines.add("spawn: <unset>");
        }

        lines.add("time: game=" + world.getGameTime() + ", clock=" + world.getDefaultClockTime());

        WeatherData weatherData = MultiWorld.getCustomWorldWeatherData(world);
        if (weatherData == null) {
            weatherData = world.getWeatherData();
        }
        lines.add("weather: c=" + weatherData.getClearWeatherTime()
                + ", r=" + weatherData.getRainTime()
                + ", t=" + weatherData.getThunderTime()
                + ", rain=" + weatherData.isRaining()
                + ", th=" + weatherData.isThundering());

        lines.add("seed=" + world.getSeed());

        CompoundTag encodedGameRules = encodeGameRules(server, world.getGameRules());
        int gameRuleCount = encodedGameRules.size();
        int trueRulesCount = (int) encodedGameRules.entrySet().stream()
                .filter(entry -> "true".equalsIgnoreCase(entry.getValue().toString()))
                .count();
        List<String> sampleTrueRules = encodedGameRules.entrySet().stream()
                .filter(entry -> "true".equalsIgnoreCase(entry.getValue().toString()))
                .map(java.util.Map.Entry::getKey)
                .sorted()
                .limit(5)
                .toList();
        lines.add("gamerules: total=" + gameRuleCount + ", true=" + trueRulesCount
                + (sampleTrueRules.isEmpty() ? "" : ", sampleTrue=" + String.join(",", sampleTrueRules)));

        Path dataMinecraftFolder = resolveDataMinecraftFolder(server, baseWorldName);
        lines.add("data/minecraft path: " + dataMinecraftFolder);
        if (!Files.isDirectory(dataMinecraftFolder)) {
            lines.add("data/minecraft: <missing>");
            return lines;
        }

        try (Stream<Path> paths = Files.list(dataMinecraftFolder)) {
            List<Path> datFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".dat"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            lines.add("saved-data files(" + datFiles.size() + "): " + datFiles.stream()
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.joining(", ")));

            for (Path datFile : datFiles) {
                lines.add(" - " + summarizeDatFile(datFile));
            }
        } catch (Exception ex) {
            lines.add("data/minecraft read error: " + ex.getMessage());
        }

        return lines;
    }

    private static ServerLevel resolveWorldByBaseName(CommandSourceStack source, String worldName) {
        String baseWorldName = getBaseWorldName(worldName);
        if ("overworld".equals(baseWorldName)) {
            return source.getServer().overworld();
        }

        ResourceKey<Level> worldKey = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(NAMESPACE, baseWorldName));
        return source.getServer().getLevel(worldKey);
    }

    private static List<String> getDebuggableWorlds(CommandSourceStack source) {
        return source.levels().stream()
                .map(ResourceKey::identifier)
                .filter(id -> id.getNamespace().equals(NAMESPACE) || id.getNamespace().equals("minecraft"))
                .filter(id -> !id.getPath().endsWith("_nether") && !id.getPath().endsWith("_end"))
                .map(Identifier::getPath)
                .map(fr.jeanney.CustomServerWorld::getBaseWorldName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private static Path resolveDataMinecraftFolder(MinecraftServer server, String baseWorldName) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        if ("overworld".equals(baseWorldName)) {
            return worldRoot.resolve("data").resolve("minecraft");
        }
        return worldRoot.resolve("dimensions")
                .resolve(NAMESPACE)
                .resolve(baseWorldName)
                .resolve("data")
                .resolve("minecraft");
    }

    private static String summarizeDatFile(Path datFile) {
        try {
            CompoundTag root = NbtIo.readCompressed(datFile, NbtAccounter.unlimitedHeap());
            List<String> rootKeys = root.entrySet().stream()
                    .map(java.util.Map.Entry::getKey)
                    .sorted()
                    .toList();

            Optional<CompoundTag> dataCompoundOpt = root.getCompound("data");
            if (dataCompoundOpt.isPresent()) {
                CompoundTag dataCompound = dataCompoundOpt.get();
                List<String> dataKeys = dataCompound.entrySet().stream()
                        .map(java.util.Map.Entry::getKey)
                        .sorted()
                        .toList();
                List<String> sampleDataKeys = dataKeys.stream().limit(4).toList();
                return datFile.getFileName()
                        + " rk=" + rootKeys.size()
                        + " dk=" + dataKeys.size()
                        + (sampleDataKeys.isEmpty() ? "" : " sample=" + String.join(",", sampleDataKeys));
            }

            List<String> sampleRootKeys = rootKeys.stream().limit(4).toList();
            return datFile.getFileName()
                    + " rk=" + rootKeys.size()
                    + (sampleRootKeys.isEmpty() ? "" : " sample=" + String.join(",", sampleRootKeys));
        } catch (Exception ex) {
            return datFile.getFileName() + " <read error: " + ex.getMessage() + ">";
        }
    }

    private static CompoundTag encodeGameRules(MinecraftServer server,
            net.minecraft.world.level.gamerules.GameRules gameRules) {
        Object encoded = net.minecraft.world.level.gamerules.GameRules.codec(server.getWorldData().enabledFeatures())
                .encodeStart(NbtOps.INSTANCE, gameRules)
                .result()
                .orElse(null);
        if (encoded instanceof CompoundTag compoundTag) {
            return compoundTag;
        }
        return new CompoundTag();
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
        return SharedSuggestionProvider.suggest(new String[] { "normal", "flat" }, builder);
    }

    private static CompletableFuture<Suggestions> suggestModes(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(new String[] { "normal", "creative" }, builder);
    }

    private static CompletableFuture<Suggestions> suggestFolderPaths(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        String normalizedInput = remaining == null ? "" : remaining;
        boolean quotedInput = normalizedInput.startsWith("\"");
        if (quotedInput) {
            normalizedInput = normalizedInput.substring(1);
        }

        normalizedInput = normalizedInput.replace('\\', '/');

        Path path = Paths.get("");
        Path inputPath = normalizedInput.isEmpty() ? path : Paths.get(normalizedInput);
        boolean trailingSlash = normalizedInput.endsWith("/");

        Path basePath;
        String namePrefix;
        if (trailingSlash) {
            basePath = inputPath;
            namePrefix = "";
        } else {
            Path parent = inputPath.getParent();
            basePath = parent == null ? path : parent;
            Path fileName = inputPath.getFileName();
            namePrefix = fileName == null ? "" : fileName.toString();
        }

        Path searchPath = basePath.isAbsolute()
                ? basePath.normalize()
                : path.toAbsolutePath().resolve(basePath).normalize();

        if (!Files.isDirectory(searchPath)) {
            return builder.buildFuture();
        }

        String prefixLower = namePrefix.toLowerCase();

        try (Stream<Path> pathStream = Files.list(searchPath)) {
            pathStream
                    .filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .filter(Objects::nonNull)
                    .map(Path::toString)
                    .filter(name -> name.toLowerCase().startsWith(prefixLower))
                    .sorted()
                    .limit(200)
                    .forEach(name -> {
                        String suggestionPath;
                        if (basePath.toString().isEmpty()) {
                            suggestionPath = name;
                        } else {
                            suggestionPath = basePath.toString().replace('\\', '/') + "/" + name;
                        }

                        String suggestion = suggestionPath.contains(" ") ? "\"" + suggestionPath + "\""
                                : suggestionPath;
                        builder.suggest(suggestion);
                    });
        } catch (IOException ignored) {
            return builder.buildFuture();
        }

        return builder.buildFuture();
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
                    Identifier.fromNamespaceAndPath(NAMESPACE, worldString));
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

        PlayerProfileManager.saveInv(player, sourceWorldName);
        PlayerProfileManager.loadInv(player, targetWorldName);

        player.sendSystemMessage(Component.literal("Teleported to " + worldTarget.dimension().identifier() + " !"));

        return 1;
    }

    private static int importCommand(CommandSourceStack source, String folderPath, String worldName) {
        source.sendSystemMessage(Component.literal("Importing world from " + folderPath + " to " + worldName + "..."));

        try {
            if (!importWorld(source.getServer(), folderPath, worldName)) {
                source.sendFailure(
                        Component.literal("Error importing world: invalid source folder or missing level.dat"));
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

    private static int createCommand(CommandSourceStack source, String worldName, String preset, String mode,
            long seed) {
        ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(NAMESPACE, worldName));
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

        source.sendSystemMessage(Component.literal("Creating new world '" + worldName + "' with seed: " + seed
                + " (preset=" + normalizedPreset + ", mode=" + normalizedMode + ")"));

        try {
            createWorldWithDimensions(source.getServer(), worldName, seed, null, normalizedPreset, worldMode);
            source.sendSuccess(
                    () -> Component.literal(
                            "§aWorld created successfully! Use §6/mw tp " + worldName + "§a to teleport to it."),
                    false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError creating world: " + e.getMessage()));
            LOGGER.error("Error creating world", e);
            return 0;
        }
    }

    private static int deleteCommand(CommandSourceStack source, String worldName) {
        ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(NAMESPACE, worldName));
        if (source.getServer().getLevel(worldKey) == null) {
            source.sendFailure(Component.literal("§cWorld '" + worldName + "' not found!"));
            return 0;
        }

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            String currentWorld = getBaseWorldName(player.level().dimension().identifier().getPath());
            if (currentWorld.equals(worldName)) {
                PlayerProfileManager.saveInv(player, currentWorld);
                PlayerProfileManager.loadInv(player, "overworld");
                player.sendSystemMessage(
                        Component.literal("§cWorld being deleted! You've been teleported to the overworld."));
            }
        }

        source.sendSystemMessage(Component.literal("§eDeleting world '" + worldName + "'..."));

        try {
            deleteWorld(source.getServer(), worldName);
            source.sendSuccess(() -> Component.literal("§aWorld '" + worldName + "' has been successfully deleted."),
                    false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError deleting world: " + e.getMessage()));
            LOGGER.error("Error deleting world", e);
            return 0;
        }
    }

    public static @NotNull String getSourceWorldName(CommandSourceStack source) {
        ServerLevel sourceWorld = source.getLevel();
        ResourceKey<Level> sourceWorldKey = sourceWorld.dimension() == Level.END
                || sourceWorld.dimension() == Level.NETHER ? Level.OVERWORLD : sourceWorld.dimension();
        String sourceWorldName = sourceWorldKey.identifier().getPath();
        return getBaseWorldName(sourceWorldName);
    }
}
