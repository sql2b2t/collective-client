package me.collective.mixin;

import me.collective.module.movement.ElytraBounce;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    @Inject(method = "getLyingPositionRotationDegrees", at = @At("RETURN"), cancellable = true)
    private void collective$standUprightWhileFlying(CallbackInfoReturnable<Float> cir) {
        if (ElytraBounce.lookLikeFalling()) {
            cir.setReturnValue(0.0F);
        }
    }
}