package me.collective.mixin;

import me.collective.module.movement.ElytraBounce;
import me.collective.module.render.NameTags;
import me.collective.util.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class MixinPlayerEntityRenderer {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void collective$updateRenderState(
        PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!(player instanceof Player self)) return;
        if (self != Minecraft.getInstance().player) return;

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

        // ElytraBounce SmoothCamera in F5: the player model glides smoothly too,
        // without the jump arc — the same cutoff that the camera got.
        // The filter makes the frame advance in Camera.update, here we read it.
        ElytraBounce bounce = ElytraBounce.INSTANCE;
        if (bounce != null && bounce.isToggled() && bounce.smoothCamera.getValue()) {
            double realY = self.getLerpedPos(tickDelta).y;
            state.y -= bounce.smoothVisualDeltaY(realY, false);
        }
    }

    // HideVanillaLabel: при включённом NameTags ванильный неймтег не рисуется
    @Inject(
        method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/PoseStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void collective$hideVanillaLabel(
        PlayerEntityRenderState state, PoseStack matrices,
        OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        NameTags nametags = NameTags.INSTANCE;
        if (nametags != null && nametags.isToggled()) ci.cancel();
    }
}