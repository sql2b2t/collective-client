package me.collective.mixin;

import me.collective.module.movement.ElytraBounce;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public abstract class MixinSoundSystem {
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;", at = @At("HEAD"), cancellable = true)
    private void collective$cancelFakeFlySounds(SoundInstance sound, CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
        if (sound != null && sound.getId() != null && ElytraBounce.shouldCancelSound(sound.getId())) {
            cir.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
        }
    }
}