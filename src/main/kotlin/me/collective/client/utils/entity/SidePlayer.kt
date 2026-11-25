package me.collective.client.utils.entity

import me.collective.client.utils.inventory.ArmorSlot

import me.collective.client.ClientAccess

import com.mojang.authlib.GameProfile
import me.collective.mixin.LivingEntityAccessor
import java.io.DataInputStream
import java.io.IOException
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.DefaultedRegistry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.PlayerModelPart
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

class SidePlayer : RemotePlayer {
   private val visibleModelParts: MutableSet<PlayerModelPart> = HashSet()
   private var capturedSkin: PlayerSkin? = null
   private var animationBoosted: Boolean = false
   private var capturedPosition: Vec3? = null
   private var capturedTickCount: Int = 0

   fun setAnimationBoosted(boosted: Boolean) {
      this.animationBoosted = boosted
   }

   override fun setPos(x: Double, y: Double, z: Double) {
      super.setPos(x, y, z)
      this.xOld = x
      this.yOld = y
      this.zOld = z
   }

   override fun restoreFrom(entity: Entity) {
      val snapshotTag = CompoundTag()
      snapshotTag.putBoolean("collective_side_player", true)
      entity.saveWithoutId(snapshotTag)
      snapshotTag.remove("Dimension")
      this.load(snapshotTag)
      this.portalProcess = entity.portalProcess
   }

   constructor(level: ClientLevel) : super(level, GameProfile(UUID.randomUUID(), ""))

   constructor(player: Player) : super(Minecraft.getInstance().level!!, player.getGameProfile()) {
      this.copyPlayerState(player)
   }

   override fun aiStep() {
   }

   override fun isModelPartShown(modelPart: PlayerModelPart): Boolean {
      return this.visibleModelParts.contains(modelPart)
   }

   private fun parseIdentifier(rawIdentifier: String): ResourceLocation {
      return try {
         ResourceLocation.parse(rawIdentifier)
      } catch (malformedIdentifier: RuntimeException) {
         // Контракт readSnapshot — IllegalStateException на невалидных данных,
         // ResourceLocationException из ResourceLocation.parse в него не входит.
         throw IllegalStateException()
      }
   }

