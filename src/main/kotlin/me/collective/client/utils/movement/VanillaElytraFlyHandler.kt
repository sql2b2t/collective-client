package me.collective.client.utils.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.managers.SwingInteractionManager
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.movement.ElytraFlyModule
import me.collective.client.modules.movement.FireworksModule
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

class VanillaElytraFlyHandler(module: ElytraFlyModule) : ElytraFlyModeHandler(module) {
   private val fireworkAction: VanillaElytraFlyHandler.FireworkAction = VanillaElytraFlyHandler.FireworkAction()
   private var descendingPhase: Boolean = false
   private var fireworkAscentPending: Boolean = false
   private var targetPitch: Float = 0.0f

   private fun computeInitialPitch(): Float {
      if (this.isWorldUnavailable()) {
         return 40.0f
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return 40.0f
      return player.getXRot()
   }

   override fun onEnable() {
      this.descendingPhase = true

      this.targetPitch = this.computeInitialPitch()
   }

   fun getTargetPitch(): Float {
      return this.targetPitch
   }

   private fun isFlightActive(): Boolean {
      return if (this.module.infDurabilitySetting.value.isDisabled())
         PlayerStateUtils.isFallFlying()
      else {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         this.module.isDurabilityBypassActive() && !PlayerStateUtils.isInWater(player)
      }
   }

   private fun isGrimAutoLaunchActive(): Boolean {
      return fireworksModule!!.isToggled() && fireworksModule!!.grimSetting.value &&
         fireworksModule!!.boostSetting.value && fireworksModule!!.autoLaunchSetting.value
   }

   override fun handlePrePlayerTick(event: LocalPlayerPreTickEvent) {
      val grimAutoLaunch: Boolean = this.isGrimAutoLaunchActive()
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (!this.isFlightActive()) {
         this.targetPitch = this.computeInitialPitch()
      } else {
         if (antiCheatModule!!.is2b2tEnabled()) {
            if (player.isUsingItem()) {
               return
            }
         }

         if (!EntityUtils.hasFirework(player) || grimAutoLaunch) {
            if (this.fireworkAction.isUsePending()) {
               this.fireworkAction.run()
            } else {
               if (!this.descendingPhase) {
                  val fireworkHeight: Double = player.getY()
                  val fireworkMinimumValue = this.module.minYSetting.value
                  val fireworkMinimumY: Int = fireworkMinimumValue

                  if (fireworkHeight
                     <= fireworkMinimumY - 10) {
                     val triggerFirework: Boolean = this.module.useFireworkSetting.value
                     if (triggerFirework) {
                        if (player.getDeltaMovement().y < 0.0) {
                           this.fireworkAscentPending = true
                        }
                     }
                  }
               }

               val ascentHeight: Double = player.getY()
               if (ascentHeight >= this.getMaximumAltitude()) {
                  this.fireworkAscentPending = false
               }

               if (this.fireworkAscentPending) {
                  val usePendingFirework: Boolean = this.module.useFireworkSetting.value
                  if (usePendingFirework && !grimAutoLaunch) {
                     this.fireworkAction.requestUseAfterDelay()
                     return
                  }
               }

               val descentHeight: Double = player.getY()
               if (descentHeight >= this.getMaximumAltitude()) {
                  this.descendingPhase = true
               } else {
                  val currentMinimumHeight: Double = player.getY()
                  val descentMinimumY = this.module.minYSetting.value
                  if (currentMinimumHeight <= descentMinimumY && this.descendingPhase) {
                     this.descendingPhase = false
                  }
               }

               if (this.descendingPhase) {
                  val previousDescendingPitch: Float = this.targetPitch

                  this.targetPitch = previousDescendingPitch + (if (grimAutoLaunch) 8.0f else 2.0f)
               } else {
                  val previousAscendingPitch: Float = this.targetPitch

                  this.targetPitch = previousAscendingPitch - 5.45f
               }

               val unclampedPitch: Float = this.targetPitch

               this.targetPitch = Mth.clamp(unclampedPitch, -40.0f, 40.0f)
            }
         }
      }
   }

   override fun handleClientTick(event: MinecraftRunTickEvent) {
      if (!this.isWorldUnavailable()) {

         if (this.isFlightActive()) {
            if (event.hasPendingTicks) {
               val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
               if (EntityUtils.hasFirework(player)) {

                  if (this.isGrimAutoLaunchActive()) {
                     player.setXRot(this.targetPitch)
                  } else {
                     this.targetPitch = -75.0f
                  }
               }

               val rotationManager: RotationManager = ClientContext.rotationManager
               val pitch: Float = this.targetPitch
               val boxedPitch: Float? = pitch

               val rotationPriority: Int = 999
               val forceRotation: Boolean = true
               val yaw: Float? = null
               rotationManager.requestRotation(yaw, boxedPitch, rotationPriority, forceRotation)
               ClientContext.rotationManager.forceRotationOverride()
            }
         }
      }
   }

   private fun getMaximumAltitude(): Int {
      val minimumValue = this.module.minYSetting.value
      val minimumY: Int = minimumValue
      val heightSetting: Setting<Int> = this.module.heightSetting
      val height: Int = heightSetting.value
      return minimumY + height
   }

   companion object {
      private var antiCheatModule: AntiCheatModule? = null
      private var fireworksModule: FireworksModule? = null

      @JvmStatic
      fun bindModuleReferences(registry: ModuleRegistry) {
         antiCheatModule = registry.getModule(
            AntiCheatModule::class.java
         ) as AntiCheatModule
         fireworksModule = registry.getModule(FireworksModule::class.java) as FireworksModule
      }
   }

   private class FireworkAction : Runnable {
      private val lastUseTimer: ElapsedTimer = ElapsedTimer()
      private var usePending: Boolean = false

      override fun run() {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
         val inventory: Inventory = player.getInventory()
         val selectedSlot: Int = inventory.getSelectedSlot()
         val includeInventory: Boolean = true
         val firework: Item = Items.FIREWORK_ROCKET
         val fireworkSlot: InventoryUtils.SwapSlot = InventoryUtils.findSwapSlot(
            firework, includeInventory
         )
         if (fireworkSlot.isFound()) {
            InventoryUtils.swapTo(fireworkSlot)
            val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode ?: return
            val usePlayer: LocalPlayer = player
            val useHand: InteractionHand = fireworkSlot.hand
            gameMode.useItem(usePlayer, useHand)
            val swingAction: SwingAction = SwingAction.INTERACT
            val swingHand: InteractionHand = InteractionHand.MAIN_HAND
            val swingManager: SwingInteractionManager = ClientContext.swingManager
            swingManager.swingPacket(swingHand, swingAction)
            InventoryUtils.swapBack(selectedSlot, fireworkSlot)
            val useTimer: ElapsedTimer = this.lastUseTimer

            useTimer.reset()
            this.usePending = false
         }
      }

      fun isUsePending(): Boolean {
         return this.usePending
      }

      fun requestUseAfterDelay() {
         val useTimer: ElapsedTimer = this.lastUseTimer

         val delayMillis: Long = 250L
         if (useTimer.hasElapsed(delayMillis)) {
            this.usePending = true
         }
      }

   }
}
