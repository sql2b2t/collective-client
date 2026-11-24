package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.managers.TaskScheduler
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting

import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket

class AutoRespawnModule : Module("AutoRespawn", "Respawns automatically.", ModuleCategory.MISC) {
   private val respawnTimer: ElapsedTimer = ElapsedTimer()
   private val saySetting: Setting<Boolean>
   private val messageSetting: Setting<String>

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (ClientAccess.collectiveMinecraft.screen is DeathScreen) {
         val timer = this.respawnTimer
         if (timer.hasElapsed(100L)) {
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            player.respawn()
            this.respawnTimer.reset()
         }
      }
   }

   @EventHandler
   private fun onPacketReceive(event: PacketEvent.Receive) {
      if (event.packet is ClientboundRespawnPacket) {
         val saySnapshot = this.saySetting
         val say: Boolean = saySnapshot.value
         if (say) {
            val scheduler = ClientContext.taskScheduler
            val sendMessage = Runnable {
               val messageSnapshot = this.messageSetting
               val message: String = messageSnapshot.value
               ChatUtils.sendChatOrCommand(message)
            }
            scheduler.scheduleRotationTask(sendMessage, 2)
         }
      }

      if (event.packet is ClientboundPlayerCombatKillPacket) {
         this.respawnTimer.reset()
      }
   }

   init {
      val sayDefault = false
      val say = BooleanSetting("Say", sayDefault)
      say.markVisibilityDependency()
      this.saySetting = this.add(say)
      val message = StringSetting("Message", "/kit cattyn")
      val dependencies = arrayOf<Setting<*>>(this.saySetting)
      this.messageSetting = this.add(message.dependsOn(*dependencies))

      this.setDrawn(false)
   }

}
