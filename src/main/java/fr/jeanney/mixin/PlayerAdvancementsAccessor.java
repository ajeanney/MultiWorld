package fr.jeanney.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.nio.file.Path;

@Mixin(PlayerAdvancements.class)
public interface PlayerAdvancementsAccessor {
    @Accessor("playerSavePath")
    Path getPlayerSavePath();

    @Accessor("isFirstPacket")
    void setFirstPacket(boolean firstPacket);

    @Invoker("markForVisibilityUpdate")
    void invokeMarkForVisibilityUpdate(AdvancementHolder advancementHolder);
}
