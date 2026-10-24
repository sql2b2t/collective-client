package me.collective.mixin;

import me.collective.module.movement.ElytraBounce;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraFeatureRenderer.class)
public abstract class MixinElytraFeatureRenderer {
    @Inject(method = "render(Lnet/minecraft/client/util/math/PoseStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
        at = @At("HEAD"), cancellable = true)
    private void collective$hideOwnWings(PoseStack matrices, OrderedRenderCommandQueue queue, int light,
        BipedEntityRenderState state, float tickDelta, float tickProgress, CallbackInfo ci) {
        if (ElytraBounce.lookLikeFalling() && Minecraft.getInstance().player != null) {
            if (me.collective.emotes.EmoteEngine.isLocal(state)) {
                ci.cancel();
            }
        }
    }
}