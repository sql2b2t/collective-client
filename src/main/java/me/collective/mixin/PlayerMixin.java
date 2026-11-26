package me.collective.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.modules.misc.SocialEntry;
import me.collective.client.managers.PlayerTickExecutor;
import me.collective.client.utils.FloatAnimation;
import me.collective.client.utils.entity.PlayerState;
import me.collective.client.utils.entity.EntityUtils;
import me.collective.client.utils.world.BlockInteractionUtils;
import me.collective.client.settings.options.SocialRole;
import me.collective.client.events.EventStage;
import me.collective.client.commands.FakeplayerCommand;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.modules.movement.NoSlowModule;
import me.collective.client.events.PlayerAttackEvent;
import me.collective.client.events.PlayerIsStayingOnGroundSurfaceEvent;
import me.collective.client.modules.exploit.ReachModule;
import me.collective.client.modules.player.ScaffoldModule;
import me.collective.client.modules.movement.SprintModule;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements PlayerState {
   private static ScaffoldModule scaffoldModule;
   private static SprintModule sprintModule;
   private static ReachModule reachModule;
   private static NoSlowModule noSlowModule;
   private static ScaffoldModule scaffoldModule() {
      if (scaffoldModule == null) {
         scaffoldModule = ClientContext.getModule(ScaffoldModule.class);
      }
      return scaffoldModule;
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
   private static NoSlowModule noSlowModule() {
      if (noSlowModule == null) {
         noSlowModule = ClientContext.getModule(NoSlowModule.class);
      }
      return noSlowModule;
   }
   @Unique
   private long lastCrawlingTime;
   @Unique
   private long lastEatingTime;
   @Unique
   private SocialRole socialRole;
   @Unique
   private boolean nextToWall;
   @Unique
   private int lastAttackedTicksServer;
   @Unique
   private FloatAnimation healthAnimation;

   @Shadow
   public abstract Abilities getAbilities();

   protected PlayerMixin(EntityType entityType, Level level) {
      super(entityType, level);
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void initializePlayerState(
      Level level, GameProfile profile, CallbackInfo callback
   ) {
      this.healthAnimation = new FloatAnimation();
      this.healthAnimation
         .setValue(
            EntityUtils.getHealthFraction(this)
         );

      for (Object entry : (List)ClientContext.socialRegistry.getRegistry()) {
         SocialEntry socialEntry = (SocialEntry)entry;
         if (profile.name().equalsIgnoreCase(socialEntry.getName())) {
            this.collective$setRole(socialEntry.role());
            break;
         }
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void updatePlayerState(CallbackInfo callback) {
      this.healthAnimation
         .setTarget(
            EntityUtils.getHealthFraction(this), 100L
         );
      if (this.getBoundingBox().getYsize() < 1.0) {
         this.lastCrawlingTime = System.currentTimeMillis();
      }

      this.nextToWall = !this.level()
         .noCollision(this.getBoundingBox().inflate(1.0E-4, 0.0, 1.0E-4));
      if (!this.isUsingItem()
         || this.getItemUseProgress(this.getUseItem(), this) > 1.0F) {
         this.lastEatingTime = System.currentTimeMillis();
      }
   }

   @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
   private void onIsStayingOnGroundSurface(CallbackInfoReturnable<Boolean> callback) {
      if (ClientAccess.collectiveMinecraft.player != null) {
         PlayerIsStayingOnGroundSurfaceEvent event = new PlayerIsStayingOnGroundSurfaceEvent();
         ClientAccess.eventBus
            .post(event);
         if (event.isCancelled()) {
            callback.setReturnValue(true);
         }
      }
   }

   @ModifyExpressionValue(method = "maybeBackOffFromEdge", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;maxUpStep()F"))
   private float modifySafeWalkStepHeight(float originalHeight) {
      if (ClientAccess.collectiveMinecraft.player == null) {
         return originalHeight;
      }

      if (!scaffoldModule().isToggled() || !scaffoldModule().safeWalkSetting.getValue()) {
         return originalHeight;
      }
      Item heldItem = ClientAccess.collectiveMinecraft.player.getItemInHand(InteractionHand.MAIN_HAND).getItem();
      if (!(heldItem instanceof BlockItem blockItem)) {
         return originalHeight;
      }
      VoxelShape collisionShape = blockItem.getBlock().defaultBlockState().getCollisionShape(
         ClientAccess.collectiveMinecraft.level, BlockPos.ZERO
      );
      return collisionShape != null && !collisionShape.isEmpty() ? (float)collisionShape.bounds().getYsize() : originalHeight;
   }

   @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
   public void onAttackPre(Entity target, CallbackInfo callback) {
      PlayerAttackEvent event = new PlayerAttackEvent(EventStage.Pre, target);
      ClientAccess.eventBus
         .post(event);
      if (event.isCancelled()) {
         callback.cancel();
      }
   }

   @Inject(method = "attack", at = @At("TAIL"))
   private void onAttackPost(Entity attackedEntity, CallbackInfo callback) {
      PlayerAttackEvent event = new PlayerAttackEvent(EventStage.Post, attackedEntity);
      ClientAccess.eventBus
         .post(event);
   }

   @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
   private boolean allowAttackMovementSlowdown(Player player, Vec3 movement) {
      return !sprintModule().isToggled()
         || !sprintModule().keepSetting.getValue();
   }

   @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
   private boolean allowAttackSprintReset(Player player, boolean sprinting) {
      return !sprintModule().isToggled()
         || !sprintModule().keepSetting.getValue();
   }

   @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
   private boolean allowSimulatedPlayerKnockback(
      LivingEntity entity, double strength, double directionX, double directionZ
   ) {
      return !((Object)this instanceof FakeplayerCommand.SimulatedPlayer)
         || entity != ClientAccess.collectiveMinecraft.player;
   }

   @Inject(method = "entityInteractionRange", at = @At("HEAD"), cancellable = true)
   private void overrideEntityInteractionRange(CallbackInfoReturnable<Double> callback) {
      if (reachModule().isToggled()) {
         callback.setReturnValue(
            (reachModule().modifierSetting.getValue())
               .doubleValue()
         );
      }
   }

   @Inject(method = "blockInteractionRange", at = @At("HEAD"), cancellable = true)
   private void overrideBlockInteractionRange(CallbackInfoReturnable<Double> callback) {
      if (reachModule().isToggled()) {
         callback.setReturnValue(
            (reachModule().modifierSetting.getValue())
               .doubleValue()
         );
      }
   }

   @Inject(method = "isSecondaryUseActive", at = @At("HEAD"), cancellable = true)
   private void forceSecondaryUseWhilePlacing(CallbackInfoReturnable<Boolean> callback) {
      if (BlockInteractionUtils.isPlacingBlock) {
         callback.setReturnValue(true);
      }
   }

   @Inject(method = "resetOnlyAttackStrengthTicker", at = @At("HEAD"))
   private void resetServerAttackTicks(CallbackInfo callback) {
      this.collective$resetLastAttackedServer();
   }

   @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;attackStrengthTicker:I", ordinal = 0, opcode = 180))
   private void incrementServerAttackTicks(CallbackInfo callback) {
      if (!PlayerTickExecutor.isRunningExtraTick()) {
         this.lastAttackedTicksServer++;
      }
   }

   @ModifyReturnValue(method = "getSpeed", at = @At("RETURN"))
   private float applyNoSlowWalkingSpeed(float originalSpeed) {
      if (!this.level().isClientSide()) {
         return originalSpeed;
      }

      if (!noSlowModule().isToggled() || !noSlowModule().slownessSetting.getValue()) {
         return originalSpeed;
      }
      float walkingSpeed = this.getAbilities().getWalkingSpeed();
      if (originalSpeed >= walkingSpeed) {
         return originalSpeed;
      }
      return this.isSprinting() ? walkingSpeed * 1.3000001F : walkingSpeed;
   }

   @Override
   public long collective$getLastEatingTime() {
      return this.lastEatingTime;
   }

   @Override
   public SocialRole collective$getRole() {
      return this.socialRole;
   }

   @Override
   public void collective$setRole(SocialRole role) {
      this.socialRole = role;
   }

   @Override
   public float collective$getHealthFactor() {
      return this.healthAnimation
         .getValue();
   }

   @Override
   public boolean collective$isNextToWall() {
      return this.nextToWall;
   }

   @Override
   public boolean collective$wasCrawling() {
      return System.currentTimeMillis() - this.lastCrawlingTime <= 150L;
   }

   @Override
   public int collective$getLastAttackedTicksServer() {
      return this.lastAttackedTicksServer;
   }

   @Override
   public void collective$resetLastAttackedServer() {
      this.lastAttackedTicksServer = 0;
   }

   @Unique
   private static void collective$bindPlayerModules(ModuleRegistry moduleRegistry) {
      scaffoldModule = (ScaffoldModule)moduleRegistry.getModule(
         ScaffoldModule.class
      );
      sprintModule = (SprintModule)moduleRegistry.getModule(
         SprintModule.class
      );
      reachModule = (ReachModule)moduleRegistry.getModule(
         ReachModule.class
      );
      noSlowModule = (NoSlowModule)moduleRegistry.getModule(
         NoSlowModule.class
      );
   }
}
