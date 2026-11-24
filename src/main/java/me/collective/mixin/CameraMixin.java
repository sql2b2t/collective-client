package me.collective.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import me.collective.client.events.CameraSetupEvent;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.utils.movement.EntityMotionAccess;
import me.collective.client.render.RenderUtils;
import me.collective.client.modules.movement.ElytraFlyModule;
import me.collective.client.modules.render.FreeLookModule;
import me.collective.client.modules.player.FreecamModule;
import me.collective.client.modules.render.NoRenderModule;
import me.collective.client.modules.render.ViewClipModule;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {
   private static ViewClipModule viewClip;
   private static NoRenderModule noRenderModule;
   private static ElytraFlyModule elytrafly;
   private static FreecamModule freecam;
   private static FreeLookModule freelook;
   private static ViewClipModule viewClip() {
      if (viewClip == null) {
         viewClip = ClientContext.getModule(ViewClipModule.class);
      }
      return viewClip;
   }
   private static NoRenderModule noRenderModule() {
      if (noRenderModule == null) {
         noRenderModule = ClientContext.getModule(NoRenderModule.class);
      }
      return noRenderModule;
   }
   private static ElytraFlyModule elytrafly() {
      if (elytrafly == null) {
         elytrafly = ClientContext.getModule(ElytraFlyModule.class);
      }
      return elytrafly;
   }
   private static FreecamModule freecam() {
      if (freecam == null) {
         freecam = ClientContext.getModule(FreecamModule.class);
      }
      return freecam;
   }
   private static FreeLookModule freelook() {
      if (freelook == null) {
         freelook = ClientContext.getModule(FreeLookModule.class);
      }
      return freelook;
   }
   @Final
   @Shadow
   private Vector3f forwards;
   @Final
   @Shadow
   private Vector3f up;
   @Final
   @Shadow
   private Vector3f left;
   @Shadow
   private Vec3 position;
   @Shadow
   private float eyeHeight;
   @Shadow
   private float eyeHeightOld;
   @Shadow
   private Entity entity;
   @Shadow
   @Final
   private EnvironmentAttributeProbe attributeProbe;
   @Shadow
   private Level level;

   @Shadow
   protected void setPosition(Vec3 position) {
   }

   @Shadow
   protected abstract void setRotation(float yaw, float pitch);

   @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
   private void applyViewClipAndFreeLookDistance(
      float originalDistance, CallbackInfoReturnable<Float> callback, @Local(argsOnly = true) LocalFloatRef distanceRef
   ) {
      if (viewClip().isToggled()) {
         float cameraDistance = viewClip().getCameraDistance();
         if (freelook().isScrollEnabled()) {
            cameraDistance += freelook().getCameraDistanceOffset();
            cameraDistance = Mth.clamp(cameraDistance, 1.0F, 20.0F);
         }

         callback.setReturnValue(cameraDistance);
         return;
      }
      if (freelook().isScrollEnabled()) {
         distanceRef.set(
            Mth.clamp(
               distanceRef.get()
                  + freelook().getCameraDistanceOffset(),
               1.0F,
               20.0F
            )
         );
      }
   }

   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void applyLegacySneakingCamera(CallbackInfo callback) {
      if (!noRenderModule().isToggled() || !noRenderModule().newSneakingSetting.getValue() || this.entity == null) {
         return;
      }
      callback.cancel();
      this.eyeHeightOld = this.eyeHeight;
      if (this.entity instanceof Player cameraPlayer) {
         if (cameraPlayer.hasPose(Pose.CROUCHING)) {
            this.eyeHeight = 1.54F;
         } else if (this.eyeHeight < 1.62F && cameraPlayer.hasPose(Pose.STANDING)) {
            float heightDifference = 1.62F - this.eyeHeight;
            heightDifference *= 0.4F;
            this.eyeHeight = 1.62F - heightDifference;
         } else {
            this.eyeHeight = this.eyeHeight + (this.entity.getEyeHeight() - this.eyeHeight) * 0.5F;
         }
      }

      this.attributeProbe.tick(this.level, this.position);
   }

   @Inject(method = "move", at = @At("HEAD"), cancellable = true)
   private void applySmoothViewClipMove(float forward, float vertical, float sideways, CallbackInfo callback) {
      if (viewClip().isToggled() && viewClip().smoothSetting.getValue()) {
         double offsetX = (double)this.forwards.x() * forward + (double)this.up.x() * vertical + (double)this.left.x() * sideways;
         double offsetY = (double)this.forwards.y() * forward + (double)this.up.y() * vertical + (double)this.left.y() * sideways;
         double offsetZ = (double)this.forwards.z() * forward + (double)this.up.z() * vertical + (double)this.left.z() * sideways;
         float blend = viewClip().clipAnimation.updateAndGet();
         this.setPosition(new Vec3(this.position.x + offsetX * blend, this.position.y + offsetY * blend, this.position.z + offsetZ * blend));
         callback.cancel();
      }
   }

   @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
   private void modifySetupPosition(
      Args args, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true) float partialTick
   ) {
      CameraSetupEvent event = new CameraSetupEvent(
         entity,
         partialTick,
         args.<Double>get(0),
         args.<Double>get(1),
         args.<Double>get(2)
      );
      ClientAccess.eventBus
         .post(event);
      if (event.isCancelled()) {
         args.set(0, event.getPosition().x);
         args.set(1, event.getPosition().y);
         args.set(
            2,
            event.getPosition().z
         );
      }
   }

   @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
   private void applyCameraSetupRotations(Args arguments) {
      if (elytrafly().isToggled()
         && elytrafly().modeSetting.getValue() == ElytraFlyModule.ModeChoice.BOUNCE
         && elytrafly().pitchLockSetting.getValue()
         && !elytrafly().silentSetting.getValue()) {
         arguments.set(1, elytrafly().pitchSetting.getValue());
      }

      float partialTick = RenderUtils.getPartialTick();
      if (freecam().isToggled()) {
         arguments.set(
            0,
            (float)freecam().getInterpolatedYaw(partialTick)
         );
         arguments.set(
            1,
            (float)freecam().getInterpolatedPitch(partialTick)
         );
      }
   }

   @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1, shift = Shift.AFTER))
   public void applyFreeLookRotation(
      Level level, Entity cameraEntity, boolean detached, boolean mirrored, float partialTick, CallbackInfo callback
   ) {
      if (!freecam().isToggled() && freelook().isToggled() && cameraEntity instanceof EntityMotionAccess motionAccess) {
         this.setRotation(motionAccess.getCameraYaw(), motionAccess.getCameraPitch());
      }
   }
}
