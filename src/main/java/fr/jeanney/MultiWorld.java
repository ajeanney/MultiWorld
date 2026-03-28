package fr.jeanney;

import fr.jeanney.command.CreativeCommand;
import fr.jeanney.command.MultiWorldCommand;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.Objective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static fr.jeanney.CustomServerWorld.getBaseWorldName;

public class MultiWorld implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(MultiWorld.class);

    public static final String NAMESPACE = "multiworld";
    public static final ResourceKey<DimensionType> DEFAULT_DIM_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE,
            Identifier.fromNamespaceAndPath(NAMESPACE, "default"));

    public enum WorldMode {
        NORMAL,
        CREATIVE;

        public static WorldMode fromString(String value) {
            if ("creative".equalsIgnoreCase(value)) {
                return CREATIVE;
            }
            return NORMAL;
        }

        public String toConfigValue() {
            return this == CREATIVE ? "creative" : "normal";
        }
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(CreativeCommand::register);
        CommandRegistrationCallback.EVENT.register(MultiWorldCommand::register);

        ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> {
            WorldStateService.resetOnServerStarted();
            WorldConfigService.loadCustomWorlds(server, WorldLifecycleService::unload,
                    WorldLifecycleService::createWorld);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            WorldStateService.tickCustomWorldClocks(server);
            WorldConfigService.autosaveCustomWorldGameRules(server);
            WorldStateService.autosaveCustomWorldScoreboards(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            WorldConfigService.persistCustomWorldGameRules(server, true);
            WorldStateService.autosaveCustomWorldScoreboards(server);
            WorldStateService.resetOnServerStopping();
        });
    }

    public static ServerClockManager getClockManagerForLevel(ServerLevel level) {
        return WorldStateService.getClockManagerForLevel(level);
    }

    public static ServerClockManager getClockManagerForSource(CommandSourceStack source,
            MinecraftServer fallbackServer) {
        if (source != null && source.getLevel() != null) {
            ServerLevel level = source.getLevel();
            if (NAMESPACE.equals(level.dimension().identifier().getNamespace())) {
                return getClockManagerForLevel(level);
            }
        }
        return fallbackServer.clockManager();
    }

    public static ClientboundSetTimePacket createTimeSyncPacketForLevel(ServerLevel level) {
        ServerClockManager clockManager = NAMESPACE.equals(level.dimension().identifier().getNamespace())
                ? getClockManagerForLevel(level)
                : level.getServer().clockManager();
        Map<Holder<WorldClock>, ClockNetworkState> updates = new HashMap<>();
        var clockRegistry = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK);
        clockRegistry.listElements().forEach(
                clock -> updates.put(clock, new ClockNetworkState(clockManager.getTotalTicks(clock), 0.0F, 1.0F)));
        return new ClientboundSetTimePacket(level.getGameTime(), updates);
    }

    public static boolean isWeatherGameEvent(ClientboundGameEventPacket.Type eventType) {
        return eventType == ClientboundGameEventPacket.START_RAINING
                || eventType == ClientboundGameEventPacket.STOP_RAINING
                || eventType == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE
                || eventType == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE;
    }

    public static ClientboundGameEventPacket createWeatherSyncPacketForLevel(ServerLevel level,
            ClientboundGameEventPacket.Type eventType) {
        if (eventType == ClientboundGameEventPacket.START_RAINING
                || eventType == ClientboundGameEventPacket.STOP_RAINING) {
            boolean raining = level.getWeatherData().isRaining();
            return new ClientboundGameEventPacket(
                    raining ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING,
                    0.0F);
        }

        if (eventType == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE) {
            return new ClientboundGameEventPacket(eventType, level.getRainLevel(1.0F));
        }

        if (eventType == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE) {
            return new ClientboundGameEventPacket(eventType, level.getThunderLevel(1.0F));
        }

        return new ClientboundGameEventPacket(eventType, 0.0F);
    }

    public static ServerScoreboard getScoreboardForSource(CommandSourceStack source, MinecraftServer fallbackServer) {
        if (source != null && source.getLevel() != null) {
            ServerLevel level = source.getLevel();
            if (NAMESPACE.equals(level.dimension().identifier().getNamespace())) {
                return getScoreboardForLevel(level);
            }
        }
        return fallbackServer.getScoreboard();
    }

    public static ServerScoreboard getScoreboardForLevel(ServerLevel level) {
        return WorldStateService.getScoreboardForLevel(level);
    }

    public static GameRules getCustomWorldGameRules(ServerLevel level) {
        return WorldStateService.getCustomWorldGameRules(level);
    }

    public static WeatherData getCustomWorldWeatherData(ServerLevel level) {
        return WorldStateService.getCustomWorldWeatherData(level);
    }

    private static WorldStateService.WorldInitialState createFreshInitialState(WorldMode worldMode) {
        return WorldStateService.createFreshInitialState(worldMode);
    }

    private static WorldStateService.WorldInitialState createImportedInitialState(CompoundTag dataTag,
            Optional<WeatherData> savedWeather,
            boolean preserveSavedWorldClocks, OptionalLong overworldClockTicks) {
        return WorldStateService.createImportedInitialState(dataTag, savedWeather, preserveSavedWorldClocks,
                overworldClockTicks);
    }

    public static boolean isCreativeModeWorld(ResourceKey<Level> key) {
        return WorldStateService.isCreativeModeWorld(key);
    }

    public static Optional<String> getCreativeWorldName(MinecraftServer server) {
        return WorldStateService.getCreativeWorldName(server);
    }

    private static void rememberPendingInitialState(String worldName,
            WorldStateService.WorldInitialState initialState) {
        WorldStateService.rememberPendingInitialState(worldName, initialState);
    }

    public static void applyPendingInitialStateIfNeeded(MinecraftServer server, String worldName) {
        Optional<WorldStateService.WorldInitialState> initialState = WorldStateService
                .consumePendingInitialState(worldName);
        if (initialState.isPresent()) {
            WorldStateService.applyInitialStateToWorldFamily(server, getBaseWorldName(worldName), initialState.get());
        }
    }

    public static boolean importWorld(MinecraftServer server, String worldFolderPath, String worldName) {
        LOGGER.info("Importing world from {} to multiworld:{}", worldFolderPath, worldName);

        File sourceWorldFolder = new File(worldFolderPath);
        if (!sourceWorldFolder.exists() || !sourceWorldFolder.isDirectory()) {
            LOGGER.error("Source world folder {} does not exist or is not a directory", worldFolderPath);
            return false;
        }

        try {
            sourceWorldFolder = WorldImportSupport.resolveImportSourceWorldFolder(sourceWorldFolder);
            File levelDatFile = new File(sourceWorldFolder, "level.dat");
            if (!levelDatFile.exists()) {
                LOGGER.error("level.dat not found in {}", sourceWorldFolder.getAbsolutePath());
                return false;
            }

            CompoundTag rawLevelData = NbtIo.readCompressed(levelDatFile.toPath(), NbtAccounter.unlimitedHeap());
            long seed = WorldImportSupport.extractSeedFromLevelDat(rawLevelData, server, sourceWorldFolder);
            CompoundTag rawDataTag = rawLevelData.getCompound("Data").orElse(rawLevelData);
            BlockPos importedSpawnPos = WorldImportSupport.extractImportedSpawnPos(rawDataTag).orElse(null);

            CompoundTag levelData = rawLevelData.copy();
            levelData = DataFixTypes.LEVEL.updateToCurrentVersion(
                    server.getFixerUpper(),
                    levelData,
                    NbtUtils.getDataVersion(levelData, 0));
            NbtUtils.addCurrentDataVersion(levelData);
            CompoundTag dataTag = levelData.getCompound("Data").orElse(levelData);
            LOGGER.info("Seed from world: {}", seed);

            String serverWorldFolder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toString();

            ResourceKey<Level> overWorldKey = ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, worldName));
            ResourceKey<Level> netherWorldKey = ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, worldName + "_nether"));
            ResourceKey<Level> endWorldKey = ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, worldName + "_end"));

            if (server.getLevel(overWorldKey) != null) {
                LOGGER.info("Import target world '{}' already exists; replacing it", worldName);
                deleteWorld(server, worldName);
            }

            File sourceOverworldFolder = WorldImportSupport.resolveOverworldImportFolder(sourceWorldFolder);
            WorldImportSupport.copyWorldFiles(sourceOverworldFolder, serverWorldFolder, overWorldKey);

            if (!sourceOverworldFolder.equals(sourceWorldFolder)) {
                String overworldDestination = WorldImportSupport.buildWorldDirectoryPath(serverWorldFolder,
                        overWorldKey);
                // Modern saves keep shared saved-data (world_gen_settings, weather,
                // scoreboard...) at root/data.
                // Merge it into imported overworld data after copying the per-dimension folder.
                WorldImportSupport.copyFilesBeforeWorldCreation(sourceWorldFolder, "data", overworldDestination);
            }

            File sourceNetherFolder = WorldImportSupport.resolveNetherImportFolder(sourceWorldFolder);
            if (sourceNetherFolder != null) {
                WorldImportSupport.copyWorldFiles(sourceNetherFolder, serverWorldFolder, netherWorldKey);
            }

            File sourceEndFolder = WorldImportSupport.resolveEndImportFolder(sourceWorldFolder);
            if (sourceEndFolder != null) {
                WorldImportSupport.copyWorldFiles(sourceEndFolder, serverWorldFolder, endWorldKey);
            }

            Optional<WeatherData> importedWeather = WorldImportSupport.extractWeatherFromSavedData(server,
                    sourceWorldFolder);
            boolean preserveSavedWorldClocks = WorldImportSupport.hasWorldClocksDat(sourceWorldFolder);
            OptionalLong overworldClockTicks = WorldImportSupport.extractOverworldClockTicksFromDat(sourceWorldFolder);
            Set<String> globalObjectivesBeforeImport = getObjectiveNames(server.getScoreboard());
            WorldStateService.WorldInitialState importedInitialState = createImportedInitialState(dataTag,
                    importedWeather,
                    preserveSavedWorldClocks, overworldClockTicks);
            createWorldWithDimensions(server, worldName, seed, importedSpawnPos, "normal", WorldMode.NORMAL,
                    importedInitialState);

            ServerLevel overworld = server.getLevel(overWorldKey);

            if (overworld == null) {
                LOGGER.error("Failed to create overworld dimension");
                return false;
            }

            GameRules importedGameRules = WorldImportSupport.extractImportedGameRules(server, sourceWorldFolder,
                    rawDataTag);
            applyImportedGameRulesToWorldFamily(server, worldName, importedGameRules);
            removeLeakedGlobalObjectives(server, globalObjectivesBeforeImport);

            File multiworldFolder = new File(serverWorldFolder, NAMESPACE);
            if (!multiworldFolder.exists() && !multiworldFolder.mkdirs()) {
                LOGGER.error("Could not create multiworld directory");
                return false;
            }

            File worldNameFolder = new File(multiworldFolder, worldName);
            if (!worldNameFolder.exists() && !worldNameFolder.mkdirs()) {
                LOGGER.error("Could not create world directory");
                return false;
            }

            File playerDataFolder = resolvePlayerDataFolder(sourceWorldFolder);
            File statsFolder = resolvePlayerStatsFolder(sourceWorldFolder);
            File advancementsFolder = resolvePlayerAdvancementsFolder(sourceWorldFolder);
            if (playerDataFolder != null) {
                File[] playerDataFiles = playerDataFolder.listFiles((dir, name) -> name.endsWith(".dat"));
                if (playerDataFiles != null) {
                    for (File playerDataFile : playerDataFiles) {
                        try {
                            CompoundTag playerData = NbtIo.readCompressed(playerDataFile.toPath(),
                                    NbtAccounter.unlimitedHeap());

                            String uuid = playerDataFile.getName().replace(".dat", "");

                            playerData = PlayerProfileManager.migratePlayerDataToCurrentVersion(
                                    server,
                                    playerData,
                                    "import/" + worldName + "/" + uuid);

                            WorldImportSupport.updateDimensionInTag(playerData, "respawn", worldName);

                            String dimensionTagString = playerData.getString("Dimension")
                                    .orElse(Level.OVERWORLD.identifier().toString());
                            playerData.putString("Dimension",
                                    WorldImportSupport.convertDimensionName(dimensionTagString, worldName));

                            WorldImportSupport.updateDimensionInTag(playerData, "LastDeathLocation", worldName);

                            JsonObject playerJson = new JsonObject();
                            playerJson.addProperty("player", playerData.toString());

                            File statsFile = statsFolder == null
                                    ? null
                                    : new File(statsFolder, uuid + ".json");
                            if (statsFile != null && statsFile.exists()) {
                                try (FileReader statsReader = new FileReader(statsFile)) {
                                    JsonElement parsed = JsonParser.parseReader(statsReader);
                                    if (parsed != null && parsed.isJsonObject()) {
                                        playerJson.add("stats", parsed.getAsJsonObject());
                                    }
                                }
                            }

                            File advancementsFile = advancementsFolder == null
                                    ? null
                                    : new File(advancementsFolder, uuid + ".json");
                            if (advancementsFile != null && advancementsFile.exists()) {
                                try (FileReader advancementsReader = new FileReader(advancementsFile)) {
                                    JsonElement parsed = JsonParser.parseReader(advancementsReader);
                                    if (parsed != null && parsed.isJsonObject()) {
                                        playerJson.add("advancements", parsed.getAsJsonObject());
                                    }
                                }
                            }

                            try (BufferedWriter writer = Files.newBufferedWriter(
                                    Paths.get(worldNameFolder.getPath(), uuid + ".json"))) {
                                writer.write(playerJson.toString());
                            }

                        } catch (Exception e) {
                            LOGGER.error("Error processing player data: {}", e.getMessage());
                        }
                    }
                }
            } else {
                LOGGER.info("No player data directory found in import source '{}'; skipping player profile import",
                        sourceWorldFolder.getAbsolutePath());
            }

            LOGGER.info("World import completed successfully!");
            return true;

        } catch (Exception e) {
            LOGGER.error("Error importing world", e);
            return false;
        }
    }

    private static File resolvePlayerDataFolder(File sourceWorldFolder) {
        File legacy = new File(sourceWorldFolder, "playerdata");
        if (legacy.isDirectory()) {
            return legacy;
        }

        File modern = new File(sourceWorldFolder, "players/data");
        if (modern.isDirectory()) {
            return modern;
        }

        return null;
    }

    private static File resolvePlayerStatsFolder(File sourceWorldFolder) {
        File legacy = new File(sourceWorldFolder, "stats");
        if (legacy.isDirectory()) {
            return legacy;
        }

        File modern = new File(sourceWorldFolder, "players/stats");
        if (modern.isDirectory()) {
            return modern;
        }

        return null;
    }

    private static File resolvePlayerAdvancementsFolder(File sourceWorldFolder) {
        File legacy = new File(sourceWorldFolder, "advancements");
        if (legacy.isDirectory()) {
            return legacy;
        }

        File modern = new File(sourceWorldFolder, "players/advancements");
        if (modern.isDirectory()) {
            return modern;
        }

        return null;
    }

    private static void applyImportedGameRulesToWorldFamily(MinecraftServer server, String worldName,
            GameRules importedGameRules) {
        String[] dimensionNames = { worldName, worldName + "_nether", worldName + "_end" };
        for (String name : dimensionNames) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, name));
            ServerLevel level = server.getLevel(key);
            if (level == null) {
                continue;
            }

            level.getGameRules().setAll(importedGameRules, server);
            WorldStateService.setLastSavedGameRuleSnapshot(
                    key,
                    WorldConfigService.encodeGameRules(server, level.getGameRules()).toString());
        }
    }

    private static void removeLeakedGlobalObjectives(MinecraftServer server,
            Set<String> globalObjectivesBeforeImport) {
        ServerScoreboard globalScoreboard = server.getScoreboard();
        for (String objectiveName : getObjectiveNames(globalScoreboard)) {
            if (globalObjectivesBeforeImport.contains(objectiveName)) {
                continue;
            }
            Objective objective = globalScoreboard.getObjective(objectiveName);
            if (objective != null) {
                globalScoreboard.removeObjective(objective);
            }
        }
    }

    private static Set<String> getObjectiveNames(ServerScoreboard scoreboard) {
        Set<String> names = new HashSet<>();
        for (Objective objective : scoreboard.getObjectives()) {
            names.add(objective.getName());
        }
        return names;
    }

    public static void createWorldWithDimensions(MinecraftServer server, String worldString, long seed) {
        createWorldWithDimensions(server, worldString, seed, null, "normal", WorldMode.NORMAL);
    }

    public static void createWorldWithDimensions(MinecraftServer server, String worldString, long seed,
            BlockPos spawnPos) {
        createWorldWithDimensions(server, worldString, seed, spawnPos, "normal", WorldMode.NORMAL);
    }

    public static void createWorldWithDimensions(
            MinecraftServer server,
            String worldString,
            long seed,
            BlockPos spawnPos,
            String preset,
            WorldMode worldMode) {
        WorldStateService.WorldInitialState initialState = createFreshInitialState(worldMode);
        rememberPendingInitialState(worldString, initialState);
        createWorldWithDimensions(server, worldString, seed, spawnPos, preset, worldMode, initialState);
    }

    private static void createWorldWithDimensions(
            MinecraftServer server,
            String worldString,
            long seed,
            BlockPos spawnPos,
            String preset,
            WorldMode worldMode,
            WorldStateService.WorldInitialState initialState) {
        WorldLifecycleService.createWorldWithDimensions(server, worldString, seed, spawnPos, preset, worldMode,
                initialState);
    }

    public static void deleteWorld(MinecraftServer server, String worldName) {
        WorldLifecycleService.deleteWorld(server, worldName);
    }

}