   @Throws(IOException::class)
   fun readSnapshot(input: DataInputStream) {
      val snapshotTickCount = input.readInt()
      if (ClientAccess.collectiveMinecraft.player == null ||
         ClientAccess.collectiveMinecraft.level == null ||
         snapshotTickCount < 0
      ) {
         throw IllegalStateException()
      }

      this.capturedTickCount = snapshotTickCount
      val capturedX = input.readDouble()
      val capturedY = input.readDouble()
      val capturedZ = input.readDouble()
      this.capturedPosition = Vec3(capturedX, capturedY, capturedZ)
      var eyePosition: Vec3 = ClientAccess.collectiveMinecraft.player?.getEyePosition()
         ?: throw IllegalStateException()
      val capturedPositionForDistance = this.capturedPosition!!
      val capturedDistance = eyePosition.distanceTo(capturedPositionForDistance)

      if (capturedDistance >= 9999.0) {
         throw IllegalStateException()
      }

      val positionX = input.readDouble()
      val positionY = input.readDouble()
      val positionZ = input.readDouble()
      val localPlayerForPositionDistance: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         ?: throw IllegalStateException()
      eyePosition = localPlayerForPositionDistance.getEyePosition()
      val snapshotPositionForDistance = Vec3(positionX, positionY, positionZ)
      val positionDistance = eyePosition.distanceTo(snapshotPositionForDistance)

      if (positionDistance >= 9999.0) {
         throw IllegalStateException()
      }

      val previousYawValue = input.readFloat()
      val previousYaw = Mth.wrapDegrees(previousYawValue)
      val previousPitchValue = input.readFloat()

      val unclampedPreviousPitch = previousPitchValue % 360.0F

      val decodedPreviousPitchMinimum = -90.0F

      val previousPitchMaximum = 90.0F
      val previousPitchMinimum = decodedPreviousPitchMinimum
      val previousPitchRemainder = unclampedPreviousPitch
      val previousPitch = Math.clamp(previousPitchRemainder, previousPitchMinimum, previousPitchMaximum)
      val yawValue = input.readFloat()
      val yaw = Mth.wrapDegrees(yawValue)
      val pitchValue = input.readFloat()

      val unclampedPitch = pitchValue % 360.0F

      val decodedPitchMinimum = -90.0F

      val pitchMaximum = 90.0F
      val pitchMinimum = decodedPitchMinimum
      val pitchRemainder = unclampedPitch
      val pitch = Math.clamp(pitchRemainder, pitchMinimum, pitchMaximum)
      val snapshotPitch = pitch
      val snapshotYaw = yaw
      val snapshotZ = positionZ
      val snapshotY = positionY
      val snapshotX = positionX
      this.moveTo(snapshotX, snapshotY, snapshotZ, snapshotYaw, snapshotPitch)
      this.yRotO = previousYaw
      val newYaw = yaw
      this.setYRot(newYaw)
      this.xRotO = previousPitch
      val newPitch = pitch
      this.setXRot(newPitch)
      val bodyYaw = input.readFloat()
      this.yBodyRot = Mth.wrapDegrees(bodyYaw)
      val previousBodyYaw = input.readFloat()
      this.yBodyRotO = Mth.wrapDegrees(previousBodyYaw)
      val headYaw = input.readFloat()
      this.yHeadRot = Mth.wrapDegrees(headYaw)
      val previousHeadYaw = input.readFloat()
      this.yHeadRotO = Mth.wrapDegrees(previousHeadYaw)
      this.walkAnimation.speed = input.readFloat()
      this.walkAnimation.speedOld = this.walkAnimation.speed
      this.walkAnimation.position = input.readFloat()
      if (this.walkAnimation.speed < 0.0F || this.walkAnimation.position < 0.0F) {
         throw IllegalStateException()
      }

      val walkSpeed = this.walkAnimation.speed
      if (!walkSpeed.isFinite()) {
         throw IllegalStateException()
      }

      val walkPosition = this.walkAnimation.position
      if (!walkPosition.isFinite()) {
         throw IllegalStateException()
      }

      this.deathTime = 0
      val zeroMotion = Vec3(0.0, 0.0, 0.0)
      this.setDeltaMovement(zeroMotion)
      var pose: Pose? = null
      val poseId = input.readInt()

      for (candidatePose in Pose.values()) {
         if (candidatePose.id() == poseId) {
            pose = candidatePose
            break
         }
      }

      if (pose == null) {
         throw IllegalStateException()
      }

      val selectedPose = pose
      this.setPose(selectedPose)
      val sneaking = input.readBoolean()
      this.setShiftKeyDown(sneaking)
      val mainHandRegistry: DefaultedRegistry<Item> = BuiltInRegistries.ITEM
      val mainHandIdentifier = input.readUTF()
      val mainHandItemId = parseIdentifier(mainHandIdentifier)
      val readMainHandItem: Item = mainHandRegistry.getValue(mainHandItemId)
      val offHandRegistry: DefaultedRegistry<Item> = BuiltInRegistries.ITEM
      val offHandIdentifier = input.readUTF()
      val offHandItemId = parseIdentifier(offHandIdentifier)
      val readOffHandItem: Item = offHandRegistry.getValue(offHandItemId)
      val mainHand = InteractionHand.MAIN_HAND
      val mainHandStack = readMainHandItem.getDefaultInstance()
      this.setItemInHand(mainHand, mainHandStack)
      val offHand = InteractionHand.OFF_HAND
      val offHandStack = readOffHandItem.getDefaultInstance()
      this.setItemInHand(offHand, offHandStack)
      val armorCount = input.readInt()
      if (armorCount < 0) {
         throw IllegalStateException()
      }

      if (armorCount > 4) {
         throw IllegalStateException()
      }

      for (armorIndex in 0 until armorCount) {
         val armorRegistry: DefaultedRegistry<Item> = BuiltInRegistries.ITEM
         val armorIdentifier = input.readUTF()
         val armorItemId = parseIdentifier(armorIdentifier)
         val armorItem: Item = armorRegistry.getValue(armorItemId)
         val armorStack = armorItem.getDefaultInstance()
         var armorSlot = -1
         val headArmorTag = ItemTags.HEAD_ARMOR
         val headArmorStack = armorStack
         if (headArmorStack.`is`(headArmorTag)) {
            val helmetSlot = ArmorSlot.HELMET
            armorSlot = helmetSlot.getInventoryIndex()
         } else {
            val chestArmorTag = ItemTags.CHEST_ARMOR
            val chestArmorStack = armorStack
            if (!chestArmorStack.`is`(chestArmorTag) && armorItem !== Items.ELYTRA) {
               val legArmorTag = ItemTags.LEG_ARMOR
               val legArmorStack = armorStack
               if (legArmorStack.`is`(legArmorTag)) {
                  val leggingsSlot = ArmorSlot.LEGGINGS
                  armorSlot = leggingsSlot.getInventoryIndex()
               } else {
                  val footArmorTag = ItemTags.FOOT_ARMOR
                  val footArmorStack = armorStack
                  if (footArmorStack.`is`(footArmorTag)) {
                     val bootsSlot = ArmorSlot.BOOTS
                     armorSlot = bootsSlot.getInventoryIndex()
                  }
               }
            } else {
               val chestplateSlot = ArmorSlot.CHESTPLATE
               armorSlot = chestplateSlot.getInventoryIndex()
            }
         }

         if (armorSlot != -1) {
            val snapshotInventory: Inventory = this.getInventory()
            val equippedArmorStack = armorStack
            val targetArmorSlot = armorSlot
            snapshotInventory.setItem(targetArmorSlot, equippedArmorStack)
         }
      }

      val leanAccessor = this as LivingEntityAccessor
      val leanValue = input.readFloat()

      val unclampedLean = leanValue % 360.0F

      val decodedLeanMinimum = -90.0F

      val leanMaximum = 90.0F
      val leanMinimum = decodedLeanMinimum
      val leanRemainder = unclampedLean
      leanAccessor.`collective$setLeaningPitch`(Math.clamp(leanRemainder, leanMinimum, leanMaximum))
      val previousLeanAccessor = this as LivingEntityAccessor
      val previousLeanValue = input.readFloat()

      val unclampedPreviousLean = previousLeanValue % 360.0F

      val decodedPreviousLeanMinimum = -90.0F

      val previousLeanMaximum = 90.0F
      val previousLeanMinimum = decodedPreviousLeanMinimum
      val previousLeanRemainder = unclampedPreviousLean
      previousLeanAccessor.`collective$setLastLeaningPitch`(Math.clamp(previousLeanRemainder, previousLeanMinimum, previousLeanMaximum))
      val fallFlying = input.readBoolean()
      val glidingTicksAccessor = this as LivingEntityAccessor
      glidingTicksAccessor.`collective$setGlidingTicks`(input.readInt())
      if (this.getFallFlyingTicks() < 0) {
         throw IllegalStateException()
      }

      if (fallFlying) {
         this.startFallFlying()
      }

      var animationTick = 0

      while (true) {

         val snapshotTicksForAnimation = snapshotTickCount
         if (animationTick >= Math.min(snapshotTicksForAnimation, 10)) {
            val zeroZ = 0.0
            val zeroY = 0.0
            val zeroX = 0.0
            this.setDeltaMovement(zeroX, zeroY, zeroZ)
            return
         }

         this.elytraAnimationState.tick()
         animationTick++
      }
   }

