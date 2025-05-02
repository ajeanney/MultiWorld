package com.gmail.anthony17j.creativeworld;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.gmail.anthony17j.creativeworld.util.Json;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.gmail.anthony17j.creativeworld.CreativeWorld.LOGGER;

public class Utils {
    public static void saveInv(ServerPlayerEntity player,String world) {
        // LOGGER.info("Saving player data for {} in world {}", player.getName().getString(), world);
        try {
            // create a writer
            if (new File("mods/creativeworld/" + world).mkdirs()) {
                LOGGER.info("Directory created: mods/creativeworld/{}", world);
            }
            BufferedWriter writer = Files.newBufferedWriter(Paths.get("mods/creativeworld/" + world + "/" + player.getUuidAsString() + ".json"));

            NbtCompound tag = new NbtCompound();
            player.writeNbt(tag);

            // Remove ender pearls the player had launched (they will be put back on load)
            player.getEnderPearls().forEach((enderPearl) -> enderPearl.remove(Entity.RemovalReason.DISCARDED));

            // Remove player vehicle (will be put back on load)
            if (player.getRootVehicle() != player) {
                player.getRootVehicle().remove(Entity.RemovalReason.DISCARDED);
            }

            Json uuid = new Json(tag.toString());

            // convert Json list to JSON and write to file.json
            Jsoner.serialize(uuid, writer);

            // close the writer
            writer.close();

        } catch (Exception ex) {
            LOGGER.error("Error saving player data: {}", ex.getMessage());
        }
    }

    public static void loadInv(ServerPlayerEntity player, String world) throws CommandSyntaxException {
        // LOGGER.info("Loading player data for {} in world {}", player.getName().getString(), world);
        try (FileReader fileReader = new FileReader("mods/creativeworld/" + world + "/" + player.getUuidAsString() + ".json")) {
            JsonObject file = (JsonObject) Jsoner.deserialize(fileReader);

            String playerString = (String) file.get("player");
            NbtCompound playerNbt = StringNbtReader.readCompound(playerString);

            player.readNbt(playerNbt);
            player.readGameModeNbt(playerNbt);
            player.readEnderPearls(playerNbt);
            player.getAbilities().readNbt(playerNbt);

            String[] strArr = playerNbt.getString("Dimension").orElse("creativeworld:creative").split(":");
            RegistryKey<DimensionOptions> DIMENSION_KEY = RegistryKey.of(RegistryKeys.DIMENSION, Identifier.of(strArr[0], strArr[1]));
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, DIMENSION_KEY.getValue());
            ServerWorld dimension = player.getEntityWorld().getServer().getWorld(key);

            player.teleport(
                dimension,
                player.getX(),
                player.getY(),
                player.getZ(),
                PositionFlag.getFlags(0),
                player.getYaw(),
                player.getPitch(),
                false
            );

            player.readRootVehicle(playerNbt);
        } catch (Exception ex) {
            NbtCompound playerNbt = new NbtCompound();

            player.readNbt(playerNbt);
            player.readGameModeNbt(playerNbt);
            player.readEnderPearls(playerNbt);
            player.readCustomDataFromNbt(playerNbt);
            player.getAbilities().readNbt(playerNbt);

            // Teleport
            String dimension = world.equals("creative") ? "creativeworld:creative" : "minecraft:overworld";
            String[] strArr = dimension.split(":");
            RegistryKey<DimensionOptions> DIMENSION_KEY = RegistryKey.of(RegistryKeys.DIMENSION, Identifier.of(strArr[0], strArr[1]));
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, DIMENSION_KEY.getValue());
            ServerWorld worldDim = player.getEntityWorld().getServer().getWorld(key);
            BlockPos spawnPos = world.equals("creative") ? new BlockPos(0, 63, 0) : worldDim.getSpawnPos();
            player.teleport(worldDim,spawnPos.getX(),spawnPos.getY(),spawnPos.getZ(), PositionFlag.getFlags(0),0,0,true);

            // Do it twice to prevent a bug where the player must log out and back in to change gamemode
            player.changeGameMode(world.equals("creative") ? GameMode.SURVIVAL : GameMode.CREATIVE);
            player.changeGameMode(world.equals("creative") ? GameMode.CREATIVE : GameMode.SURVIVAL);
        }
    }
}
