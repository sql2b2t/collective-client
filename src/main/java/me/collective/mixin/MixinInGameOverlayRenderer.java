package me.collective.mixin;

import me.collective.module.render.NoRender;
import me.collective.module.render.TotemAnimation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class MixinInGameOverlayRenderer {

    @Shadow
    private ItemStack floatingItem;

    @Shadow
    private int floatingItemTimer;

    @Inject(method = "setFloatingItem", at = @At("HEAD"))
    private void collective$onSetFloatingItem(ItemStack floatingItem, Random random, CallbackInfo ci) {
        TotemAnimation mod = TotemAnimation.INSTANCE;
        if (mod != null && mod.isToggled()) {
            mod.showFloatingItem(floatingItem);
        }
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void collective$onRenderFloatingItem(MatrixStack matrices, float tickDelta, OrderedRenderCommandQueue renderCommands, CallbackInfo ci) {
        TotemAnimation mod = TotemAnimation.INSTANCE;
        if (mod != null && mod.isToggled()) {
            if (mod.renderCustomAnimation(matrices, tickDelta, renderCommands, this.floatingItem, this.floatingItemTimer)) {
                ci.cancel();
            }
        }
    }

    // ------------------------------------------------------------- NoRender

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void collective$noRenderFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
        NoRender mod = NoRender.INSTANCE;
        if (mod != null && mod.isToggled() && mod.fire.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void collective$noRenderUnderwater(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        NoRender mod = NoRender.INSTANCE;
        if (mod != null && mod.isToggled() && mod.water.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void collective$noRenderInWall(Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        NoRender mod = NoRender.INSTANCE;
        if (mod != null && mod.isToggled() && mod.inWall.getValue()) {
            ci.cancel();
        }
    }
}
