package me.collective.mixin;

import me.collective.module.render.CrystalModifier;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndCrystalRenderer.class)
public class MixinEndCrystalEntityRenderer {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", shift = At.Shift.AFTER)
    )
    private void collective$onRenderScale(EndCrystalRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, CallbackInfo ci) {
        CrystalModifier modifier = CrystalModifier.INSTANCE;
        if (modifier != null && modifier.isToggled()) {
            float s = modifier.scale.getValue().floatValue();
            if (s != 1.0f) {
                matrices.scale(s, s, s);
            }
        }
    }

    @Inject(method = "getY", at = @At("HEAD"), cancellable = true)
    private static void collective$onGetY(float f, CallbackInfoReturnable<Float> cir) {
        CrystalModifier modifier = CrystalModifier.INSTANCE;
        if (modifier != null && modifier.isToggled() && !modifier.bounce.getValue()) {
            cir.setReturnValue(0.0f);
        }
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V",
        at = @At("TAIL")
    )
    private void collective$onExtractRenderState(EndCrystal entity, EndCrystalRenderState state, float tickProgress, CallbackInfo ci) {
        CrystalModifier modifier = CrystalModifier.INSTANCE;
        if (modifier != null && modifier.isToggled()) {
            if (!modifier.base.getValue()) {
                state.showsBottom = false;
            }
            if (!modifier.beam.getValue()) {
                state.beamOffset = null;
            }
        }
    }
}