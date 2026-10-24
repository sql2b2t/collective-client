package me.collective.mixin;

import me.collective.module.render.NoRender;
import me.collective.module.render.TotemAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
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
        if (floatingItem != null && floatingItem.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) {
            me.collective.module.combat.Offhand offhand = me.collective.module.combat.Offhand.INSTANCE;
            if (offhand != null) {
                offhand.onTotemPop();
            }
        }
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void collective$onRenderFloatingItem(PoseStack matrices, float tickDelta, OrderedRenderCommandQueue renderCommands, CallbackInfo ci) {
        TotemAnimation mod = TotemAnimation.INSTANCE;
        if (mod != null && mod.isToggled()) {
            if (mod.renderCustomAnimation(matrices, tickDelta, renderCommands, this.floatingItem, this.floatingItemTimer)) {
                ci.cancel();
            }
        }
    }

    // ------------------------------------------------------------- NoRender

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void collective$noRenderFire(PoseStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
        NoRender mod = NoRender.INSTANCE;
        if (mod != null && mod.isToggled() && mod.fire.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void collective$noRenderUnderwater(Minecraft client, PoseStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        NoRender mod = NoRender.INSTANCE;
        if (mod != null && mod.isToggled() && mod.water.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void collective$noRenderInWall(Sprite sprite, PoseStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        NoRender mod = NoRender.INSTANCE;
        if (mod != null && mod.isToggled() && mod.inWall.getValue()) {
            ci.cancel();
        }
    }
}