package fr.jeanney.mixin;

import fr.jeanney.MultiWorld;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Final
    @Shadow
    private List<ServerPlayer> players;

    @Redirect(method = "sendLevelInfo", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private ServerClockManager useTargetLevelClockManager(MinecraftServer server, ServerPlayer player,
            ServerLevel level) {
        return MultiWorld.getClockManagerForLevel(level);
    }

    @Inject(method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void broadcastPerWorldTimeAndWeatherPackets(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ClientboundSetTimePacket) {
            for (ServerPlayer player : this.players) {
                ServerLevel level = player.level();
                player.connection.send(MultiWorld.createTimeSyncPacketForLevel(level));
            }
            ci.cancel();
            return;
        }

        if (packet instanceof ClientboundGameEventPacket gameEventPacket
                && MultiWorld.isWeatherGameEvent(gameEventPacket.getEvent())) {
            for (ServerPlayer player : this.players) {
                ServerLevel level = player.level();
                player.connection.send(MultiWorld.createWeatherSyncPacketForLevel(level, gameEventPacket.getEvent()));
            }
            ci.cancel();
        }
    }
}
