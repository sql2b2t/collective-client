package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.bus.EventBus
import me.collective.client.utils.entity.SidePlayer
import me.collective.client.render.ForcedEntityLayers
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import java.awt.Color
import java.util.ArrayDeque
import java.util.Queue
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class FakeLagModule : Module("FakeLag", "Cancels movement packets until toggled off.", ModuleCategory.MOVEMENT) {
   private val bufferedPackets: Queue<Packet<*>> = ArrayDeque()
   val modeSetting: Setting<ModeChoice>
   private val cancelSetting: Setting<Boolean>
   private val timeoutSetting: Setting<Int>
   private val autoDisableSetting: Setting<Boolean>
   private val onDamageSetting: Setting<Boolean>
   private val onRubberBandSetting: Setting<Boolean>
   private val onTimeSetting: Setting<Float>
   @JvmField
   val renderSetting: Setting<Boolean>
   @JvmField
   val chamsSetting: Setting<Boolean>
   @JvmField
   val fillSetting: Setting<Color>
   @JvmField
   val outlineSetting: Setting<Color>
   @JvmField
   val lineWidthSetting: Setting<Float>
   private val packetsSetting: Setting<Boolean>
   private val movementSetting: Setting<Boolean>
   private val interactSetting: Setting<Boolean>
   private val swingSetting: Setting<Boolean>
   private val swapSetting: Setting<Boolean>
   private val interactEntitySetting: Setting<Boolean>
   private val diggingSetting: Setting<Boolean>
   private val pingSetting: Setting<Boolean>
   private val allSetting: Setting<Boolean>
   private val lastReleaseTimer: ElapsedTimer = ElapsedTimer()
   private val enabledDurationTimer: ElapsedTimer = ElapsedTimer()
   private val pulseTimer: ElapsedTimer = ElapsedTimer()
   @JvmField
   var bufferedPlayerSnapshot: SidePlayer? = null
   private var bufferedPositionBounds: AABB? = null

   private fun releaseBufferedPackets() {
      while (!this.bufferedPackets.isEmpty()) {
         val packet: Packet<*> = this.bufferedPackets.poll()!!

         PacketSender.sendDirect(packet)
      }

      this.bufferedPositionBounds = this.captureTrackedPositionBounds()
      if (this.modeSetting.value == FakeLagModule.ModeChoice.PULSE) {
         this.lastReleaseTimer.reset()
      }
   }

   override fun getInfo(): String? {
      val elapsedSeconds: Float = this.lastReleaseTimer.elapsedMillis().toFloat() / 1000.0f
      return String.format("%.1f", Math.max(elapsedSeconds - 0.1f, 0.0f))
   }

   override fun onEnable() {
      this.lastReleaseTimer.reset()
      this.bufferedPackets.clear()
      this.bufferedPlayerSnapshot = null
      if (this.isWorldUnavailable()) {
         this.disable()
      } else {
         this.bufferedPositionBounds = this.captureTrackedPositionBounds()
         this.enabledDurationTimer.reset()
         this.pulseTimer.reset()
      }
   }

   override fun onDisable() {
      if (!this.isWorldUnavailable()) {
         this.releaseBufferedPackets()
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (this.modeSetting.value == ModeChoice.PULSE &&
         this.pulseTimer.hasElapsed(this.timeoutSetting.value.toLong())) {
         this.releaseBufferedPackets()
         this.pulseTimer.reset()
      }
      if (this.autoDisableSetting.value && !this.onTimeSetting.isAtMinimum()) {
         val durationSeconds: Double = this.onTimeSetting.value.toDouble()
         if (this.enabledDurationTimer.hasElapsed(durationSeconds, TimeUnit.SECONDS)) {
            this.toggle()
         }
      }
   }

   @EventHandler(priority = -200)
   private fun onConnectionSendPacketEvent(
      event: PacketEvent.Send
   ) {
      if (this.bufferedPackets.isEmpty()) {
         this.bufferedPlayerSnapshot = SidePlayer(
            ClientAccess.collectiveMinecraft.player!!
         )
         (this.bufferedPlayerSnapshot!! as Any as ForcedEntityLayers).`collective$setForcedLayers`(true)
         val snapshot: SidePlayer = this.bufferedPlayerSnapshot!!

         val trackedPosition: Vec3 = ClientContext.playerStateTracker.getPacketPosition()
         snapshot.setPos(trackedPosition)
      }

      val packetToClassify: Packet<*> = event.packet
      if (!this.shouldBufferPacket(packetToClassify)) {
         return
      }
      if (event.isCancelled) {
         return
      }
      val packetQueue: Queue<Packet<*>> = this.bufferedPackets
      val packetToBuffer: Packet<*> = event.packet
      packetQueue.add(packetToBuffer)
      event.cancel()
   }

   @EventHandler
   private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
      this.disable()
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundPlayerPositionPacket) {
         if (this.modeSetting.value == FakeLagModule.ModeChoice.PULSE) {
            this.pulseTimer.clear()
         }

         if (this.autoDisableSetting.value) {
            if (this.onRubberBandSetting.value) {
               this.toggle()
            }
         }
      }

      if (event.packet is ClientboundDamageEventPacket) {
         val damagedEntityId: Int = event.packet.entityId
         if (damagedEntityId == ClientAccess.collectiveMinecraft.player!!.getId()) {
            if (this.autoDisableSetting.value) {
               if (this.onDamageSetting.value) {
                  this.toggle()
               }
            }
         }
      }
   }

   private fun captureTrackedPositionBounds(): AABB {
      var bounds: AABB = ClientAccess.collectiveMinecraft.player!!.getBoundingBox()
      val bottomCenter: Vec3 = bounds.getBottomCenter()
      val originScale: Double = -1.0
      val originOffset: Vec3 = bottomCenter.scale(originScale)
      bounds = bounds.move(originOffset)

      val trackedPosition: Vec3 = ClientContext.playerStateTracker.getPacketPosition()
      return bounds.move(trackedPosition)
   }

   private fun shouldBufferPacket(packet: Packet<*>): Boolean {
      return packet is ServerboundMovePlayerPacket && this.movementSetting.value ||
         packet is ServerboundUseItemOnPacket && this.interactSetting.value ||
         packet is ServerboundInteractPacket && this.interactEntitySetting.value ||
         packet is ServerboundPlayerActionPacket && this.diggingSetting.value ||
         packet is ServerboundSwingPacket && this.swingSetting.value ||
         packet is ServerboundSetCarriedItemPacket && this.swapSetting.value ||
         (packet is ServerboundPongPacket ||
         packet is ServerboundKeepAlivePacket ||
         packet is ServerboundResourcePackPacket ||
         packet is ServerboundPingRequestPacket) && this.pingSetting.value ||
         this.allSetting.value
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", FakeLagModule.ModeChoice.BLINK))
      this.cancelSetting = this.add(BooleanSetting("Cancel", false))
      val timeoutName = "Timeout"
      val timeoutVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.PULSE }
      this.timeoutSetting = this.add(NumberSetting<Int>(timeoutName, 200, 50, 1000, timeoutVisibility).withUnit("ms"))
      this.autoDisableSetting = this.add(BooleanSetting("AutoDisable", false).markVisibilityDependency())
      this.onDamageSetting = this.add(BooleanSetting("OnDamage", false).dependsOn(this.autoDisableSetting))
      this.onRubberBandSetting = this.add(BooleanSetting("OnRubberBand", false).dependsOn(this.autoDisableSetting))
      val onTimeSettingValue = NumberSetting<Float>("OnTime", 0.0f, 0.0f, 5.0f)
      onTimeSettingValue.withUnit("s")
      this.onTimeSetting = this.add(onTimeSettingValue.dependsOn(this.autoDisableSetting))
      this.renderSetting = this.add(BooleanSetting("Render", true).markVisibilityDependency())
      this.chamsSetting = this.add(BooleanSetting("Chams", false).dependsOn(this.renderSetting))
      val fillVisibility = Predicate<Any> { ignored -> this.renderSetting.isExpanded() }
      this.fillSetting = this.add(ColorSetting("Fill", Color(1354603007, true), fillVisibility))
      val outlineVisibility = Predicate<Any> { ignored -> this.renderSetting.isExpanded() }
      val outlineDefault = Color(1186830847, true)
      this.outlineSetting = this.add(ColorSetting("Outline", outlineDefault, outlineVisibility))
      val lineWidthVisibility = Predicate<Any> { ignored -> this.renderSetting.isExpanded() }
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0f, 0.1f, 2.0f, lineWidthVisibility))
      this.packetsSetting = this.add(BooleanSetting("Packets", false).markAsGroup().markVisibilityDependency())
      this.movementSetting = this.add(BooleanSetting("Movement", true).dependsOn(this.packetsSetting))
      this.interactSetting = this.add(BooleanSetting("Interact", true).dependsOn(this.packetsSetting))
      this.swingSetting = this.add(BooleanSetting("Swing", true).dependsOn(this.packetsSetting))
      this.swapSetting = this.add(BooleanSetting("Swap", true).dependsOn(this.packetsSetting))
      this.interactEntitySetting = this.add(BooleanSetting("InteractEntity", false).dependsOn(this.packetsSetting))
      this.diggingSetting = this.add(BooleanSetting("Digging", false).dependsOn(this.packetsSetting))
      this.pingSetting = this.add(BooleanSetting("Ping", false).dependsOn(this.packetsSetting))
      this.allSetting = this.add(BooleanSetting("All", false).dependsOn(this.packetsSetting))

      val timeoutVisibilitySetting: Setting<Int> = this.timeoutSetting
      val timeoutModeVisibility = Predicate<Int> { ignored -> this.modeSetting.value == FakeLagModule.ModeChoice.PULSE }
      timeoutVisibilitySetting.addVisibilityPredicate(timeoutModeVisibility)
      val cancelVisibilitySetting: Setting<Boolean> = this.cancelSetting
      val cancelModeVisibility = Predicate<Boolean> { ignored -> this.modeSetting.value == FakeLagModule.ModeChoice.BLINK }
      cancelVisibilitySetting.addVisibilityPredicate(cancelModeVisibility)
      val cancelCallbackSetting: Setting<Boolean> = this.cancelSetting
      val cancelCallback = Runnable {
         if (!this.cancelSetting.value) {
            return@Runnable
         }
         this.cancelSetting.value = false

         if (!this.cancelSetting.isVisible()) {
            return@Runnable
         }
         if (this.isWorldUnavailable()) {
            return@Runnable
         }
         if (!this.isToggled()) {
            return@Runnable
         }
         val rollbackBounds: AABB = this.bufferedPositionBounds!!
         val rollbackPosition: Vec3 = BoundingBoxUtils.getBottomCenter(
            rollbackBounds
         )
         ClientAccess.collectiveMinecraft.player!!.xo = rollbackPosition.x
         ClientAccess.collectiveMinecraft.player!!.yo = rollbackPosition.y
         ClientAccess.collectiveMinecraft.player!!.zo = rollbackPosition.z
         ClientAccess.collectiveMinecraft.player!!.setPos(rollbackPosition)
         ClientAccess.collectiveMinecraft.player!!.setDeltaMovement(Vec3.ZERO)
         this.bufferedPackets.clear()
         this.disable()
      }
      cancelCallbackSetting.setChangeCallback(cancelCallback)
      val eventBus: EventBus = ClientAccess.eventBus
      val renderer = FakeLagRenderer(this)
      eventBus.subscribe(renderer)
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      BLINK("Blink"),
      PULSE("Pulse");

      override fun getName(): String {
         return this.displayName
      }
   }
}
