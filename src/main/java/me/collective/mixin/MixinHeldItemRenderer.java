package me.collective.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.collective.event.EventBus;
import me.collective.event.events.RenderHeldItemEvent;
import me.collective.module.render.HandsShader;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class MixinHeldItemRenderer {

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void collective$onRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue renderCommands, int light, CallbackInfo ci) {
        EventBus.INSTANCE.post(new RenderHeldItemEvent(player, hand, item, matrices, renderCommands, light));
    }

    /**
     * Ваниль 1.21.11 выгружает очередь команд рук прямо внутри
     * HeldItemRenderer.renderItem (вызов entityRenderDispatcher.render() в его
     * теле), а не вторым dispatcher.render() из GameRenderer.renderWorld —
     * поэтому окно HandsShader размечаем здесь.
     */
    @WrapOperation(
        method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
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
