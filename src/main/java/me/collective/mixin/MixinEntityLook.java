package me.collective.mixin;

import me.collective.module.render.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Во фрикаме мышь крутит камеру, а не игрока. */
@Mixin(Entity.class)
public abstract class MixinEntityLook {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void collective$freecamLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) return;

        me.collective.module.render.FreeLook freeLook = me.collective.module.render.FreeLook.INSTANCE;
        if (freeLook != null && freeLook.isToggled()) {
            freeLook.turn(cursorDeltaX, cursorDeltaY);
            ci.cancel();
            return;
        }

        Freecam freecam = Freecam.INSTANCE;
        if (freecam != null && freecam.isToggled()) {
            freecam.turn(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}