package me.collective.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    /**
     * Класс HandsShader больше не навешивает флаг на dispatcher.render() из
     * renderWorld: ваниль 1.21.11 выгружает очередь рук внутри
     * HeldItemRenderer.renderItem, и окно шейдинга размечается там
     * (MixinHeldItemRenderer).
     */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void collective$onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        me.collective.module.render.NoHurtCam noHurtCam = me.collective.module.render.NoHurtCam.INSTANCE;
        if (noHurtCam != null && noHurtCam.isToggled()) {
            ci.cancel();
        }
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void collective$onGetFov(net.minecraft.client.render.Camera camera, float tickDelta, boolean changingFov, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        me.collective.module.render.Zoom zoom = me.collective.module.render.Zoom.INSTANCE;
        if (zoom != null) {
            float modified = zoom.modifyFov(cir.getReturnValueF(), tickDelta);
            if (modified != cir.getReturnValueF()) {
                cir.setReturnValue(modified);
            }
        }
    }
}

