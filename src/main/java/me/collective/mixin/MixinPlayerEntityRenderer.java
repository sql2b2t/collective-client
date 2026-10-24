package me.collective.mixin;

import me.collective.module.movement.ElytraBounce;
import me.collective.module.render.NameTags;
import me.collective.util.RotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
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

@Mixin(AvatarRenderer.class)
public abstract class MixinPlayerEntityRenderer {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void collective$updateRenderState(
        Avatar player, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        if (!(player instanceof Player self)) return;
        if (self != Minecraft.getInstance().player) return;

        // ShowChestplate: render chestplate instead of elytra
        if (ElytraBounce.showChestplate()) {
            ItemStack chest = self.getItemBySlot(EquipmentSlot.CHEST);
            if (!chest.isEmpty() && (chest.is(Items.ELYTRA) || chest.getHoverName().getString().toLowerCase().contains("elytra"))) {
                state.chestEquipment = new ItemStack(Items.IRON_CHESTPLATE);
            }
        }

        // LookLikeFalling: reset gliding flag
        if (ElytraBounce.lookLikeFalling()) {
            state.fallFlyingTimeInTicks = 0.0F;
        }

        // F5 Rotations visualizer: align third person view with server-side rotation
        RotationUtils rot = RotationUtils.getInstance();
        if (rot.isRotating()) {
            float yaw = Mth.rotLerp(tickDelta, rot.getPrevSentYaw(), rot.getSentYaw());
            float pitch = Mth.lerp(tickDelta, rot.getPrevSentPitch(), rot.getSentPitch());
            state.xRot = pitch;
            state.bodyRot = yaw;
            state.yRot = yaw;
        }

        // ElytraBounce SmoothCamera in F5: the player model glides smoothly too
        ElytraBounce bounce = ElytraBounce.INSTANCE;
        if (bounce != null && bounce.isToggled() && bounce.smoothCamera.getValue()) {
            double realY = self.position().y;
            state.y -= bounce.smoothVisualDeltaY(realY, false);
        }
    }

    // HideVanillaLabel: при включённом NameTags ванильный неймтег не рисуется
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void collective$hideVanillaLabel(CallbackInfo ci) {
        NameTags nametags = NameTags.INSTANCE;
        if (nametags != null && nametags.isToggled()) ci.cancel();
    }
}