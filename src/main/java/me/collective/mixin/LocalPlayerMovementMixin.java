package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.LocalPlayerTickPhaseEvent;
import me.collective.client.utils.network.PacketSender;
import me.collective.client.managers.RotationManager;
import me.collective.client.access.LocalPlayerTickAccess;
import me.collective.client.events.EventStage;
import me.collective.client.modules.movement.EntityControlModule;
import me.collective.client.events.LocalPlayerAiStepEvent;
import me.collective.client.events.LocalPlayerMoveEvent;
import me.collective.client.events.LocalPlayerMoveTowardsClosestSpaceEvent;
import me.collective.client.events.LocalPlayerSendPositionEvent;
import me.collective.client.events.LocalPlayerTickEvent;
import me.collective.client.events.LocalPlayerPreTickEvent;
import me.collective.client.modules.movement.NoSlowModule;
import me.collective.client.modules.exploit.ReachModule;
import me.collective.client.modules.movement.SprintModule;
import me.collective.client.modules.misc.SwingModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMovementMixin extends AbstractClientPlayer implements ClientAccess, LocalPlayerTickAccess {
   private static SwingModule swingModule;
   private static NoSlowModule noSlowModule;
   private static EntityControlModule entityControlModule;
   private static SprintModule sprintModule;
   private static ReachModule reachModule;
   private static SwingModule swingModule() {
      if (swingModule == null) {
         swingModule = ClientContext.getModule(SwingModule.class);
      }
      return swingModule;
   }
   private static NoSlowModule noSlowModule() {
      if (noSlowModule == null) {
         noSlowModule = ClientContext.getModule(NoSlowModule.class);
      }
      return noSlowModule;
   }
   private static EntityControlModule entityControlModule() {
      if (entityControlModule == null) {
         entityControlModule = ClientContext.getModule(EntityControlModule.class);
      }
      return entityControlModule;
   }
   private static SprintModule sprintModule() {
      if (sprintModule == null) {
         sprintModule = ClientContext.getModule(SprintModule.class);
      }
      return sprintModule;
   }
   private static ReachModule reachModule() {
      if (reachModule == null) {
         reachModule = ClientContext.getModule(ReachModule.class);
      }
      return reachModule;
   }
   @Unique
   private LocalPlayerSendPositionEvent sendPositionEvent;
   @Shadow
   private float yRotLast;
   @Shadow
   private float xRotLast;

   public LocalPlayerMovementMixin(ClientLevel level, GameProfile profile) {
      super(level, profile);
   }

   @Shadow
   protected abstract void updateAutoJump(float deltaX, float deltaZ);

   @Shadow
   private void sendPosition() {
   }

   @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
   public void applySwingOverride(InteractionHand hand, CallbackInfo callback) {
      if (swingModule().isToggled()
         && swingModule().handSetting.getValue()
            != SwingModule.HandChoice.VANILLA) {
         if (swingModule().handSetting.getValue()
            != SwingModule.HandChoice.PACKET) {
            super.swing(
               (swingModule().handSetting
                     .getValue())
                  .getHand()
            );
         }

         PacketSender.send(new ServerboundSwingPacket(hand));
         callback.cancel();
      }
   }

   @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = Shift.BEFORE))
   public void postTickBeforeSuperclassTick(CallbackInfo callback) {
      if (ClientAccess.collectiveMinecraft.player != null
         && ClientAccess.collectiveMinecraft.level != null) {
         ClientAccess.eventBus.post(
            new LocalPlayerPreTickEvent()
         );
      }
   }

   @Inject(method = "tick", at = @At("RETURN"))
   public void postTickReturnEvent(CallbackInfo callback) {
      if (ClientAccess.collectiveMinecraft.player != null
         && ClientAccess.collectiveMinecraft.level != null) {
         ClientAccess.eventBus.post(
            new LocalPlayerTickEvent()
         );
      }
   }

   @Inject(method = "move", at = @At("HEAD"), cancellable = true)
   private void applyMoveEvent(
      MoverType moverType, Vec3 movement, CallbackInfo callback
   ) {
      Vec3 originalMovement = new Vec3(movement.x, movement.y, movement.z);
      LocalPlayerMoveEvent event = new LocalPlayerMoveEvent(movement, moverType);
      ClientAccess.eventBus.post(
         event
      );
      if (event.isCancelled()) {
         callback.cancel();
         return;
      }
      if (originalMovement.equals(event.getVelocity())) {
         return;
      }
      callback.cancel();
      double previousX = this.getX();
      double previousZ = this.getZ();
      super.move(event.getMoverType(), event.getVelocity());
      float deltaX = (float)(this.getX() - previousX);
      float deltaZ = (float)(this.getZ() - previousZ);
      super.addWalkedDistance(Mth.length(deltaX, deltaZ) * 0.6F);
      this.updateAutoJump(deltaX, deltaZ);
   }

   @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isPassenger()Z", shift = Shift.BEFORE))
   private void postBeforePassengerCheckEvent(CallbackInfo callback) {
      ClientAccess.eventBus.post(
         new LocalPlayerTickPhaseEvent.BeforePassengerCheck()
      );
   }

   @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = Shift.AFTER), cancellable = true)
   private void applyPreSendPositionEvent(CallbackInfo callback) {
      this.resetEvent();
      ClientAccess.eventBus.post(
         this.sendPositionEvent
      );
      if (this.sendPositionEvent.isCancelled()) {
         callback.cancel();
      } else {
         this.setPos(
            this.sendPositionEvent
               .getX(),
            this.sendPositionEvent
               .getY(),
            this.sendPositionEvent
               .getZ()
         );
         this.setYRot(
            this.sendPositionEvent
               .getCurrentYaw()
         );
         this.setXRot(
            this.sendPositionEvent
               .getCurrentPitch()
         );
         this.setOnGround(
            this.sendPositionEvent
               .isOnGround()
         );
      }

      ClientAccess.eventBus.post(
         new LocalPlayerTickPhaseEvent.LocalPlayerTickEvent()
      );
   }

   @Inject(
      method = "tick",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;ambientSoundHandlers:Ljava/util/List;")
   )
   private void restorePreSendPositionState(CallbackInfo callback) {
      if (this.sendPositionEvent
               .getX()
            != this.sendPositionEvent
               .getOriginalX()
         || this.sendPositionEvent
               .getY()
            != this.sendPositionEvent
               .getOriginalY()
         || this.sendPositionEvent
               .getZ()
            != this.sendPositionEvent
               .getOriginalZ()) {
         this.setPos(
            new Vec3(
               this.sendPositionEvent
                  .getOriginalX(),
               this.sendPositionEvent
                  .getOriginalY(),
               this.sendPositionEvent
                  .getOriginalZ()
            )
         );
      }

      this.resetRotations();
      if (this.onGround()
         == this.sendPositionEvent
            .isOnGround()) {
         this.setOnGround(
            this.sendPositionEvent
               .wasOnGround()
         );
      }
   }

   @Inject(method = "sendPosition", at = @At("RETURN"))
   private void postSendPositionReturnEvent(CallbackInfo callback) {
      LocalPlayerSendPositionEvent event = new LocalPlayerSendPositionEvent(
         EventStage.Post, this.sendPositionEvent
      );
      if (this.sendPositionEvent.isCancelled()) {
         event.cancel();
      }

      ClientAccess.eventBus.post(
         event
      );
   }

   @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"))
   private boolean overrideSprintInput(boolean vanillaSprint) {
      return (sprintModule().isToggled() && sprintModule().shouldDeferToVanillaSprinting()) || vanillaSprint;
   }

   @ModifyArgs(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setSprinting(Z)V"), require = 0)
   private void forceSprintingWhenAllowed(Args arguments) {
      if (sprintModule().isToggled()
         && sprintModule().canStartSprinting()) {
         arguments.set(0, true);
      }
   }

   @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;canStartSprinting()Z"))
   private void postSprintAiStepEvent(CallbackInfo callback) {
      ClientAccess.eventBus.post(
         new LocalPlayerAiStepEvent()
      );
   }

   @Inject(method = "moveTowardsClosestSpace(DD)V", at = @At("HEAD"), cancellable = true)
   private void cancelMoveTowardsClosestSpace(
      double x, double z, CallbackInfo callback
   ) {
      LocalPlayerMoveTowardsClosestSpaceEvent event = new LocalPlayerMoveTowardsClosestSpaceEvent();
      ClientAccess.eventBus.post(
         event
      );
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "getJumpRidingScale", at = @At("HEAD"), cancellable = true)
   private void maximizeRidingJumpScale(CallbackInfoReturnable<Float> callback) {
      if (entityControlModule().isToggled()
         && entityControlModule().horseJumpSetting.getValue()) {
         callback.setReturnValue(1.0F);
      }
   }

   @Inject(method = "handlePortalTransitionEffect", at = @At("HEAD"))
   private void suppressPortalTransitionEffect(CallbackInfo callback) {
      if (noSlowModule().isToggled() && this.portalProcess != null) {
         this.portalProcess.setAsInsidePortalThisTick(false);
      }
   }

   @Inject(method = "isMovingSlowly", at = @At("HEAD"), cancellable = true)
   private void overrideMovingSlowly(CallbackInfoReturnable<Boolean> callback) {
      if (ClientAccess.collectiveMinecraft.player == null) {
         return;
      }
      boolean bypassCrawl = ClientAccess.collectiveMinecraft.player.isVisuallyCrawling()
         && noSlowModule().crawlSetting.getValue();
      boolean bypassSneak = !bypassCrawl
         && ClientAccess.collectiveMinecraft.player.isShiftKeyDown()
         && noSlowModule().sneakSetting.getValue();
      if (noSlowModule().isToggled() && (bypassCrawl || bypassSneak)) {
         callback.setReturnValue(false);
      }
   }

   @ModifyExpressionValue(method = "isSlowDueToUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
   private boolean overrideItemUseForSlowdown(boolean originalUsingItem) {
      return !(noSlowModule().isToggled() && noSlowModule().itemsSetting.getValue()) && originalUsingItem;
   }

   @Inject(
      method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"
      ),
      cancellable = true
   )
   private static void useBlockHitWhenEntityTraceIgnored(
      Entity entity, double blockRange, double entityRange, float partialTick, CallbackInfoReturnable<HitResult> callback, @Local HitResult blockHit
   ) {
      if (reachModule().shouldIgnoreEntityTrace()) {
         callback.setReturnValue(blockHit);
      }
   }

   @Override
   public void sendMovementPacketsWrapper() {
      this.sendPosition();
   }

   @Override
   public void superTick() {
      super.tick();
   }

   @Override
   public void resetEvent() {
      this.sendPositionEvent = new LocalPlayerSendPositionEvent(
         EventStage.Pre, this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), this.onGround()
      );
   }

   @Override
   public void collective$updateServer(float yaw, float pitch) {
      this.yRotLast = yaw;
      this.xRotLast = pitch;
   }

   @Override
   public void resetRotations() {
      RotationManager rotations = ClientContext.rotationManager;
      float renderYaw = this.isPassenger() && !this.sendPositionEvent.isModified()
         ? this.getVehicle().getYRot()
         : this.sendPositionEvent.getYaw();
      rotations.updateRenderRotationsIfNeeded(renderYaw, this.sendPositionEvent.getPitch());

      if (this.getYRot() == this.sendPositionEvent.getCurrentYaw()) {
         this.setYRot(this.sendPositionEvent.getOriginalYaw());
      }
      if (this.getXRot() == this.sendPositionEvent.getCurrentPitch()) {
         this.setXRot(this.sendPositionEvent.getOriginalPitch());
      }
   }
}
