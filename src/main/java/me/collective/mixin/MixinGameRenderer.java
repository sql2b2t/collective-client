package me.collective.mixin;

import me.collective.module.render.HandsShader;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.command.RenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    /**
     * The deferred hand command batch built by renderFirstPersonItem is executed
     * by RenderDispatcher.render() right after renderHand returns, inside
     * renderWorld. Scope that exact call with the HandsShader hand-pass flag so
     * vertex decorations apply only to first-person hands and held items.
     */
    @WrapOperation(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/RenderDispatcher;render()V"))
    private void collective$shadeHandsPass(RenderDispatcher dispatcher, Operation<Void> original) {
        HandsShader handShader = HandsShader.INSTANCE;
        if (handShader != null && handShader.isToggled()) {
            HandsShader.beginHandPass();
        }
        try {
            original.call(dispatcher);
        } finally {
            HandsShader.endHandPass();
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void collective$onTiltViewWhenHurt(net.minecraft.client.util.math.MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        me.collective.module.render.NoHurtCam noHurtCam = me.collective.module.render.NoHurtCam.INSTANCE;
        if (noHurtCam != null && noHurtCam.isToggled()) {
            ci.cancel();
        }
    }
}
