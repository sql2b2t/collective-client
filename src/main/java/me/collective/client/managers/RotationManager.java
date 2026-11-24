package me.collective.client.managers;

import me.collective.client.access.LocalPlayerTickAccess;
import me.collective.client.events.EventStage;
import me.collective.client.render.RenderUtils;
import me.collective.client.utils.combat.BoundingBoxUtils;
import me.collective.client.utils.combat.RotationRequest;
import me.collective.client.utils.movement.MovementDirection;
import me.collective.client.utils.movement.MovementInputProcessor;
import me.collective.client.utils.movement.MovementPhysics;
import me.collective.client.utils.network.PacketSender;

import me.collective.client.utils.math.MathConstants;
import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.LivingEntityRotationEvent;
import me.collective.client.events.PacketEvent;
import me.collective.client.events.RotationRequestEvent;
import me.collective.client.events.SneakStateChangeEvent;
import me.collective.client.events.EventHandler;
import me.collective.client.events.FireworkRocketEntityTickEvent;
import me.collective.client.events.LocalPlayerSendPositionEvent;
import me.collective.client.events.LocalPlayerPreTickEvent;
import me.collective.client.events.MinecraftRunTickEvent;
import me.collective.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent;
import me.collective.client.events.bus.EventBus;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.modules.client.AntiCheatModule;
import me.collective.client.settings.Setting;
import me.collective.client.settings.options.AntiCheatRotationsChoice;

