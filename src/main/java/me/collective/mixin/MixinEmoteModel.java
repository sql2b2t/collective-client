package me.collective.mixin;

import me.collective.emotes.EmoteEngine;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class MixinEmoteModel {

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void collective$applyEmotePose(HumanoidRenderState state, CallbackInfo ci) {
        EmoteEngine.applyToModel((HumanoidModel) (Object) this, state);
    }
}