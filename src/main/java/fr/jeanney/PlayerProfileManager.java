package fr.jeanney;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.jeanney.mixin.PlayerAdvancementsAccessor;
import fr.jeanney.mixin.ServerLevelAccessor;
import fr.jeanney.mixin.ServerStatsCounterAccessor;
import fr.jeanney.mixin.StatsCounterAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static fr.jeanney.MultiWorld.LOGGER;
import static fr.jeanney.MultiWorld.NAMESPACE;

public final class PlayerProfileManager {
    private PlayerProfileManager() {
    }

    static CompoundTag migratePlayerDataToCurrentVersion(MinecraftServer server, CompoundTag playerNbt,
            String context) {
        int sourceVersion = NbtUtils.getDataVersion(playerNbt, 0);
        int currentVersion = SharedConstants.getCurrentVersion().dataVersion().version();
        if (sourceVersion >= currentVersion) {
            return playerNbt;
        }

        try {
            CompoundTag fixed = DataFixTypes.PLAYER.updateToCurrentVersion(server.getFixerUpper(), playerNbt,
                    sourceVersion);
            NbtUtils.addCurrentDataVersion(fixed);
            LOGGER.info("Migrated player data '{}' from DataVersion {} to {}", context, sourceVersion, currentVersion);
            return fixed;
        } catch (Exception ex) {
            LOGGER.error("Error migrating player data '{}' from DataVersion {}: {}", context, sourceVersion,
                    ex.getMessage());
            return playerNbt;
        }
    }

