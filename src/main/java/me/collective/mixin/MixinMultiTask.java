package me.collective.mixin;

import me.collective.module.ModuleManager;
import me.collective.module.player.MultiTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Часть порта MultiTask из Meteor Client.
 *
 * <p>{@code Minecraft.continueAttack} не даёт копать во время
 * использования предмета (еды). Подменяем проверку — можно есть и копать
 * одновременно.
 */
@Mixin(Minecraft.class)
public abstract class MixinMultiTask {

    @Redirect(
        method = "continueAttack",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z")
    )
    private boolean allowBreakingWhileUsing(LocalPlayer player) {
        MultiTask mod = ModuleManager.INSTANCE.getModule(MultiTask.class);
        if (mod != null && mod.isToggled()) return false;
        return player.isUsingItem();
    }
}