package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.managers.InputOverrideManager
import me.collective.client.managers.SocialRegistry
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.exploit.RocketExtenderModule
import me.collective.client.modules.movement.FireworksModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.phys.EntityHitResult
import org.lwjgl.glfw.GLFW

class MiddleClickModule : Module("MiddleClick", "Middle click actions.", ModuleCategory.MISC) {
   private val friendsSetting: Setting<Boolean>
   private val fireworksSetting: Setting<Boolean>
   private val onGroundSetting: Setting<Boolean>
   private val alternativeSetting: Setting<Boolean>
   private var middleButtonHeld: Boolean = false
   private var launchAgainNextTick: Boolean = false

   private fun handleMiddleClick() {
      val hitResult = ClientAccess.collectiveMinecraft.hitResult
      var canLaunch = true
      if (!PlayerStateUtils.isFallFlying()) {
         val fireworksSnapshot: FireworksModule? = fireworksModule

         val forceFastLaunch = false
         if (!fireworksSnapshot!!.canFastLaunch(forceFastLaunch)) {
            canLaunch = false
            val fastLaunchOption: Setting<Boolean> = fireworksModule!!.fastLaunchSetting
            val fastLaunch = fastLaunchOption.value
            if (fastLaunch) {
               canLaunch = PlayerStateUtils.isLocalWearingElytra()
            }
         }
      }
      val groundPlayer = ClientAccess.collectiveMinecraft.player!!
      if (groundPlayer!!.onGround()) {
         val onGroundOption: Setting<Boolean> = this.onGroundSetting
         val allowOnGround = onGroundOption.value
         if (!allowOnGround) {
            canLaunch = false
         }
      }

      if (canLaunch) {
         val fireworksOption: Setting<Boolean> = this.fireworksSetting
         val useFireworks = fireworksOption.value
         if (useFireworks) {
            val launchGroundPlayer = ClientAccess.collectiveMinecraft.player!!
            if (launchGroundPlayer!!.onGround()) {
               val jumpManagerSnapshot: InputOverrideManager = ClientContext.inputOverrides
               jumpManagerSnapshot.requestJump(true)

               this.launchFirework()
               this.launchAgainNextTick = true
            } else {

               this.launchFirework()
            }

            return
         }
      }

      if (hitResult is EntityHitResult) {
         val hitEntity = hitResult.entity
         if (hitEntity is Player) {
            val friendsOption: Setting<Boolean> = this.friendsSetting
            val toggleFriends = friendsOption.value
            if (toggleFriends) {
               val profile = hitEntity.gameProfile
               val playerName = profile.name
               val friendCheckerSnapshot: SocialRegistry = ClientContext.socialRegistry

               if (friendCheckerSnapshot.isFriendName(playerName)) {
                  val friendRemoverSnapshot: SocialRegistry = ClientContext.socialRegistry

                  friendRemoverSnapshot.removeSocial(playerName)
               } else {
                  val friendAdderSnapshot: SocialRegistry = ClientContext.socialRegistry

                  friendAdderSnapshot.addFriend(playerName)
               }
            }
         }
      }
   }

   fun launchFirework() {
      var rocketItem: Item = Items.FIREWORK_ROCKET

      val heldRocketHand = InventoryUtils.getHandForItem(rocketItem)
      val selectionPlayer = ClientAccess.collectiveMinecraft.player!!
      val selectionInventory = selectionPlayer!!.inventory
      val previousHotbarSlot = selectionInventory.selected
      val inventoryRocketItem: Item = Items.FIREWORK_ROCKET
      val inventoryRocketSlot = InventoryUtils.findMenuSlot(inventoryRocketItem)
      rocketItem = Items.FIREWORK_ROCKET

      val hotbarRocketSlot = InventoryUtils.findHotbarSlot(rocketItem)
      if (heldRocketHand != null) {
         val heldGameMode = ClientAccess.collectiveMinecraft.gameMode ?: return
         val heldUsePlayer = ClientAccess.collectiveMinecraft.player!!
         heldGameMode.useItem(heldUsePlayer!!, heldRocketHand)
      } else if (inventoryRocketSlot != -1) {
         var useInventorySwap = true
         if (hotbarRocketSlot != -1) {
            val alternativeOption: Setting<Boolean> = this.alternativeSetting
            val alternative = alternativeOption.value
            useInventorySwap = alternative
         }

         this.selectOrSwapRocket(hotbarRocketSlot, inventoryRocketSlot, useInventorySwap)
         val inventoryGameMode = ClientAccess.collectiveMinecraft.gameMode ?: return
         val inventoryUsePlayer = ClientAccess.collectiveMinecraft.player!!
         val inventoryUseHand = InteractionHand.MAIN_HAND
         inventoryGameMode.useItem(inventoryUsePlayer!!, inventoryUseHand)
         val extender: RocketExtenderModule? = rocketExtenderModule
         val launchPositionPlayer = ClientAccess.collectiveMinecraft.player!!
         extender!!.launchPosition = launchPositionPlayer!!.position()
         this.selectOrSwapRocket(previousHotbarSlot, inventoryRocketSlot, useInventorySwap)
      }
   }

   private fun selectOrSwapRocket(hotbarSlot: Int, inventorySlot: Int, useInventorySwap: Boolean) {
      if (useInventorySwap) {

         InventoryUtils.swapIntoSelectedSlot(inventorySlot)
      } else {

         InventoryUtils.selectHotbarSlotForPlacement(hotbarSlot)
      }
   }

   @EventHandler
   private fun onLocalPlayerPreTick(event: LocalPlayerPreTickEvent) {
      if (this.launchAgainNextTick) {

         this.launchFirework()
         this.launchAgainNextTick = false
      }

      if (ClientAccess.collectiveMinecraft.screen == null) {
         val pressClient = ClientAccess.collectiveMinecraft
         val pressWindow = pressClient.window
         var windowHandle = pressWindow.window

         if (GLFW.glfwGetMouseButton(windowHandle, 2) == 1 &&
         !this.middleButtonHeld) {
            this.middleButtonHeld = true

            this.handleMiddleClick()
         } else {
            val releaseClient = ClientAccess.collectiveMinecraft
            val releaseWindow = releaseClient.window
            windowHandle = releaseWindow.window

            if (GLFW.glfwGetMouseButton(windowHandle, 2) == 0) {
               this.middleButtonHeld = false
            }
         }
      }
   }

   init {
      friendsSetting = add(BooleanSetting("Friends", true))
      val fireworks = BooleanSetting("Fireworks", true)
      fireworks.markVisibilityDependency()
      fireworksSetting = add(fireworks)
      onGroundSetting = add(BooleanSetting("OnGround", true).dependsOn(this.fireworksSetting))
      alternativeSetting = add(BooleanSetting("Alternative", false).dependsOn(this.fireworksSetting))
      this.setDrawn(false)
   }

   companion object {

      private var fireworksModule: FireworksModule? = null
      private var rocketExtenderModule: RocketExtenderModule? = null

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         fireworksModule = registry.getModule(FireworksModule::class.java) as FireworksModule
         rocketExtenderModule = registry.getModule(RocketExtenderModule::class.java) as RocketExtenderModule
      }
   }

}
