package me.collective.mixin;

import me.collective.module.movement.ElytraBounce;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraFeatureRenderer.class)
public abstract class MixinElytraFeatureRenderer {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
        at = @At("HEAD"), cancellable = true)
    private void collective$hideOwnWings(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
        BipedEntityRenderState state, float tickDelta, float tickProgress, CallbackInfo ci) {
        if (ElytraBounce.lookLikeFalling() && MinecraftClient.getInstance().player != null) {
            if (me.collective.emotes.EmoteEngine.isLocal(state)) {
                ci.cancel();
            }
        }
    }
}
