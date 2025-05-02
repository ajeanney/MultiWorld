package com.gmail.anthony17j.creativeworld;

import com.gmail.anthony17j.creativeworld.command.creativeCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreativeWorld implements ModInitializer {

	public static final RegistryKey<DimensionOptions> DIMENSION_KEY = RegistryKey.of(
			RegistryKeys.DIMENSION,
			Identifier.of("creativeworld", "creative")
	);

	public static RegistryKey<World> CREATIVE_KEY = RegistryKey.of(
			RegistryKeys.WORLD,
			DIMENSION_KEY.getValue()
	);

	public static MinecraftServer server;

	public static final Logger LOGGER = LoggerFactory.getLogger(CreativeWorld.class);

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register((MinecraftServer s) -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED .register((MinecraftServer s) -> server = null  );

		CreativeWorld.CREATIVE_KEY = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("creativeworld", "creative"));
		Registry.register(Registries.CHUNK_GENERATOR, Identifier.of("creativeworld", "creative"), VoidChunkGenerator.CODEC);

		CommandRegistrationCallback.EVENT.register(creativeCommand::register);

		/*ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			LOGGER.info("Moved player {}: [{} -> {}]", player, origin.getRegistryKey().getValue(), destination.getRegistryKey().getValue());
			if (destination.getRegistryKey().getValue() == CREATIVE_KEY.getValue()) {
				player.sendMessage(new LiteralText("You are in the creative world!"), false);
			}
		});*/

		ServerPlayerEvents.AFTER_RESPAWN.register(((oldPlayer, newPlayer, alive) -> {
			if (oldPlayer.getEntityWorld().getRegistryKey() == CREATIVE_KEY) {
				newPlayer.teleport((ServerWorld) oldPlayer.getEntityWorld(), 0, 63, 0, PositionFlag.getFlags(0), 0, 0, true);
			}
		}));
	}
}