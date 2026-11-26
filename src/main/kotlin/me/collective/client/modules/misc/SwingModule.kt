package me.collective.client.modules.misc


import java.util.concurrent.ThreadLocalRandom
import java.util.function.Predicate
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.world.InteractionHand

class SwingModule : Module("Swing", "Changes your swinging hand.", ModuleCategory.MISC) {
   @JvmField
   var handSetting: Setting<HandChoice>

   @JvmField
   val typeSetting: Setting<TypeChoice>

   @JvmField
   val shakeSetting: Setting<Boolean>

   @JvmField
   val speedSetting: Setting<Double>

   private var nextShakeTimeMillis: Long = 0
   private var shakeTicksRemaining: Int = 0

   init {
      val defaultHand = HandChoice.MAINHAND
      this.handSetting = this.add(EnumSetting<HandChoice>(("Hand"), defaultHand))
      val defaultType = TypeChoice.VANILLA
      this.typeSetting = this.add(EnumSetting<TypeChoice>(("Type"), defaultType))
      val shakeVisibility = Predicate<Any> { this.typeSetting.value == TypeChoice.ONE_TWELVE }
      val defaultShake = false
      this.shakeSetting = this.add(BooleanSetting(("Shake"), defaultShake, shakeVisibility))
      this.speedSetting = this.add(NumberSetting<Double>("Speed", 1.0, 0.1, 3.0))
   }

   fun consumeShakeTick(): Boolean {
      return if (this.shakeTicksRemaining > 0) {
         this.shakeTicksRemaining--
         true
      } else {
         false
      }
   }

   @EventHandler
   private fun onConnectionSendPacketEvent(
      event: PacketEvent.Send
   ) {
      if (event.packet !is ServerboundSwingPacket) {
         return
      }
      if (System.currentTimeMillis() <= this.nextShakeTimeMillis) {
         return
      }
      var nextShakeTime = System.currentTimeMillis()
      val random = ThreadLocalRandom.current()

      nextShakeTime += random.nextInt(350)

      this.nextShakeTimeMillis = nextShakeTime + 150L

      this.shakeTicksRemaining = 3
   }

   enum class HandChoice(private val displayName: String, val hand: InteractionHand?) : NamedEntry {
      VANILLA("Vanilla", null),
      MAINHAND("Mainhand", InteractionHand.MAIN_HAND),
      OFFHAND("Offhand", InteractionHand.OFF_HAND),
      PACKET("Packet", null);

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class TypeChoice(private val displayName: String) : NamedEntry {
      VANILLA("Vanilla"),
      ONE_TWELVE("1.12"),
      ONE_EIGHT("1.8");

      override fun getName(): String {
         return this.displayName
      }
   }
}
