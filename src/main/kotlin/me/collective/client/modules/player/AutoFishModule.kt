package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.utils.combat.BlockRaycastClassifier
import me.collective.client.utils.combat.BlockRaycastOverride
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.BlockRaycastContext
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult.Type
import net.minecraft.world.phys.Vec3

class AutoFishModule : Module("AutoFish", "Fishes.", ModuleCategory.PLAYER) {
   private val waterSetting: Setting<Boolean>
   private val recastDelaySetting: Setting<Float>
   private val castTimer: ElapsedTimer
   @Volatile private var nearbySplashDetected: Boolean = false

   override fun onEnable() {
      this.nearbySplashDetected = false
      this.castTimer.reset()
   }

   private fun useRod(hand: InteractionHand) {
      val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      gameMode.useItem(player, hand)
      ClientAccess.collectiveMinecraft.player!!.swing(hand)
      this.castTimer.reset()
   }

   @EventHandler
   private fun updateFishing(event: LocalPlayerPreTickEvent) {
      val capturedOffhand: ItemStack = ClientAccess.collectiveMinecraft.player!!.offhandItem
      val offhandRod: Item = Items.FISHING_ROD

      val rodHand: InteractionHand = if (capturedOffhand.`is`(offhandRod)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
      val rod: Item = Items.FISHING_ROD
      val holdingPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (!holdingPlayer.isHolding(rod)) {
         return
      }
      if (this.nearbySplashDetected) {
         // Клёв: крючок в воде (fishing != null), поэтому useItem вытягивает рыбу.
         // Раньше здесь стоял castIfNoHook, который подсекал только при fishing == null —
         // подсечки не происходило вообще.
         this.nearbySplashDetected = false
         this.useRod(rodHand)
      }

      if (this.waterSetting.value && this.isWaterTargetMissing()) {
         return
      }

      val capturedFallbackTimer: ElapsedTimer = this.castTimer

      val fallbackUnit: TimeUnit = TimeUnit.MINUTES

      if (!capturedFallbackTimer.hasElapsed(1.0, fallbackUnit)) {
         if (ClientAccess.collectiveMinecraft.player!!.fishing != null) {
            return
         }

         val capturedRecastTimer: ElapsedTimer = this.castTimer
         val recastSeconds: Double = (this.recastDelaySetting.value).toDouble()
         val recastUnit: TimeUnit = TimeUnit.SECONDS

         if (!capturedRecastTimer.hasElapsed(recastSeconds, recastUnit)) {
            return
         }
      }

      this.useRod(rodHand)
   }

   @EventHandler
   private fun detectNearbySplash(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundSoundPacket) {
         val soundPacket = event.packet as ClientboundSoundPacket
         // Событие постится с netty-потока: чтение player.fishing и позиции
         // крючка переносим на главный поток клиента (гонка с тиком).
         ClientAccess.collectiveMinecraft.execute {
            val sound = soundPacket.sound
            val splashSound: Predicate<ResourceKey<SoundEvent>> = Predicate { key: ResourceKey<SoundEvent> ->
               key.location().toString()
                  .equals("minecraft:entity.fishing_bobber.splash", ignoreCase = true)
            }
            val fishingBobber = ClientAccess.collectiveMinecraft.player!!.fishing
            if (sound.`is`(splashSound) && fishingBobber != null) {
               val capturedSoundPosition: Vec3 = Vec3(soundPacket.x, soundPacket.y, soundPacket.z)
               val hookPosition: Vec3 = fishingBobber.position()

               if (capturedSoundPosition.distanceTo(hookPosition) <= 1.0) {
                  this.nearbySplashDetected = true
               }
            }
         }
      }
   }

   private fun isWaterTargetMissing(): Boolean {
      val lookDirection: Vec3 = ClientAccess.collectiveMinecraft.player!!.lookAngle
      val eyePosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.eyePosition

      val builder: BlockRaycastContext.Builder = BlockRaycastContext.Builder(
         eyePosition, eyePosition.add(lookDirection.scale(8.0))
      )
      val waterClassifier: BlockRaycastClassifier = object : BlockRaycastClassifier {
         override fun test(position: BlockPos, blockState: BlockState): BlockRaycastOverride {
            val waterBlock = Blocks.WATER
            return if (blockState.`is`(waterBlock)) BlockRaycastOverride.SOLID else BlockRaycastOverride.NONE
         }
      }
      builder.withClassifier(waterClassifier)
      val context: BlockRaycastContext = builder.build()

      val hit: BlockHitResult = RaycastUtils.raycast(context)
      if (hit.type == Type.MISS) {
         return true
      }

      val capturedLevel = ClientAccess.collectiveMinecraft.level!!
      val hitPosition: BlockPos = hit.blockPos

      val state: BlockState = capturedLevel.getBlockState(hitPosition)
      val water = Blocks.WATER

      return !state.`is`(water)
   }

   init {
      this.waterSetting = this.add(BooleanSetting("Water", false))
      this.recastDelaySetting = this.add(NumberSetting<Float>("RecastDelay", 2.0F, 1.0F, 5.0F).withUnit("s"))
      this.castTimer = ElapsedTimer()
   }

}
