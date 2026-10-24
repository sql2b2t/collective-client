package me.collective.mixin;

import me.collective.module.movement.ElytraBounce;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderState.class)
public abstract class MixinLivingEntityRenderer {
    @Inject(method = "fallFlyingScale", at = @At("HEAD"), cancellable = true)
    private void collective$standUprightWhileFlying(CallbackInfoReturnable<Float> cir) {
        if (ElytraBounce.lookLikeFalling()) {
            cir.setReturnValue(0.0F);
        }
    }
}