    public static void saveInv(ServerPlayer player, String world) {
        try {
            String worldFolder = getWorldFolder(player.level().getServer());

            if (new File(worldFolder + "/" + NAMESPACE + "/" + world).mkdirs()) {
                LOGGER.info("Directory created: {}/{}/{}", worldFolder, NAMESPACE, world);
            }
            BufferedWriter writer = Files.newBufferedWriter(
                    Paths.get(worldFolder + "/" + NAMESPACE + "/" + world + "/" + player.getStringUUID() + ".json"));

            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                    player.registryAccess());
            player.saveWithoutId(output);

            player.getEnderPearls().forEach((enderPearl) -> enderPearl.remove(Entity.RemovalReason.DISCARDED));

            if (player.getRootVehicle() != player) {
                player.getRootVehicle().remove(Entity.RemovalReason.DISCARDED);
            }

            CompoundTag tag = output.buildResult();

            CompoundTag respawnTag = new CompoundTag();
            ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
            if (respawn != null) {
                respawnTag.putString("dimension", respawn.respawnData().dimension().identifier().toString());
                respawnTag.putBoolean("forced", respawn.forced());
                respawnTag.putFloat("angle", respawn.respawnData().yaw());
                respawnTag.putFloat("pitch", respawn.respawnData().pitch());
                respawnTag.putIntArray("pos", new int[] { respawn.respawnData().pos().getX(),
                        respawn.respawnData().pos().getY(), respawn.respawnData().pos().getZ() });
                tag.put("respawn", respawnTag);
            }

            player.getStats().save();
            JsonObject statsJson = new JsonObject();
            Path vanillaStatsPath = ((ServerStatsCounterAccessor) player.getStats()).getFile();
            if (vanillaStatsPath != null && Files.exists(vanillaStatsPath)) {
                try (FileReader statsReader = new FileReader(vanillaStatsPath.toFile())) {
                    JsonElement parsed = JsonParser.parseReader(statsReader);
                    if (parsed != null && parsed.isJsonObject()) {
                        statsJson = parsed.getAsJsonObject();
                    }
                }
            }

            player.getAdvancements().save();
            JsonObject advancementsJson = new JsonObject();
            Path vanillaAdvancementsPath = ((PlayerAdvancementsAccessor) player.getAdvancements()).getPlayerSavePath();
            if (vanillaAdvancementsPath != null && Files.exists(vanillaAdvancementsPath)) {
                try (FileReader advancementsReader = new FileReader(vanillaAdvancementsPath.toFile())) {
                    JsonElement parsed = JsonParser.parseReader(advancementsReader);
                    if (parsed != null && parsed.isJsonObject()) {
                        advancementsJson = parsed.getAsJsonObject();
                    }
                }
            }

            JsonObject playerJson = new JsonObject();
            playerJson.addProperty("player", tag.toString());
            playerJson.add("stats", statsJson);
            playerJson.add("advancements", advancementsJson);
            writer.write(playerJson.toString());
            writer.close();

        } catch (Exception ex) {
            LOGGER.error("Error saving player data: {}", ex.getMessage());
        }
    }

    public static void loadInv(ServerPlayer player, String world) {
        String worldFolder = getWorldFolder(player.level().getServer());
        Path playerProfilePath = Paths.get(worldFolder, NAMESPACE, world, player.getStringUUID() + ".json");
        Path advancementsPath = ((PlayerAdvancementsAccessor) player.getAdvancements()).getPlayerSavePath();
        if (advancementsPath == null) {
            advancementsPath = Paths.get(worldFolder, "advancements", player.getStringUUID() + ".json");
        }

        if (!Files.exists(playerProfilePath)) {
            loadMissingWorldProfile(player, world, advancementsPath);
            return;
        }

        try (FileReader fileReader = new FileReader(playerProfilePath.toFile())) {
            JsonElement parsedRoot = JsonParser.parseReader(fileReader);
            JsonObject file = parsedRoot != null && parsedRoot.isJsonObject() ? parsedRoot.getAsJsonObject()
                    : new JsonObject();

            String playerString = file.has("player") ? file.get("player").getAsString() : "{}";
            CompoundTag playerNbt = TagParser.parseCompoundFully(playerString);
            playerNbt = migratePlayerDataToCurrentVersion(
                    player.level().getServer(),
                    playerNbt,
                    "loadInv/" + world + "/" + player.getStringUUID());

            ValueInput nbtReadView = TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(),
                    playerNbt);
            player.load(nbtReadView);
            player.loadAndSpawnEnderPearls(nbtReadView);

            String[] strArr = playerNbt.getString("Dimension").orElse(Level.OVERWORLD.identifier().toString())
                    .split(":");
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(strArr[0], strArr[1]));
            ServerLevel dimension = player.level().getServer().getLevel(key);

            Optional<CompoundTag> respawnTag = playerNbt.getCompound("respawn");
            if (respawnTag.isPresent()) {
                CompoundTag respawnData = respawnTag.get();
                int[] respawnPos = respawnData.getIntArray("pos").orElseThrow();
                String respawnDimensionStr = respawnData.getString("dimension")
                        .orElse(Level.OVERWORLD.identifier().toString());
                String[] dimParts = respawnDimensionStr.split(":");
                ResourceKey<Level> respawnDimension = ResourceKey.create(Registries.DIMENSION,
                        Identifier.fromNamespaceAndPath(dimParts[0], dimParts[1]));
                LevelData.RespawnData respawnDataObj = new LevelData.RespawnData(
                        new GlobalPos(respawnDimension, new BlockPos(respawnPos[0], respawnPos[1], respawnPos[2])),
                        respawnData.getFloat("angle").orElse(0F),
                        respawnData.getFloat("pitch").orElse(0F));
                player.setRespawnPosition(
                        new ServerPlayer.RespawnConfig(respawnDataObj, respawnData.getBooleanOr("forced", false)),
                        false);
            }

            if (file.has("stats") && file.get("stats").isJsonObject()) {
                JsonObject statsData = file.getAsJsonObject("stats");

                ServerStatsCounter statHandler = player.getStats();
                for (Stat<?> stat : ((StatsCounterAccessor) statHandler).getStatMap().keySet()) {
                    player.resetStat(stat);
                }

                statHandler.parse(player.level().getServer().getFixerUpper(), statsData);
                statHandler.markAllDirty();
            }

            if (file.has("advancements") && file.get("advancements").isJsonObject()) {
                JsonObject advancementsData = file.getAsJsonObject("advancements");
                Files.createDirectories(advancementsPath.getParent());
                Files.write(
                        advancementsPath,
                        advancementsData.toString().getBytes());
            }
            player.getAdvancements().reload(player.level().getServer().getAdvancements());

            if (dimension != null) {
                player.teleport(new TeleportTransition(dimension, new Vec3(player.getX(), player.getY(), player.getZ()),
                        Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
                syncCustomWorldTimeToClient(player);
            }

            player.loadAndSpawnParentVehicle(nbtReadView);
            forceAdvancementClientRefresh(player, false);
        } catch (Exception ex) {
            LOGGER.warn("Failed to load world profile '{}' for player {}: {}", playerProfilePath,
                    player.getStringUUID(), ex.getMessage());
            loadMissingWorldProfile(player, world, advancementsPath);
        }
    }

    public static String getMostRecentWorldSaved(ServerPlayer player, String sourceWorld) {
        String worldFolder = getWorldFolder(player.level().getServer());
        File folder = new File(worldFolder + "/" + NAMESPACE);
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            String mostRecentWorld = "";
            long mostRecentTime = 0;
            for (File file : listOfFiles) {
                if (file.isDirectory()) {
                    File playerFile = new File(file.getPath() + "/" + player.getUUID() + ".json");
                    if (playerFile.exists()) {
                        long lastModified = playerFile.lastModified();
                        if (lastModified > mostRecentTime && !file.getName().equals(sourceWorld)) {
                            mostRecentTime = lastModified;
                            mostRecentWorld = file.getName();
                        }
                    }
                }
            }
            return mostRecentWorld;
        } else {
            LOGGER.error("No worlds found in namespace folder");
            return "overworld";
        }
    }

    private static String getWorldFolder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toString();
    }

    private static void loadMissingWorldProfile(ServerPlayer player, String world, Path advancementsPath) {
        if (!"overworld".equals(world)) {
            MultiWorld.applyPendingInitialStateIfNeeded(player.level().getServer(), world);
        }

        CompoundTag playerNbt = new CompoundTag();

        ValueInput nbtReadView = TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), playerNbt);
        player.load(nbtReadView);
        player.loadAndSpawnEnderPearls(nbtReadView);

        player.setRespawnPosition(null, false);

        ServerStatsCounter statHandler = player.getStats();
        for (Stat<?> stat : ((StatsCounterAccessor) statHandler).getStatMap().keySet()) {
            player.resetStat(stat);
        }
        statHandler.markAllDirty();

        JsonObject clearedAdvancementsData = new JsonObject();
        JsonObject advancementsData = new JsonObject();
        try {
            Files.createDirectories(advancementsPath.getParent());
        } catch (Exception e) {
            LOGGER.error("Error creating advancements directory: {}", e.getMessage());
        }
        try (FileReader advancementsReader = new FileReader(advancementsPath.toFile())) {
            JsonElement parsed = JsonParser.parseReader(advancementsReader);
            if (parsed != null && parsed.isJsonObject()) {
                advancementsData = parsed.getAsJsonObject();
            }
        } catch (Exception ignored) {
        }
        if (advancementsData.has("DataVersion")) {
            clearedAdvancementsData.add("DataVersion", advancementsData.get("DataVersion"));
        }
        try (BufferedWriter advancementsWriter = Files.newBufferedWriter(advancementsPath)) {
            advancementsWriter.write(clearedAdvancementsData.toString());
        } catch (Exception e) {
            LOGGER.error("Error writing cleared advancements: {}", e.getMessage());
        }
        player.getAdvancements().reload(player.level().getServer().getAdvancements());

        String dimensionStr = world.equals("overworld") ? "minecraft:overworld" : NAMESPACE + ":" + world;
        String[] strArr = dimensionStr.split(":");
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(strArr[0], strArr[1]));

        MinecraftServer server = player.level().getServer();
        ServerLevel serverWorld = server.getLevel(key);

        if (serverWorld != null) {
            BlockPos spawnPos = serverWorld.getRespawnData().pos();
            Vec3 worldSpawn = Vec3.atBottomCenterOf(spawnPos);
            player.teleport(new TeleportTransition(serverWorld, worldSpawn, Vec3.ZERO, 0.0F, 0.0F,
                    TeleportTransition.DO_NOTHING));
            syncCustomWorldTimeToClient(player);

            GameType gameMode = ((ServerLevelAccessor) serverWorld).getWorldProperties().getGameType();
            player.setGameMode(gameMode == GameType.CREATIVE ? GameType.SURVIVAL : GameType.CREATIVE);
            player.setGameMode(gameMode);

            forceAdvancementClientRefresh(player, true);
        }
    }

    private static void forceAdvancementClientRefresh(ServerPlayer player, boolean hardResetClientCache) {
        forceAdvancementClientRefreshNow(player, hardResetClientCache);

        MinecraftServer server = player.level().getServer();
        Arrays.asList(1, 5, 20).forEach(offset -> {
            int refreshTick = server.getTickCount() + offset;
            server.schedule(new TickTask(refreshTick, () -> {
                ServerPlayer currentPlayer = server.getPlayerList().getPlayer(player.getUUID());
                if (currentPlayer != null) {
                    forceAdvancementClientRefreshNow(currentPlayer, false);
                }
            }));
        });
    }

    private static void forceAdvancementClientRefreshNow(ServerPlayer player, boolean hardResetClientCache) {
        if (hardResetClientCache) {
            player.connection.send(new ClientboundUpdateAdvancementsPacket(
                    true,
                    List.of(),
                    Set.of(),
                    Map.<Identifier, AdvancementProgress>of(),
                    true));
            player.connection.send(new ClientboundSelectAdvancementsTabPacket(null));
        }

        PlayerAdvancementsAccessor advancements = (PlayerAdvancementsAccessor) player.getAdvancements();
        player.level().getServer().getAdvancements().getAllAdvancements()
                .forEach(advancements::invokeMarkForVisibilityUpdate);
        advancements.setFirstPacket(true);
        player.getAdvancements().setSelectedTab(null);
        player.getAdvancements().flushDirty(player, true);
    }

    private static void syncCustomWorldTimeToClient(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        if (!NAMESPACE.equals(level.dimension().identifier().getNamespace())) {
            return;
        }

        player.connection.send(MultiWorld.createTimeSyncPacketForLevel(level));

        MinecraftServer server = level.getServer();
        int syncTick = server.getTickCount() + 1;
        server.schedule(new TickTask(syncTick, () -> {
            ServerPlayer currentPlayer = server.getPlayerList().getPlayer(player.getUUID());
            if (currentPlayer != null
                    && NAMESPACE.equals(currentPlayer.level().dimension().identifier().getNamespace())) {
                ServerLevel currentLevel = (ServerLevel) currentPlayer.level();
                currentPlayer.connection.send(MultiWorld.createTimeSyncPacketForLevel(currentLevel));
            }
        }));
    }
}
