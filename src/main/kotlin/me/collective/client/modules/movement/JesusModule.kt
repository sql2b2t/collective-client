package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.events.PacketEvent
import me.collective.client.events.BlockCollisionShapeEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.mixin.ServerboundMovePlayerPacketAccessor
import java.util.function.Predicate
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class JesusModule : Module("Jesus", "Allows you to walk on water.", ModuleCategory.MOVEMENT) {
   val modeSetting: Setting<ModeChoice>
   private val strictSetting: Setting<Boolean>
   private val ascendingSetting: Setting<Float>
   private val strictYOffsetSequence: DoubleArray
   private var strictOffsetIndex: Int = 0

   override fun getInfo(): String? {
      return (this.modeSetting.value).getName()
   }

   override fun onToggle() {
      this.strictOffsetIndex = 0
   }

   private fun isStableAboveWater(): Boolean {
      if (Math.abs(ClientAccess.collectiveMinecraft.player!!.yo - ClientAccess.collectiveMinecraft.player!!.getY()) > 0.01) {
         return false
      }
      for (position in PlayerStateUtils.getOccupiedBlockPositions(ClientAccess.collectiveMinecraft.player!!)) {
         if (WorldUtils.getBlock(position.below()) !== Blocks.WATER) {
            return false
         }
      }
      return true
   }

   fun canWalkOnWater(): Boolean {
      val player = ClientAccess.collectiveMinecraft.player ?: return false
      val input = player.input ?: return false
      if (this.isWorldUnavailable() || player.isPassenger()) {
         return false
      }
      return !input.keyPresses.shift()
   }

   @EventHandler
   private fun onPlayerTick(event: LocalPlayerTickEvent) {
      if (!this.canWalkOnWater() || !ClientAccess.collectiveMinecraft.player!!.isInWater()) {
         return
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val upwardImpulse: Double = if (this.modeSetting.value == ModeChoice.DOLPHIN)
         0.0399
      else
         0.08 * this.ascendingSetting.value
      player.push(0.0, upwardImpulse, 0.0)
   }

   @EventHandler
   private fun onSendPacket(event: PacketEvent.Send) {
      if (this.modeSetting.value != ModeChoice.SOLID || !this.canWalkOnWater()) {
         return
      }
      val packet = event.packet
      if (packet !is ServerboundMovePlayerPacket) {
         return
      }
      if (!this.strictSetting.value || !this.isStableAboveWater()) {
         return
      }
      val packetAccessor: ServerboundMovePlayerPacketAccessor = packet as ServerboundMovePlayerPacketAccessor
      packetAccessor.setOnGround(false)
      packetAccessor.setY(packet.getY(0.0) + this.strictYOffsetSequence[this.strictOffsetIndex++])
      this.strictOffsetIndex %= this.strictYOffsetSequence.size
   }

   @EventHandler
   private fun onCollisionShape(event: BlockCollisionShapeEvent) {
      if (this.isWorldUnavailable()) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.isInWater()) {
         return
      }
      if (this.modeSetting.value != ModeChoice.SOLID || !this.canWalkOnWater()) {
         return
      }
      val blockYAsFloat: Float = event.pos!!.getY().toFloat()
      val liquidSurfaceY: Double = blockYAsFloat.toDouble() + 0.2f
      if (liquidSurfaceY > ClientAccess.collectiveMinecraft.player!!.getY()) {
         return
      }
      if (event.state!!.`is`(Blocks.WATER)) {
         val waterBounds: AABB = AABB(0.0, 0.0, 0.0, 1.0, 0.98, 1.0)
         val waterShape: VoxelShape = Shapes.create(waterBounds)
         event.shape = waterShape
      }
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.SOLID))
      val showStrict: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.SOLID }
      this.strictSetting = this.add(BooleanSetting("Strict", false, showStrict))
      val showAscending: Predicate<Any> = Predicate { ignored -> this.modeSetting.value == ModeChoice.SOLID }
      this.ascendingSetting = this.add(NumberSetting<Float>("Ascending", 1.0f, 0.1f, 1.0f, showAscending))
      this.strictYOffsetSequence = doubleArrayOf(
         0.08232659236482401, 0.13927999979019162, 0.1654239999427795,
         0.11427136035293574, 0.04194693730418576, 0.01236341326161235
      )
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      SOLID("Solid"),
      DOLPHIN("Dolphin");

      override fun getName(): String {
         return this.displayName
      }
   }
}
