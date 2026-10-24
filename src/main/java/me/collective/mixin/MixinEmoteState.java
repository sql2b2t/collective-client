package me.collective.mixin;

import me.collective.emotes.EmoteEngine;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class MixinEmoteState {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void collective$mapEmoteState(Avatar entity, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        if (entity == null) return;
        // Мапим ВСЕХ: локальный игрок нужен для своих эмоутов, чужие — для
        // эмоутов из релея IRC (EmoteEngine различает по STATE_UUID).
        EmoteEngine.mapState(state, entity.getUUID());
    }
}