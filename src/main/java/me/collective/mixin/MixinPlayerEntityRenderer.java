package me.collective.mixin;

import me.collective.module.movement.ElytraBounce;
import me.collective.module.render.NameTags;
import me.collective.util.RotationUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class MixinPlayerEntityRenderer {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void collective$updateRenderState(
        PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!(player instanceof PlayerEntity self)) return;
        if (self != MinecraftClient.getInstance().player) return;

        // ShowChestplate: render chestplate instead of elytra
        if (ElytraBounce.showChestplate()) {
            ItemStack chest = self.getEquippedStack(EquipmentSlot.CHEST);
            if (!chest.isEmpty() && (chest.isOf(Items.ELYTRA) || chest.getItem().getName().getString().toLowerCase().contains("elytra"))) {
                state.equippedChestStack = new ItemStack(Items.IRON_CHESTPLATE);
            }
        }

        // LookLikeFalling: reset gliding flag
        if (ElytraBounce.lookLikeFalling()) {
            state.isGliding = false;
            state.glidingTicks = 0.0F;
        }

        // F5 Rotations visualizer: align third person view with server-side rotation
        RotationUtils rot = RotationUtils.getInstance();
        if (rot.isRotating()) {
            float yaw = MathHelper.lerpAngleDegrees(tickDelta, rot.getPrevSentYaw(), rot.getSentYaw());
            float pitch = MathHelper.lerp(tickDelta, rot.getPrevSentPitch(), rot.getSentPitch());
            state.pitch = pitch;
            state.bodyYaw = yaw;
            state.relativeHeadYaw = 0.0F;
        }
    }

    // HideVanillaLabel: при включённом NameTags ванильный неймтег не рисуется
    @Inject(
        method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void collective$hideVanillaLabel(
        PlayerEntityRenderState state, MatrixStack matrices,
        OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        NameTags nametags = NameTags.INSTANCE;
        if (nametags != null && nametags.isToggled()) ci.cancel();
    }
}
