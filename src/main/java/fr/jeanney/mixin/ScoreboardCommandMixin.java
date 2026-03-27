package fr.jeanney.mixin;

import fr.jeanney.MultiWorld;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.commands.ScoreboardCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ScoreboardCommand.class)
public abstract class ScoreboardCommandMixin {

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"))
    private static ServerScoreboard routeScoreboardToWorldContext(MinecraftServer server, CommandSourceStack source) {
        return MultiWorld.getScoreboardForSource(source, server);
    }
}
