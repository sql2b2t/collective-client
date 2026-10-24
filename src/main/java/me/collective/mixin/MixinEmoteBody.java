package me.collective.mixin;

import me.collective.emotes.EmoteEngine;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class MixinEmoteBody {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void collective$emoteBodyTransform(AvatarRenderState state, PoseStack matrices, float f, float tickDelta, CallbackInfo ci) {
        EmoteEngine.applyBodyTransform(state, matrices);
    }
}