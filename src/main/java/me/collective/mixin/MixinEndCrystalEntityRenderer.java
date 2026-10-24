package me.collective.mixin;

import me.collective.module.render.CrystalModifier;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndCrystalEntityRenderer.class)
public class MixinEndCrystalEntityRenderer {

    @Inject(
        method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V", shift = At.Shift.AFTER)
    )
    private void collective$onRenderScale(EndCrystalEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        CrystalModifier modifier = CrystalModifier.INSTANCE;
        if (modifier != null && modifier.isToggled()) {
            float s = modifier.scale.getValue().floatValue();
            if (s != 1.0f) {
                matrices.scale(s, s, s);
            }
        }
    }

    @Inject(method = "getYOffset", at = @At("HEAD"), cancellable = true)
    private static void collective$onGetYOffset(float f, CallbackInfoReturnable<Float> cir) {
        CrystalModifier modifier = CrystalModifier.INSTANCE;
        if (modifier != null && modifier.isToggled() && !modifier.bounce.getValue()) {
            cir.setReturnValue(0.0f);
        }
    }

    @Inject(
        method = "updateRenderState(Lnet/minecraft/entity/decoration/EndCrystalEntity;Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void collective$onUpdateRenderState(EndCrystalEntity entity, EndCrystalEntityRenderState state, float tickProgress, CallbackInfo ci) {
        CrystalModifier modifier = CrystalModifier.INSTANCE;
        if (modifier != null && modifier.isToggled()) {
            if (!modifier.rotate.getValue()) {
                state.age = 0.0f;
            }
            if (!modifier.base.getValue()) {
                state.baseVisible = false;
            }
            if (!modifier.beam.getValue()) {
                state.beamOffset = null;
            }
        }
    }
}
