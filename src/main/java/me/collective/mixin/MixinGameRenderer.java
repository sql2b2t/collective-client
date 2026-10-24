package me.collective.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void collective$onBobHurt(CameraRenderState state, PoseStack matrices, CallbackInfo ci) {
        me.collective.module.render.NoHurtCam noHurtCam = me.collective.module.render.NoHurtCam.INSTANCE;
        if (noHurtCam != null && noHurtCam.isToggled()) {
            ci.cancel();
        }
    }
}
