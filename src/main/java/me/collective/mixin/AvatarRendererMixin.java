package me.collective.mixin;

import me.collective.client.utils.math.MathConstants;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.collective.client.modules.render.AnimationsModule;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.render.EntityRenderStateAccess;
import me.collective.client.events.NameTagRenderEvent;
import me.collective.client.render.ClientRenderTypes;
import me.collective.client.modules.movement.ElytraFlyModule;
import me.collective.client.modules.render.NoRenderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(PlayerRenderer.class)
public abstract class AvatarRendererMixin extends LivingEntityRenderer {
   private static AnimationsModule avatarAnimationsModule;
   private static NoRenderModule noRenderModule;
   private static ElytraFlyModule elytraFlyModule;
   private static AnimationsModule avatarAnimationsModule() {
      if (avatarAnimationsModule == null) {
         avatarAnimationsModule = ClientContext.getModule(AnimationsModule.class);
      }
      return avatarAnimationsModule;
   }
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   private static ElytraFlyModule elytraFlyModule() {
      if (elytraFlyModule == null) {
         elytraFlyModule = ClientContext.getModule(ElytraFlyModule.class);
      }
      return elytraFlyModule;
   }
   @Unique
   AbstractClientPlayer cachedScalingPlayer;

   public AvatarRendererMixin(Context context, PlayerModel playerModel, float shadowRadius) {
      super(context, playerModel, shadowRadius);
   }

   @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
   private void applyRemotePlayerSneakOffset(
      PlayerRenderState renderState, CallbackInfoReturnable<Vec3> callback
   ) {
      if (avatarAnimationsModule().isSneakingAnimationEnabled()
         && !ClientAccess.isLocalPlayer(
            EntityRenderStateAccess.getEntity(renderState)
         )) {
         callback.setReturnValue(new Vec3(0.0, renderState.scale * -2.0F / 16.0, 0.0));
      }
   }

   @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"))
   private void captureScalingPlayer(
      PlayerRenderState renderState, PoseStack poses, CallbackInfo callback
   ) {
      if (EntityRenderStateAccess.getEntity(renderState) instanceof AbstractClientPlayer scalingPlayer) {
         this.cachedScalingPlayer = scalingPlayer;
      }
   }

   @ModifyArgs(method = "scale", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
   private void applyPlayerScaleAndSneakState(
      Args arguments, @Local(argsOnly = true) PlayerRenderState renderState
   ) {
      if (avatarAnimationsModule().isToggled()
         && avatarAnimationsModule().isPlayerScalingEnabled()
         && this.cachedScalingPlayer != Minecraft.getInstance().player) {
         arguments.set(
            0,
            arguments.<Float>get(0)
               * avatarAnimationsModule().playerScaleSetting.getValue()
         );
         arguments.set(
            1,
            arguments.<Float>get(1)
               * avatarAnimationsModule().playerScaleSetting.getValue()
         );
         arguments.set(
            2,
            arguments.<Float>get(2)
               * avatarAnimationsModule().playerScaleSetting.getValue()
         );
      }

      if (avatarAnimationsModule().isSneakingAnimationEnabled()
         && !ClientAccess.isLocalPlayer(
            this.cachedScalingPlayer
         )) {
         renderState.isCrouching = true;
         renderState.isDiscrete = true;
      }
   }

   @ModifyArgs(method = "setupRotations", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V", ordinal = 0))
   private void applySilentElytraPitch(Args arguments) {
      if (elytraFlyModule().isToggled()) {
         if (elytraFlyModule().modeSetting.getValue()
               == ElytraFlyModule.ModeChoice.BOUNCE
            && elytraFlyModule().canStartFlightFromJump()
            && elytraFlyModule().pitchLockSetting.getValue()
            && elytraFlyModule().silentSetting.getValue()) {
            arguments.set(
               0,
               Axis.XP
                  .rotationDegrees(-MathConstants.FORTY_FIVE_DEGREES)
            );
         }
      }
   }

   @ModifyExpressionValue(
      method = "renderHand",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;entityTranslucent(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;")
   )
   private RenderType applyAvatarHandShaderType(
      RenderType originalType, @Local(argsOnly = true) ResourceLocation texture
   ) {
      return noRenderModule().isToggled()
            && noRenderModule().handsSetting.getValue()
            && ClientContext.shaderRegistry.renderingIntoTarget
         ? RenderType.entitySolid(texture)
         : originalType;
   }

   @ModifyExpressionValue(
      method = "renderHand",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;entityTranslucent(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;")
   )
   private RenderType applyAvatarHandOpacity(
      RenderType originalType, @Local(argsOnly = true) ResourceLocation texture
   ) {
      return noRenderModule().getHandOpacity()
            != 1.0F
         ? ClientRenderTypes.entitySolid(texture)
         : originalType;
   }

   @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
   private void onAvatarNameTag(
      PlayerRenderState renderState, Component displayName, PoseStack poses, MultiBufferSource bufferSource, int packedLight, CallbackInfo callback
   ) {
      if (ClientContext.shaderRegistry.renderingIntoTarget) {
         callback.cancel();
         return;
      }

      Entity entity = EntityRenderStateAccess.getEntity(renderState);
      if (entity == null) {
         return;
      }
      NameTagRenderEvent event = new NameTagRenderEvent(poses, entity);
      ClientAccess.eventBus.post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Override
   protected float getShadowRadius(EntityRenderState renderState) {
      return super.getShadowRadius((LivingEntityRenderState)renderState);
   }
}
