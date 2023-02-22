package com.gmail.anthony17j.creativeworld;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.gmail.anthony17j.creativeworld.util.Json;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

public class Utils {
    public static String effectsToString(StatusEffectInstance effectInstance) {
        NbtCompound tag = new NbtCompound();
        effectInstance.writeNbt(tag);
        return tag.toString();
    }

    public static void saveInv(ServerPlayerEntity player,String world) {
        try {
            // create a writer
            boolean dirCreated = new File("mods/creativeworld").mkdir();
            boolean dirCreated2 = new File("mods/creativeworld/" + world).mkdir();
            BufferedWriter writer = Files.newBufferedWriter(Paths.get("mods/creativeworld/" + world + "/" + player.getUuidAsString() + ".json"));

            //save inventory
            String[] playerInventoryS = new String[36];
            for (int i = 0; i < playerInventoryS.length; i++) {
                playerInventoryS[i] = String.valueOf(player.getInventory().main.get(i).writeNbt(new NbtCompound()));
            }

            //save armor
            String[] playerArmorS = new String[4];
            for (int i = 0; i < playerArmorS.length; i++) {
                playerArmorS[i] = String.valueOf(player.getInventory().armor.get(i).writeNbt(new NbtCompound()));
            }

            //save ender chest
            String[] playerEnderChestS = new String[27];
            for (int i = 0; i < playerEnderChestS.length; i++) {
                playerEnderChestS[i] = String.valueOf(player.getEnderChestInventory().getStack(i).writeNbt(new NbtCompound()));
            }

            //save offHand
            String playerOffHandS = player.getInventory().offHand.get(0).writeNbt(new NbtCompound()).toString();

            //save selected slot
            int playerSelectedSlotS = player.getInventory().selectedSlot;

            //save xp
            int playerXpS = player.experienceLevel;
            float playerXpProgressS = player.experienceProgress;

            //save food level
            NbtCompound tag = new NbtCompound();
            player.getHungerManager().writeNbt(tag);
            String playerFoodLevelS = tag.toString();

            //save health
            float playerHealthS = player.getHealth();

            //save effects
            ArrayList<String> effects = new ArrayList<>();
            for (StatusEffectInstance s : player.getStatusEffects()) {
                effects.add(Utils.effectsToString(s));
            }
            String playerEffectsS = effects.toString();

            //save pos
            double playerPosXS = player.getPos().getX();
            double playerPosYS = player.getPos().getY();
            double playerPosZS = player.getPos().getZ();
            float playerPitchS = player.getPitch();
            float playerYawS = player.getYaw();

            //save dimension
            String playerDimS = player.getEntityWorld().getRegistryKey().getValue().toString();

            //save Absorption
            float playerAbsorptionS = player.getAbsorptionAmount();

            //save Velocity
            double playerVelocityXS = player.getVelocity().getX();
            double playerVelocityYS = player.getVelocity().getY();
            double playerVelocityZS = player.getVelocity().getZ();

            //save gamemode
            String playerGamemodeS = player.interactionManager.getGameMode().toString();

            //save fly
            boolean playerFlyS = player.getAbilities().flying;

            //save fall
            float playerFallDistanceS = player.fallDistance;

            ////////////////////////////////////////////////////

            Json uuid = new Json(playerInventoryS,playerArmorS,playerEnderChestS,playerOffHandS,
                    playerSelectedSlotS,playerXpS,playerXpProgressS,playerFoodLevelS,playerHealthS,playerEffectsS,playerPosXS,playerPosYS,
                    playerPosZS,playerPitchS,playerYawS,playerDimS,playerAbsorptionS,playerVelocityXS,playerVelocityYS,playerVelocityZS,
                    playerGamemodeS,playerFlyS,playerFallDistanceS);

            // convert Json list to JSON and write to file.json
            Jsoner.serialize(uuid, writer);

            // close the writer
            writer.close();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void loadInv(ServerPlayerEntity player,String world) {
        try (FileReader fileReader = new FileReader("mods/creativeworld/" + world + "/" + player.getUuidAsString() + ".json")) {
            JsonObject file = (JsonObject) Jsoner.deserialize(fileReader);

            //set inventory
            JsonArray inventory = (JsonArray) file.get("inventory");
            for (int i = 0; i < 36; i++) {
                player.getInventory().main.set(i, ItemStack.fromNbt(StringNbtReader.parse((String) inventory.get(i))));
            }

            //set armor
            JsonArray armor = (JsonArray) file.get("armor");
            for (int i = 0; i < 4; i++) {
                player.getInventory().armor.set(i,ItemStack.fromNbt(StringNbtReader.parse((String) armor.get(i))));
            }

            //set ender chest
            JsonArray enderChest = (JsonArray) file.get("enderChest");
            for (int i = 0; i < 27; i++) {
                player.getEnderChestInventory().setStack(i,ItemStack.fromNbt(StringNbtReader.parse((String) enderChest.get(i))));
            }

            //set offHand
            player.getInventory().offHand.set(0,ItemStack.fromNbt(StringNbtReader.parse((String) file.get("offHand"))));

            //set selected slot
            player.getInventory().selectedSlot = Integer.parseInt(file.get("selectedSlot").toString());

            //set xp
            player.experienceLevel = Integer.parseInt(file.get("xp").toString());
            player.experienceProgress = Float.parseFloat(file.get("xpProgress").toString());
            player.addExperienceLevels(0);

            //set food level
            player.getHungerManager().readNbt(StringNbtReader.parse((String) file.get("foodLevel")));

            //set health
            player.setHealth(Float.parseFloat(file.get("health").toString()));

            //set effects
            player.clearStatusEffects();
            String str = (String) file.get("effects");
            if (!str.equals("[]")) {
                String[] strArr =  str.replace("[", "").replace("]", "").split(", ");
                for (String s : strArr) {
                    player.addStatusEffect(StatusEffectInstance.fromNbt(StringNbtReader.parse(s)));
                }
            }

            //set pos
            double posX = Double.parseDouble(file.get("posX").toString());
            double posY = Double.parseDouble(file.get("posY").toString());
            double posZ = Double.parseDouble(file.get("posZ").toString());
            float pitch = Float.parseFloat(file.get("pitch").toString());
            float yaw = Float.parseFloat(file.get("yaw").toString());
            String dimension = (String) file.get("dimension");
            String[] strArr = dimension.split(":");
            RegistryKey<DimensionOptions> DIMENSION_KEY = RegistryKey.of(RegistryKeys.DIMENSION, new Identifier(strArr[0], strArr[1]));
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, DIMENSION_KEY.getValue());
            ServerWorld creativeDim = (ServerWorld) player.getEntityWorld().getServer().getWorld(key);
            player.teleport(creativeDim,posX,posY,posZ,yaw,pitch);

            //set Absorption
            player.setAbsorptionAmount(Float.parseFloat(file.get("absorption").toString()));

            //set Velocity
            double velocityX = Double.parseDouble(file.get("velocityX").toString());
            double velocityY = Double.parseDouble(file.get("velocityY").toString());
            double velocityZ = Double.parseDouble(file.get("velocityZ").toString());
            player.setVelocity(velocityX,velocityY,velocityZ);
            player.setVelocityClient(velocityX,velocityY,velocityZ);

            //set gamemode
            player.changeGameMode(GameMode.valueOf((String) file.get("gamemode")));

            //set fly
            player.getAbilities().flying = Boolean.parseBoolean(file.get("fly").toString());
            player.sendAbilitiesUpdate();

            //set fall
            player.fallDistance = Float.parseFloat(file.get("fallDistance").toString());

        } catch (Exception ex) {

            //reset inventory
            for (int i = 0; i < 36; i++) {
                player.getInventory().main.set(i, ItemStack.EMPTY);
            }

            //reset armor
            for (int i = 0; i < 4; i++) {
                player.getInventory().armor.set(i,ItemStack.EMPTY);
            }

            //reset ender chest
            for (int i = 0; i < 27; i++) {
                player.getEnderChestInventory().setStack(i,ItemStack.EMPTY);
            }

            //reset offHand
            player.getInventory().offHand.set(0,ItemStack.EMPTY);

            //reset selected slot
            player.getInventory().selectedSlot = 0;

            //reset xp
            player.experienceLevel = 0;
            player.experienceProgress = 0;

            //reset health
            player.setHealth(20);

            //reset food level
            try {
                player.getHungerManager().readNbt(StringNbtReader.parse("{foodExhaustionLevel:4.0f,foodLevel:20,foodSaturationLevel:3.0f,foodTickTimer:0}"));
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }

            //reset effects
            player.clearStatusEffects();

            //reset Absorption
            player.setAbsorptionAmount(0);

            //reset Velocity
            player.setVelocity(0,0,0);
            player.setVelocityClient(0,0,0);

            //reset fly
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();

            //reset fall
            player.fallDistance = 0;

            String dimension;
            String gamemode;
            if (Objects.equals(world, "creative")) {
                dimension = "creativeworld:creative";
                gamemode = "CREATIVE";
            } else {
                dimension = "minecraft:overworld";
                gamemode = "SURVIVAL";
            }

            //reset gamemode
            player.changeGameMode(GameMode.valueOf(gamemode));

            //reset pos
            String[] strArr = dimension.split(":");
            RegistryKey<DimensionOptions> DIMENSION_KEY = RegistryKey.of(RegistryKeys.DIMENSION, new Identifier(strArr[0], strArr[1]));
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, DIMENSION_KEY.getValue());
            ServerWorld creativeDim = player.getEntityWorld().getServer().getWorld(key);
            player.teleport(creativeDim,0,63,0,0,0);
        }
    }
}
