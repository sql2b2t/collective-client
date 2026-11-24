package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.events.ClientPacketListenerHandleExplosionEvent
import me.collective.client.events.ClientPacketListenerHandleSetEntityMotionEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.FishingHookPullEntityEvent
import me.collective.client.events.LocalPlayerMoveTowardsClosestSpaceEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.utils.movement.VelocityModeHandler
import me.collective.client.utils.movement.GrimVelocityHandler
import me.collective.client.utils.movement.WallsVelocityHandler
import me.collective.client.render.FontRenderer
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.math.MathUtils

import me.collective.client.managers.RotationManager
import java.util.function.Predicate
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.world.item.enchantment.Enchantments

class VelocityModule : Module("Velocity", "Cancels all the pushing you receive.", ModuleCategory.MOVEMENT) {
   @JvmField
   val modeSetting: Setting<ModeChoice>

   @JvmField
   val horizontalSetting: Setting<Int>

   @JvmField
   val verticalSetting: Setting<Int>

   @JvmField
   val inverseSetting: Setting<Boolean>

   @JvmField
   val reduceSetting: Setting<Boolean>

   @JvmField
   val staticSetting: Setting<Boolean>

   @JvmField
   val sourcesSetting: Setting<Boolean>

   @JvmField
   val explosionsSetting: Setting<Boolean>

   @JvmField
   val pushSetting: Setting<Boolean>

   @JvmField
   val liquidsSetting: Setting<Boolean>

   @JvmField
   val fishingBobberSetting: Setting<Boolean>

   private val modeHandlers: EnumSelectionMap<VelocityModeHandler>
   var lungeMotionPending: Boolean = false

   override fun getInfo(): String? {
      if (this.modeSetting.value != ModeChoice.PLAIN) {
         val mode: ModeChoice = this.modeSetting.value
         return FontRenderer.capitalize(mode)
      }

      val horizontalDisplay: Float = MathUtils.floorToDecimals(this.horizontalSetting.value.toFloat(), 1)
      val verticalDisplay: Float = MathUtils.floorToDecimals(this.verticalSetting.value.toFloat(), 1)
      return "H" + horizontalDisplay + "% V" + verticalDisplay + "%"
   }

   override fun onDisable() {
      this.modeHandlers.selected.onDisable()
   }

   @EventHandler
   private fun onClientPacketListenerHandleExplosionEvent(
      event: ClientPacketListenerHandleExplosionEvent
   ) {
      this.modeHandlers.selected.onExplosion(event)
   }

   @EventHandler
   private fun onFishingHookPullEntityEvent(event: FishingHookPullEntityEvent) {
      if (this.fishingBobberSetting.value && event.entity === ClientAccess.collectiveMinecraft.player!!) {
         event.cancel()
      }
      this.modeHandlers.selected.onFishingPull(event)
   }

   @EventHandler
   private fun onLocalPlayerMoveTowardsClosestSpaceEvent(
      event: LocalPlayerMoveTowardsClosestSpaceEvent
   ) {
      if (this.pushSetting.value) {
         event.cancel()
      }
   }

   @EventHandler
   private fun onClientPacketListenerHandleSetEntityMotionEvent(
      event: ClientPacketListenerHandleSetEntityMotionEvent
   ) {
      this.modeHandlers.selected.onEntityMotion(event)
   }

   @EventHandler
   private fun onMultiPlayerGameModeUseItemOnEvent(event: MultiPlayerGameModeUseItemOnEvent) {
      this.modeHandlers.selected.onUseItemOn(event)
   }

   @EventHandler
   private fun onConnectionSendPacketEvent(event: PacketEvent.Send) {
      if (this.isWorldUnavailable()) {
         return
      }
      val packet = event.packet
      if (packet is ServerboundPlayerActionPacket &&
         Action.STAB == packet.action &&
         EnchantmentUtils.hasEnchantment(Enchantments.LUNGE, ClientAccess.collectiveMinecraft.player!!.mainHandItem)) {
         this.lungeMotionPending = true
      }
      this.modeHandlers.selected.onSendPacket(event)
   }

   @EventHandler(priority = -200)
   private fun onLocalPlayerPreTickEvent(event: LocalPlayerPreTickEvent) {
      this.modeHandlers.selected.onTick()
   }

   @EventHandler
   private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
      this.modeHandlers.selected.onSendPosition(event)
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      this.modeHandlers.selected.onReceivePacket(event)
   }

   companion object {
      @JvmStatic
      fun bindModules(modules: ModuleRegistry) {
         WallsVelocityHandler.bindModules(modules)
      }
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.PLAIN))
      val horizontalVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.PLAIN }
      this.horizontalSetting = this.add(NumberSetting<Int>("Horizontal", 0, 0, 100, horizontalVisibility).withUnit("%"))
      val verticalVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.PLAIN }
      this.verticalSetting = this.add(NumberSetting<Int>("Vertical", 0, 0, 100, verticalVisibility).withUnit("%"))
      val inverseVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.PLAIN }
      this.inverseSetting = this.add(BooleanSetting("Inverse", false, inverseVisibility))
      val reduceVisibility: Predicate<Any> = Predicate { ignored -> RotationManager.isMovementSyncEnabled() || this.modeSetting.value == ModeChoice.WALLS }
      this.reduceSetting = this.add(BooleanSetting("Reduce", false, reduceVisibility))
      val staticVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.WALLS }
      this.staticSetting = this.add(BooleanSetting("Static", false, staticVisibility).dependsOn(this.reduceSetting))
      this.sourcesSetting = this.add(BooleanSetting("Sources", false).markAsGroup())
      this.explosionsSetting = this.add(BooleanSetting("Explosions", true).dependsOn(this.sourcesSetting))
      this.pushSetting = this.add(BooleanSetting("Push", true).dependsOn(this.sourcesSetting))
      this.liquidsSetting = this.add(BooleanSetting("Liquids", true).dependsOn(this.sourcesSetting))
      this.fishingBobberSetting = this.add(BooleanSetting("FishingBobber", true).dependsOn(this.sourcesSetting))
      this.modeHandlers = EnumSelectionMap<VelocityModeHandler>(this.modeSetting)
      this.modeHandlers.register(ModeChoice.PLAIN, PlainVelocityHandler(this))
      this.modeHandlers.register(ModeChoice.GRIM, GrimVelocityHandler(this))
      this.modeHandlers.register(ModeChoice.WALLS, WallsVelocityHandler(this))
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      PLAIN("Plain"),
      GRIM("Grim"),
      WALLS("Walls");

      override fun getName(): String {
         return this.displayName
      }
   }
}