   fun copyPlayerState(player: Player) {
      this.capturedTickCount = player.tickCount
      this.capturedPosition = Vec3(player.getX(), player.getY(), player.getZ())
      this.restoreFrom(player)
      this.copyPosition(player)
      this.yRotO = player.getYRot()
      val yaw = player.getYRot()
      this.setYRot(yaw)
      this.xRotO = player.getXRot()
      val pitch = player.getXRot()
      this.setXRot(pitch)
      this.yBodyRot = player.yBodyRot
      this.yBodyRotO = player.yBodyRot
      this.yHeadRot = player.yHeadRot
      this.yHeadRotO = player.yHeadRot
      this.walkAnimation.speed = player.walkAnimation.speed
      this.walkAnimation.speedOld = player.walkAnimation.speed
      this.walkAnimation.position = player.walkAnimation.position
      this.deathTime = 0
      val pose = player.getPose()
      this.setPose(pose)
      val sneaking = player.isShiftKeyDown()
      this.setShiftKeyDown(sneaking)
      if (player is AbstractClientPlayer) {
         val clientPlayer = player as AbstractClientPlayer
         val sourceForSkin = clientPlayer
         this.capturedSkin = sourceForSkin.getSkin()

         for (modelPart in PlayerModelPart.values()) {
            if (modelPart != PlayerModelPart.CAPE) {
               val candidateModelPart = modelPart
               val sourceForModelPart = clientPlayer
               if (sourceForModelPart.isModelPartShown(candidateModelPart)) {
                  this.visibleModelParts.add(modelPart)
               }
            }
         }
      }

      var inventorySlot = 0

      while (true) {
         val inventoryForSize: Inventory = this.getInventory()
         if (inventorySlot >= inventoryForSize.getContainerSize()) {
            break
         }

         val targetInventory: Inventory = this.getInventory()
         val inventoryFromPlayer: Inventory = player.getInventory()
         val sourceSlot = inventorySlot
         val sourceStack: ItemStack = inventoryFromPlayer.getItem(sourceSlot)
         val copiedStack = sourceStack.copy()
         val targetSlot = inventorySlot
         targetInventory.setItem(targetSlot, copiedStack)
         inventorySlot++
      }

      var hand = InteractionHand.MAIN_HAND
      val sourceMainHandStack: ItemStack = player.getMainHandItem()
      val copiedMainHandStack = sourceMainHandStack.copy()
      val mainHand = hand
      this.setItemInHand(mainHand, copiedMainHandStack)
      hand = InteractionHand.OFF_HAND
      val sourceOffHandStack: ItemStack = player.getOffhandItem()
      val copiedOffHandStack = sourceOffHandStack.copy()
      val offHand = hand
      this.setItemInHand(offHand, copiedOffHandStack)
      val inventoryForSelectedSlot: Inventory = this.getInventory()
      val sourceInventoryForSelectedSlot: Inventory = player.getInventory()
      val selectedSlot = sourceInventoryForSelectedSlot.selected
      inventoryForSelectedSlot.selected = selectedSlot
      (this as LivingEntityAccessor).`collective$setLeaningPitch`((player as LivingEntityAccessor).`collective$getLeaningPitch`())
      (this as LivingEntityAccessor).`collective$setLastLeaningPitch`((player as LivingEntityAccessor).`collective$getLastLeaningPitch`())
      val glidingTicksAccessor = this as LivingEntityAccessor
      glidingTicksAccessor.`collective$setGlidingTicks`(player.getFallFlyingTicks())
      val motion: Vec3 = player.getDeltaMovement()
      this.setDeltaMovement(motion)
      if (player.isFallFlying()) {
         this.startFallFlying()
      }

      var animationTick = 0

      while (true) {
         val sourceTickCount = player.tickCount

         val sourceTicksForAnimation = sourceTickCount
         if (animationTick >= Math.min(sourceTicksForAnimation, 10)) {
            val zeroZ = 0.0
            val zeroY = 0.0
            val zeroX = 0.0
            this.setDeltaMovement(zeroX, zeroY, zeroZ)
            return
         }

         this.elytraAnimationState.tick()
         animationTick++
      }
   }

   override fun getSkin(): PlayerSkin {
      return this.capturedSkin ?: super.getSkin()
   }

   fun getCapturedTickCount(): Int {
      return this.capturedTickCount
   }

   fun getCapturedPosition(): Vec3 {
      return this.capturedPosition!!
   }

   fun isAnimationBoosted(): Boolean {
      return this.animationBoosted
   }

}
