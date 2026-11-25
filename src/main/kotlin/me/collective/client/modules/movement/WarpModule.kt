package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.managers.TickRateController
import me.collective.client.events.EventStage
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.WarpRechargeChoice
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.phys.Vec3

class WarpModule : Module("Warp", "Allows you to dash forward after standing still for a certain amount of time.", ModuleCategory.MOVEMENT, "tickshift") {
   companion object {
      private var fakeLag: FakeLagModule? = null

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         fakeLag = registry.getModule(FakeLagModule::class.java) as FakeLagModule
      }
   }

   @JvmField
   val modeSetting: Setting<ModeChoice>

   @JvmField
   val boostSetting: Setting<Int>

   @JvmField
   val chargeSetting: Setting<Int>

   @JvmField
   val chargeSpeedSetting: Setting<Int>

   @JvmField
   val rechargeSetting: Setting<WarpRechargeChoice>

   protected val fakeLagSetting: Setting<Boolean>

   protected val disableGroundSetting: Setting<Boolean>

   protected val autoDisableSetting: Setting<Boolean>

   protected val rechargeController: WarpRechargeController

   protected var receivedCorrection: Boolean = false

   @JvmField
   @Volatile
   var plainCharge: Int = 0

   fun getCharge(): Int {
      return if (this.modeSetting.value == ModeChoice.ALTERNATIVE)
         this.rechargeController.pendingPongs.size
      else
         this.plainCharge
   }

   private fun updatePlainWarp() {
      if (!this.hasDisplacement()) {
         ClientContext.tickRateController.releaseMultiplier(this)
         this.setFakeLagEnabled(false)
         this.receivedCorrection = false
      } else if (this.plainCharge == 0 ||
         this.receivedCorrection) {
         ClientContext.tickRateController.releaseMultiplier(this)
         if (this.autoDisableSetting.value) {
            this.toggle()
         }
         this.setFakeLagEnabled(false)
      } else if (this.plainCharge > 0) {
         if (this.fakeLagSetting.value) {
            val wasFakeLagEnabled: Boolean = fakeLag!!.isToggled()
            val groundPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            var enableFakeLag: Boolean = true
            if (!groundPlayer.onGround()) {
               val requireGround: Boolean = this.disableGroundSetting.value
               enableFakeLag = !requireGround
            }
            this.setFakeLagEnabled(enableFakeLag)
            if (!wasFakeLagEnabled) {
               return
            }
         }

         val boostControllerSnapshot: TickRateController = ClientContext.tickRateController
         val boost: Float = this.boostSetting.value.toFloat()
         boostControllerSnapshot.requestMultiplier(this, boost)
      }
   }

   fun hasMovementInputOrDisplacement(): Boolean {
      if (ClientAccess.collectiveMinecraft.player!!.input.moveVector.length() > 0.0f) {
         return true
      }
      if (ClientAccess.collectiveMinecraft.player!!.x - ClientAccess.collectiveMinecraft.player!!.xo != 0.0) {
         return true
      }
      if (ClientAccess.collectiveMinecraft.player!!.y - ClientAccess.collectiveMinecraft.player!!.yo != 0.0) {
         return true
      }
      return ClientAccess.collectiveMinecraft.player!!.z - ClientAccess.collectiveMinecraft.player!!.zo != 0.0
   }

   private fun updateAlternativeWarp() {
      if (!this.rechargeController.pendingPongs.isEmpty()) {
         if (this.hasMovementInputOrDisplacement()) {
            val boostControllerSnapshot: TickRateController.PlayerTicks = ClientContext.playerTickController
            val boost: Float = this.boostSetting.value.toFloat()
            boostControllerSnapshot.requestMultiplier(this, boost)
            val connection: ClientCommonPacketListenerImpl = ClientAccess.collectiveMinecraft.player!!.connection
            val packet: Packet<*> = this.rechargeController.pendingPongs.poll()
            connection.send(packet)
            return
         }
      }

      if (!this.rechargeController.pendingPongs.isEmpty() &&
         !this.receivedCorrection) {
         ClientContext.playerTickController.releaseMultiplier(this)
      } else {
         ClientContext.playerTickController.releaseMultiplier(this)
         if (this.autoDisableSetting.value) {
            this.toggle()
         }
      }
   }

   override fun getInfo(): String? {
      return this.getCharge().toString()
   }

   override fun onEnable() {
      this.receivedCorrection = false
      if (this.rechargeSetting.value == WarpRechargeChoice.INSTANT) {
         this.plainCharge = this.chargeSetting.value
      }
   }

   override fun onDisable() {
      this.setFakeLagEnabled(false)
      if (this.modeSetting.value == ModeChoice.ALTERNATIVE && !this.isWorldUnavailable()) {
         try {
            while (
               !this.rechargeController.pendingPongs.isEmpty()
            ) {
               val connection: ClientCommonPacketListenerImpl = ClientAccess.collectiveMinecraft.player!!.connection
               val packet: Packet<*> = this.rechargeController.pendingPongs.poll()
               connection.send(packet)
            }
         } catch (ignored: Exception) {
         }

         this.rechargeController.pendingPongs.clear()
      }
   }

   private fun setFakeLagEnabled(enabled: Boolean) {
      if (this.fakeLagSetting.value && ClientAccess.collectiveMinecraft.player != null) {
         fakeLag!!.setToggled(enabled)
      }
   }

   fun hasDisplacement(): Boolean {
      val positionSnapshot: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
      val previousX: Double = ClientAccess.collectiveMinecraft.player!!.xo
      val previousY: Double = ClientAccess.collectiveMinecraft.player!!.yo
      val previousZ: Double = ClientAccess.collectiveMinecraft.player!!.zo
      return positionSnapshot.distanceToSqr(previousX, previousY, previousZ) > 0.0
   }

   @EventHandler
   private fun onIncomingPacket(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundPlayerPositionPacket) {
         this.receivedCorrection = true
      }
   }

   @EventHandler
   private fun onSendPosition(event: LocalPlayerSendPositionEvent) {
      if (EventStage.Pre == event.stage) {
         if (this.modeSetting.value == ModeChoice.ALTERNATIVE) {
            this.updateAlternativeWarp()
         } else {
            this.updatePlainWarp()
         }
      }
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.PLAIN))
      this.boostSetting = this.add(NumberSetting<Int>("Boost", 2, 1, 50))
      this.chargeSetting = this.add(NumberSetting<Int>("Charge", 20, 1, 100))
      val chargeSpeedVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.PLAIN }
      this.chargeSpeedSetting = this.add(NumberSetting<Int>("ChargeSpeed", 1, 1, 4, chargeSpeedVisibility))
      val rechargeVisibility: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.PLAIN }
      this.rechargeSetting = this.add(EnumSetting<WarpRechargeChoice>("Recharge", WarpRechargeChoice.NONE, rechargeVisibility))
      this.fakeLagSetting = this.add(BooleanSetting("FakeLag", false).markVisibilityDependency())
      this.disableGroundSetting = this.add(BooleanSetting("DisableGround", false).dependsOn(this.fakeLagSetting))
      this.autoDisableSetting = this.add(BooleanSetting("AutoDisable", false))
      this.rechargeController = WarpRechargeController(this)
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      PLAIN("Plain"),
      ALTERNATIVE("Alternative");

      override fun getName(): String {
         return this.displayName
      }
   }
}
