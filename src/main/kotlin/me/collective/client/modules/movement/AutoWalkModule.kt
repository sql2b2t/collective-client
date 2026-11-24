package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.AbstractClientPlayerTickEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.utils.combat.ExtraBreakTarget
import me.collective.client.audio.BuiltinSound
import me.collective.client.audio.SoundClip
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.utils.collections.NamedIdentifier
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

class AutoWalkModule : Module("AutoWalk", "Lets your forward key rest.", ModuleCategory.MOVEMENT) {
   companion object {
      private var speedMineModule: SpeedMineModule? = null

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         speedMineModule = registry.getModule(SpeedMineModule::class.java) as SpeedMineModule
      }
   }

   private val autoJumpSetting: Setting<Boolean>
   private val stuckNotifySetting: Setting<Boolean>
   private val soundSetting: Setting<NamedIdentifier>
   private val volumeSetting: Setting<Float>
   private val pauseSetting: Setting<Boolean>
   private val miningSetting: Setting<Boolean>
   private val eatingSetting: Setting<Boolean>
   private var stuckTicks: Int = 0

   init {
      this.autoJumpSetting = this.add(BooleanSetting("AutoJump", false))
      this.stuckNotifySetting = this.add(BooleanSetting("StuckNotify", false).markVisibilityDependency())
      this.soundSetting = this.add(IdentifierSetting("Sound", BuiltinSound.WARNING.identifier).dependsOn(this.stuckNotifySetting))
      this.volumeSetting = this.add(NumberSetting<Float>("Volume", 1.0f, 0.0f, 1.0f).dependsOn(this.stuckNotifySetting))
      this.pauseSetting = this.add(BooleanSetting("Pause", false).markAsGroup())
      this.miningSetting = this.add(BooleanSetting("Mining", false).dependsOn(this.pauseSetting))
      this.eatingSetting = this.add(BooleanSetting("Eating", false).dependsOn(this.pauseSetting))
   }

   override fun onDisable() {
      ClientContext.inputOverrides.requestForward(false)
      ClientContext.inputOverrides.requestJump(false)
      this.stuckTicks = 0
   }

   private fun isMining(): Boolean {
      val sm = speedMineModule
      if (sm == null || !sm.isToggled) {
         return ClientAccess.collectiveMinecraft.gameMode?.isDestroying() == true
      }
      val miningPosition: BlockPos? = sm.miningPos
      val extraTarget: ExtraBreakTarget? = sm.extraBreakTarget
      return WorldUtils.canMineBlock(miningPosition) ||
         extraTarget != null && WorldUtils.canMineBlock(extraTarget.position)
   }

   private fun shouldPauseForAction(): Boolean {
      return this.eatingSetting.value && ClientAccess.collectiveMinecraft.player!!.isUsingItem() ||
         this.miningSetting.value && this.isMining()
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (this.shouldPauseForAction()) {
         return
      }
      val verticalDistance = Math.abs(ClientAccess.collectiveMinecraft.player!!.y - ClientAccess.collectiveMinecraft.player!!.yo)
      if (ClientContext.movementSpeedTracker.currentSpeed != 0.0 || !(verticalDistance < 0.2)) {
         this.stuckTicks = 0
         return
      }
      if (this.stuckTicks == 5 && this.stuckNotifySetting.value) {
         ChatUtils.printChat(Component.literal("You are stuck!"), ChatUtils.moduleSignature(this), ChatUtils.MessagePriority.HIGH)
         val sound: SoundClip = ClientContext.soundManager.getSound(this.soundSetting.value)
         sound.play(this.volumeSetting.value)
      }
      this.stuckTicks++
   }

   @EventHandler
   private fun onAbstractClientPlayerTickEvent(event: AbstractClientPlayerTickEvent) {
      if (!this.shouldPauseForAction()) {
         ClientContext.inputOverrides.requestForwardMovement(1.0f)
         ClientContext.inputOverrides.requestForward(true)
         if (this.autoJumpSetting.value) {
            ClientContext.inputOverrides.requestJump(true)
         }
      }
   }
}
