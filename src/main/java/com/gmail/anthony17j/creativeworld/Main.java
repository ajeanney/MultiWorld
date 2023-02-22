package com.gmail.anthony17j.creativeworld;

import com.gmail.anthony17j.creativeworld.command.creativeCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main implements ModInitializer {

	public static final RegistryKey<DimensionOptions> DIMENSION_KEY = RegistryKey.of(
			RegistryKeys.DIMENSION,
			new Identifier("creativeworld", "creative")
	);

	public static RegistryKey<World> CREATIVE_KEY = RegistryKey.of(
			RegistryKeys.WORLD,
			DIMENSION_KEY.getValue()
	);

	private static final RegistryKey<DimensionType> DIMENSION_TYPE_KEY = RegistryKey.of(
			RegistryKeys.DIMENSION_TYPE,
			new Identifier("creativeworld", "creative")
	);

	private static final Logger LOGGER = LogManager.getLogger(Main.class);

	@Override
	public void onInitialize() {
		Main.CREATIVE_KEY = RegistryKey.of(RegistryKeys.WORLD, new Identifier("creativeworld", "creative"));
		Registry.register(Registries.CHUNK_GENERATOR, new Identifier("creativeworld", "creative"), VoidChunkGenerator.CODEC);

		CommandRegistrationCallback.EVENT.register(creativeCommand::register);

		/*ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			LOGGER.info("Moved player {}: [{} -> {}]", player, origin.getRegistryKey().getValue(), destination.getRegistryKey().getValue());
			if (destination.getRegistryKey().getValue() == CREATIVE_KEY.getValue()) {
				player.sendMessage(new LiteralText("You are in the creative world!"), false);
			}
		});*/

		ServerPlayerEvents.AFTER_RESPAWN.register(((oldPlayer, newPlayer, alive) -> {
			//LOGGER.info("Player Respawn - old:{} new:{} alive:{}", oldPlayer.getServerWorld().getRegistryKey().getValue(), newPlayer.getServerWorld().getRegistryKey().getValue(), alive);
			if (oldPlayer.getEntityWorld().getRegistryKey() == CREATIVE_KEY) {
				newPlayer.teleport((ServerWorld) oldPlayer.getEntityWorld(),0,63,0,0,0);
			}
		}));
	}
}