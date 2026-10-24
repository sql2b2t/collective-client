package me.collective.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.collective.event.EventBus;
import me.collective.event.events.RenderHeldItemEvent;
import me.collective.module.render.HandsShader;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class MixinHeldItemRenderer {

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void collective$onRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, OrderedRenderCommandQueue renderCommands, int light, CallbackInfo ci) {
        EventBus.INSTANCE.post(new RenderHeldItemEvent(player, hand, item, matrices, renderCommands, light));
    }

    /**
     * Ваниль 1.21.11 выгружает очередь команд рук прямо внутри
     * HeldItemRenderer.renderItem (вызов entityRenderDispatcher.render() в его
     * теле), а не вторым dispatcher.render() из GameRenderer.renderWorld —
     * поэтому окно HandsShader размечаем здесь.
     */
    @WrapOperation(
        method = "renderItem(FLnet/minecraft/client/util/math/PoseStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/LocalPlayer;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/RenderDispatcher;render()V")
    )
    private void collective$shadeHandFlush(RenderDispatcher dispatcher, Operation<Void> original) {
        HandsShader.beginHandPass();
        try {
            original.call(dispatcher);
        } finally {
            HandsShader.endHandPass();
        }
    }
}