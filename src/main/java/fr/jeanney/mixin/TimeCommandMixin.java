package fr.jeanney.mixin;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import fr.jeanney.MultiWorld;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.timeline.Timeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TimeCommand.class)
public abstract class TimeCommandMixin {
    @Redirect(method = "suggestTimeMarkers", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private static ServerClockManager redirectSuggestTimeMarkersClockManager(MinecraftServer server,
            CommandSourceStack source, SuggestionsBuilder builder, Holder<WorldClock> clock) {
        return MultiWorld.getClockManagerForSource(source, server);
    }

    @Redirect(method = "queryTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private static ServerClockManager redirectQueryTimeClockManager(MinecraftServer server, CommandSourceStack source,
            Holder<WorldClock> clock) {
        return MultiWorld.getClockManagerForSource(source, server);
    }

    @Redirect(method = "queryTimelineTicks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private static ServerClockManager redirectQueryTimelineTicksClockManager(MinecraftServer server,
            CommandSourceStack source, Holder<WorldClock> clock, Holder<Timeline> timeline) {
        return MultiWorld.getClockManagerForSource(source, server);
    }

    @Redirect(method = "queryTimelineRepetitions", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private static ServerClockManager redirectQueryTimelineRepetitionsClockManager(MinecraftServer server,
            CommandSourceStack source, Holder<WorldClock> clock, Holder<Timeline> timeline) {
        return MultiWorld.getClockManagerForSource(source, server);
    }

    @Redirect(method = "setTotalTicks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private static ServerClockManager redirectSetTotalTicksClockManager(MinecraftServer server,
            CommandSourceStack source, Holder<WorldClock> clock, int ticks) {
        return MultiWorld.getClockManagerForSource(source, server);
    }

    @Redirect(method = "addTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private static ServerClockManager redirectAddTimeClockManager(MinecraftServer server, CommandSourceStack source,
            Holder<WorldClock> clock, int ticks) {
        return MultiWorld.getClockManagerForSource(source, server);
    }

    @Redirect(method = "setTimeToTimeMarker", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private static ServerClockManager redirectSetTimeToTimeMarkerClockManager(MinecraftServer server,
            CommandSourceStack source, Holder<WorldClock> clock, ResourceKey<ClockTimeMarker> markerKey) {
        return MultiWorld.getClockManagerForSource(source, server);
    }

    @Redirect(method = "setPaused", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private static ServerClockManager redirectSetPausedClockManager(MinecraftServer server, CommandSourceStack source,
            Holder<WorldClock> clock, boolean paused) {
        return MultiWorld.getClockManagerForSource(source, server);
    }

    @Redirect(method = "setRate", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;clockManager()Lnet/minecraft/world/clock/ServerClockManager;"))
    private static ServerClockManager redirectSetRateClockManager(MinecraftServer server, CommandSourceStack source,
            Holder<WorldClock> clock, float rate) {
        return MultiWorld.getClockManagerForSource(source, server);
    }
}