import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class RotationManager implements ClientAccess {
   private static AntiCheatModule antiCheat;
   private static Frustum frustum;
   public static boolean renderingInventory;
   private float yaw;
   private float pitch;
   private float bodyYaw;
   private float previousPitch;
   private float previousBodyYaw;
   private float previousHeadYaw;
   private float headYaw;
   private float[] lastRotations;
   private int lastRotationTick;
   private int rotationHoldTicks;
   private final MovementInputProcessor movementInput = new MovementInputProcessor();
   private final Queue<Runnable> deferredActions = new ArrayDeque<>();
   private boolean collectingActions;
   private boolean executingAction;
   private RotationRequest request;
   private boolean forcedOverride;
   private float movementYaw;
   private boolean requestedYawForMovement;

   private boolean shouldOverrideRotation() {
      return this.request != null && (isMovementSyncEnabled() || this.forcedOverride);
   }

   public float getHeadYaw() {
      return this.headYaw;
   }

   public float getPreviousHeadYaw() {
      return this.previousHeadYaw;
   }

   public float getBodyYaw() {
      return this.bodyYaw;
   }

   public void forceRotationOverride() {
      this.forcedOverride = true;
   }

   public float getPreviousPitch() {
      return this.previousPitch;
   }

   public boolean isExecutingAction() {
      return this.executingAction;
   }

   private void runDeferredActions() {
      for (int index = 0; !this.deferredActions.isEmpty() && index < 500; index++) {
         Runnable action = this.deferredActions.poll();
         if (action != null) {
            this.executeAction(action);
         }
      }
      this.deferredActions.clear();
   }

   public float getPreviousBodyYaw() {
      return this.previousBodyYaw;
   }

   private void collectRotationRequests() {
      this.collectingActions = true;
      try {
         ClientAccess.eventBus.post(new RotationRequestEvent());
      } finally {
         this.collectingActions = false;
      }
      if (this.request == null) {
         this.runDeferredActions();
      }
   }

   public boolean isRequestedYawUsedForMovement() {
      return this.requestedYawForMovement;
   }

   public float getPitch() {
      return this.pitch;
   }

   public float getYaw() {
      return this.yaw;
   }

   public RotationRequest getRequest() {
      return this.request;
   }

   public void useRequestedYawForPriority(int priority) {
      if (this.request != null) {
         if (this.request.priority() != priority) {
            return;
         }
      }

      this.requestedYawForMovement = true;
   }

   public static boolean is2b2tMode() {
      if (antiCheat() == null) {
         return false;
      }

      return antiCheat().is2b2tEnabled();
   }

   public static boolean isMovementSyncEnabled() {
      if (antiCheat() == null) {
         return false;
      }

      Setting<Boolean> movementSyncSetting = antiCheat().movementSyncSetting;

      return movementSyncSetting.getValue();
   }

   public void executeAction(Runnable action) {
      boolean alreadyExecuting = this.executingAction;
      this.executingAction = true;
      try {
         action.run();
      } finally {
         this.executingAction = alreadyExecuting;
      }
   }

   public RotationManager() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   private static AntiCheatModule antiCheat() {
      if (antiCheat == null) {
         antiCheat = ClientContext.getModule(AntiCheatModule.class);
      }
      return antiCheat;
   }

   private float calculateBodyYaw(float yaw, float previousBodyYaw) {
      if (ClientAccess.collectiveMinecraft.player.isPassenger()) {
         return yaw;
      }

      float bodyYaw = previousBodyYaw;

      double deltaX = ClientAccess.collectiveMinecraft.player.getX()
         - ClientAccess.collectiveMinecraft.player.xo;

      double deltaZ = ClientAccess.collectiveMinecraft.player.getZ()
         - ClientAccess.collectiveMinecraft.player.zo;
      double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
      if (distanceSquared > 0.002500000176951289D) {
         float movementHeading = ((float)Mth.atan2(deltaZ, deltaX)) * 57.295776F
            - MathConstants.NINETY_DEGREES;
         float headingDifference = Mth.wrapDegrees(yaw) - movementHeading;
         float absoluteDifference = Mth.abs(headingDifference);

         bodyYaw = movementHeading;
         if (95.0F < absoluteDifference) {
            if (absoluteDifference < 265.0F) {
               bodyYaw = movementHeading - 180.0F;
            }
         }
      }

      if (ClientAccess.collectiveMinecraft.player.attackAnim > 0.0F) {
         bodyYaw = yaw;
      }

      float bodyDelta = bodyYaw - previousBodyYaw;
      float wrappedBodyDelta = Mth.wrapDegrees(bodyDelta);

      bodyYaw = previousBodyYaw + wrappedBodyDelta * 0.3F;
      float headDelta = yaw - bodyYaw;
      float wrappedHeadDelta = Mth.wrapDegrees(headDelta);

      if (wrappedHeadDelta < -75.0F) {
         wrappedHeadDelta = -75.0F;
      } else {
         if (wrappedHeadDelta >= 75.0F) {
            wrappedHeadDelta = 75.0F;
         }
      }

      bodyYaw = yaw - wrappedHeadDelta;
      float headDeltaSquared = wrappedHeadDelta * wrappedHeadDelta;

      if (headDeltaSquared > 2500.0F) {
         bodyYaw += wrappedHeadDelta * 0.2F;
      }

      return bodyYaw;
   }

   public static boolean isVisible(AABB bounds) {
      if (frustum == null) {
         return true;
      }

      return frustum.isVisible(bounds);
   }

   public static boolean isSilentRotationMode() {
      if (antiCheat() == null) {
         return false;
      }

      if (isMovementSyncEnabled()) {
         Setting<AntiCheatRotationsChoice> rotationSetting = antiCheat().rotationsSetting;
         if (rotationSetting.getValue() == AntiCheatRotationsChoice.SILENT) {
            return true;
         }
      }

      return false;
   }

   public static int getYawSector(float yaw) {
      double scaledYaw = yaw
            * 8.0F
            / MathConstants.FULL_TURN_DEGREES
         + MathConstants.ONE_HALF;
      int sector = Mth.floor(scaledYaw);

      return sector & 7;
   }

   public static float[] getRotationsToBlock(BlockPos position) {
      Vec3 center = new Vec3(
         blockCenterX(position),
         position.getY() + MathConstants.ONE_HALF,
         position.getZ() + MathConstants.ONE_HALF
      );

      return getRotationsToPosition(center);
   }

   private static double blockCenterX(BlockPos position) {
      return position.getX() + MathConstants.ONE_HALF;
   }

   public void updateRenderRotations(float[] angles) {
      this.setRenderRotations(angles, 0);
   }

   public static float[] calculateRotations(
      Vec3 from, Vec3 to
   ) {
      double deltaX = to.x - from.x;
      double deltaY = to.y - from.y;
      double verticalDelta = deltaY * -1.0D;
      double deltaZ = to.z - from.z;
      double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
      double horizontalDistance = Math.sqrt(horizontalDistanceSquared);

      float[] angles = new float[2];
      double yawRadians = Math.atan2(deltaZ, deltaX);
      double yawDegrees = Math.toDegrees(yawRadians)
         - MathConstants.NINETY_DEGREES;
      angles[0] = (float)Mth.wrapDegrees(yawDegrees);
      double pitchRadians = Math.atan2(verticalDelta, horizontalDistance);
      double pitchDegrees = Math.toDegrees(pitchRadians);
      angles[1] = Mth.clamp((float)pitchDegrees, -90.0F, 90.0F);
      return angles;
   }

   public static float[] getRotationsToEntity(Entity entity) {
      if (ClientAccess.collectiveMinecraft.player == null) {
         return new float[]{0.0F, 0.0F};
      }
      Vec3 targetPosition = ClientAccess.collectiveMinecraft.player.getEyePosition();
      AABB bounds = entity.getBoundingBox();
      targetPosition = BoundingBoxUtils.clampToBounds(targetPosition, bounds);

      return getRotationsToPosition(targetPosition);
   }

   public static void setFrustum(Frustum sourceFrustum) {
      frustum = new Frustum(sourceFrustum);
   }

   @EventHandler
   private void onLocalPlayerPreTick(LocalPlayerPreTickEvent event) {
      if (isMovementSyncEnabled()) {
         this.runDeferredActions();
         this.collectRotationRequests();
      }
   }

   @EventHandler
   private void onSneakStateChangeEvent(SneakStateChangeEvent event) {
      if (!this.collectingActions) {
         return;
      }
      if (event.isCancelled() || this.executingAction) {
         return;
      }
      Queue<Runnable> queue = this.deferredActions;
      boolean sneaking = event.isSneaking();
      queue.add(() -> {
         ClientContext.playerStateTracker.setSneaking(sneaking);
      });
      event.cancel();
      return;
   }

   public void requestRotation(RotationRequest incomingRequest) {
      if (this.request != null) {
         int currentPriority = this.request.priority();
         if (currentPriority > incomingRequest.priority()) {
            return;
         }
      }

      this.request = incomingRequest;
      this.requestedYawForMovement = false;
   }

   public void requestRotation(float[] angles, int priority) {
      this.requestRotation(angles, priority, false);
   }

   public void requestRotation(Float yaw, Float pitch, int priority, boolean force) {
      RotationRequest currentRequest = this.getRequest();
      float fallbackYaw;
      if (currentRequest != null) {
         fallbackYaw = currentRequest.angles()[0];
      } else {
         fallbackYaw = ClientAccess.collectiveMinecraft.player.getYRot();
      }

      float fallbackPitch;
      if (currentRequest != null) {
         fallbackPitch = currentRequest.angles()[1];
      } else {
         fallbackPitch = ClientAccess.collectiveMinecraft.player.getXRot();
      }
      float yawValue = yaw != null ? yaw.floatValue() : fallbackYaw;
      float pitchValue = pitch != null ? pitch.floatValue() : fallbackPitch;

      float[] angles = new float[2];
      angles[0] = yawValue;
      angles[1] = pitchValue;
      this.requestRotation(angles, priority, force);
   }

   public void requestRotation(float[] angles, int priority, boolean force) {
      if (antiCheat().is2b2tEnabled()
         && ClientAccess.collectiveMinecraft.screen instanceof AbstractContainerScreen
         && !force) {
         return;
      }
      if (!isSilentRotationMode() || force) {
         RotationRequest rotationRequest = new RotationRequest(angles, priority);
         this.requestRotation(rotationRequest);
         return;
      }
      if (!this.collectingActions) {
         return;
      }
      this.updateRenderRotations(angles);
      float[] packetAngles = unwrapYaw(angles);

      double x = ClientAccess.collectiveMinecraft.player.getX();

      double y = ClientAccess.collectiveMinecraft.player.getY();

      double z = ClientAccess.collectiveMinecraft.player.getZ();
      float yaw = packetAngles[0];
      float pitch = packetAngles[1];

      boolean onGround = ClientContext.playerStateTracker.isPacketOnGround();
      PacketSender.sendPositionRotation(x, y, z, yaw, pitch, onGround);
   }

   public static float[] getRotationsToFace(Vec3 position, Direction face) {
      LocalPlayer player = ClientAccess.collectiveMinecraft.player;
      float partialTick = RenderUtils.getPartialTick();

      Vec3 eyePosition = player.getEyePosition(partialTick);
      Vec3 targetPosition;
      if (face == null) {
         targetPosition = position;
      } else {
         double faceOffset = MathConstants.ONE_HALF;
         targetPosition = position.relative(face, faceOffset);
      }

      return calculateRotations(eyePosition, targetPosition);
   }

   @EventHandler
   private void onLivingEntityAiStepEvent(
      LivingEntityRotationEvent.LivingEntityAiStepEvent event
   ) {
      if (this.request == null) {
         return;
      }
      if (this.isWorldUnavailable() || this.forcedOverride || !isMovementSyncEnabled()) {
         return;
      }

      float[] angles = this.request.angles();

      float correctedMovementYaw = MovementPhysics.getMovementYaw(this.movementYaw);

      if (this.isRequestedYawUsedForMovement()) {
         correctedMovementYaw = angles[0];
      }

      ClientInput input = ClientAccess.collectiveMinecraft.player.input;
      float rotationYaw = angles[0];

      MovementDirection direction = this.movementInput.getRelativeDirection(correctedMovementYaw, rotationYaw);
      Vec2 previousMoveVector = new Vec2(input.moveVector.x, input.moveVector.y);

      if (MovementPhysics.isLocalPlayerMoving()) {
         MovementInputProcessor normalizer = this.movementInput;
         Vec2 directionVector = new Vec2(direction.getSidewaysInput(), direction.getForwardInput());
         input.moveVector = normalizer.normalizeInput(directionVector);
         Input previousKeyPresses = input.keyPresses;
         input.keyPresses = direction.createInput(previousKeyPresses);
      }

      this.movementInput.applyInputPreservingBobbing();
      input.moveVector = previousMoveVector;
      if (ClientAccess.collectiveMinecraft.player.zza <= 0.0F) {
         ClientAccess.collectiveMinecraft.player.setSprinting(false);
      }

      return;
   }

   @EventHandler
   private void onRotationYawEvent(LivingEntityRotationEvent event) {
      if (this.shouldOverrideRotation()) {
         float eventYaw = event.getYaw();
         this.movementYaw = Mth.wrapDegrees(eventYaw);
         event.cancel();

         float requestedYaw = this.request.angles()[0];
         event.setYaw(requestedYaw);

         float requestedPitch = this.request.angles()[1];
         event.setPitch(requestedPitch);
      }
   }

   @EventHandler
   public void onFireworkRocketEntityTickEvent(FireworkRocketEntityTickEvent event) {
      if (this.shouldOverrideRotation()) {
         float[] angles = this.request.angles();
         if (event.getYaw() == angles[0] && event.getPitch() == angles[1]) {
            return;
         }

         event.setYaw(angles[0]);

         event.setPitch(angles[1]);
      }
   }

   @EventHandler
   private void onMultiPlayerGameModeHandleInventoryMouseClickEvent(
      MultiPlayerGameModeHandleInventoryMouseClickEvent event
   ) {
      if (!this.collectingActions) {
         return;
      }
      if (event.isCancelled() || this.executingAction) {
         return;
      }

      this.deferredActions.add(() -> replayInventoryClick(event));
      event.cancel();
      return;
   }

   @EventHandler
   private void onConnectionSendPacketEvent(
      PacketEvent.Send event
   ) {
      if (!this.collectingActions) {
         return;
      }
      if (event.isCancelled() || this.executingAction) {
         return;
      }
      if (event.getPacket() instanceof ServerboundMovePlayerPacket movementPacket) {
         event.cancel();
         Packet packet = event.getPacket();

         PacketSender.sendDirect(packet);
         if (movementPacket.hasRotation()) {
            float packetYaw = movementPacket.getYRot(0.0F);
            float packetPitch = movementPacket.getXRot(0.0F);

            ((LocalPlayerTickAccess)ClientAccess.collectiveMinecraft.player).collective$updateServer(packetYaw, packetPitch);
         }

         this.runDeferredActions();
         return;
      }

      Queue<Runnable> queue = this.deferredActions;
      Packet packet = event.getPacket();
      queue.add(() -> PacketSender.send(packet));
      event.cancel();
      return;
   }

   private static void replayInventoryClick(MultiPlayerGameModeHandleInventoryMouseClickEvent event) {
      if (ClientAccess.collectiveMinecraft.gameMode == null) {
         return;
      }
      int containerId = event.getContainerId();
      int slotId = event.getSlotId();
      int mouseButton = event.getButton();
      ClickType clickType = event.getClickType();
      MultiPlayerGameMode gameMode = ClientAccess.collectiveMinecraft.gameMode;
      LocalPlayer player = ClientAccess.collectiveMinecraft.player;

      gameMode.handleInventoryMouseClick(containerId, slotId, mouseButton, clickType, player);
   }

   public static float[] addYawJitter(float[] angles) {
      float offset = AntiCheatModule.ROTATION_JITTER_STEP * (ClientAccess.collectiveMinecraft.player.tickCount % 6);
      return new float[]{Mth.wrapDegrees(angles[0] + offset), angles[1]};
   }

   public static boolean isPositionVisible(Vec3 position) {
      AABB bounds = new AABB(BlockPos.containing((Position)position));

      return isVisible(bounds);
   }

   public static float[] getRotationsToPosition(Vec3 position) {
      LocalPlayer player = ClientAccess.collectiveMinecraft.player;
      if (player == null) {
         return new float[]{0.0F, 0.0F};
      }
      float partialTick = RenderUtils.getPartialTick();

      return calculateRotations(player.getEyePosition(partialTick), position);
   }

   @EventHandler(priority = 200)
   private void onMinecraftRunTickEvent(MinecraftRunTickEvent event) {
      this.forcedOverride = false;
      this.requestedYawForMovement = false;
      this.request = null;
   }

   public void setRenderRotations(float[] angles, int holdTicks) {
      this.lastRotationTick = ClientAccess.collectiveMinecraft.player
         .tickCount;
      this.previousPitch = this.pitch;
      this.previousBodyYaw = this.bodyYaw;
      float requestedYaw = angles[0];
      float previousBodyYaw = this.previousBodyYaw;
      this.bodyYaw = this.calculateBodyYaw(requestedYaw, previousBodyYaw);
      this.previousHeadYaw = this.headYaw;
      this.headYaw = angles[0];
      this.yaw = angles[0];
      this.pitch = angles[1];
      this.lastRotations = angles;
      if (holdTicks > 0) {
         this.rotationHoldTicks = holdTicks;
      }
   }

   public void updateRenderRotationsIfNeeded(float yaw, float pitch) {
      if (ClientAccess.collectiveMinecraft.player.tickCount
         != this.lastRotationTick) {
         float[] angles = new float[2];
         angles[0] = yaw;
         angles[1] = pitch;
         this.updateRenderRotations(angles);
      }
   }

   public static float[] unwrapYaw(float[] angles) {
      float packetYaw = ClientContext.playerStateTracker.getPacketRotations()[0];
      float yawDifference = angles[0] - packetYaw;
      float wrappedYawDifference = Mth.wrapDegrees(yawDifference);

      float[] result = new float[2];
      result[0] = packetYaw + wrappedYawDifference;
      result[1] = angles[1];
      return result;
   }

   @EventHandler
   private void onLocalPlayerSendPositionEvent(LocalPlayerSendPositionEvent event) {
      if (this.rotationHoldTicks > 0
         && this.lastRotations != null) {
         float[] angles = this.lastRotations;
         this.updateRenderRotations(angles);
         this.rotationHoldTicks--;
      }

      if (event.getStage() == EventStage.Post) {
         this.runDeferredActions();
      } else {
         if (!isMovementSyncEnabled()) {
            this.collectRotationRequests();
         }

         if (this.request != null) {
            float[] requestedAngles = this.request.angles();

            event.setRotation(unwrapYaw(requestedAngles));
         }
      }
   }

   public static void bindReferencedModules(ModuleRegistry modules) {
      antiCheat = (AntiCheatModule)modules.getModule(
         AntiCheatModule.class
      );
   }
}
