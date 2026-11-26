package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.bus.EventBus
import net.fabricmc.loader.api.FabricLoader

class EntityCullingOverrides : ClientAccess {

   companion object {

      private val CULLING_ACCESS: EntityCullingAdapter? =
         if (FabricLoader.getInstance().isModLoaded("entityculling")) EntityCullingAdapter() else null
      private var cullingOverrideActive: Boolean = false
      private var savedCullingEnabled: Boolean = false
      private var savedTickCullingEnabled: Boolean = false
      private var tickCullingRestoreDelay: Int = 0

      init {
         ClientAccess.eventBus.subscribeStatic(EntityCullingOverrides::class.java)
      }

      @JvmStatic
      fun restoreCulling() {
         if (cullingOverrideActive &&
         CULLING_ACCESS != null) {
            cullingOverrideActive = false
            CULLING_ACCESS.setCullingEnabled(savedCullingEnabled)
         }
      }

      @JvmStatic
      @EventHandler
      private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
         val tickEvent = event
         if (tickEvent.hasPendingTicks &&
         tickCullingRestoreDelay > 0) {
            tickCullingRestoreDelay--
            if (tickCullingRestoreDelay == 0) {
               CULLING_ACCESS!!.setTickCullingEnabled(savedTickCullingEnabled)
            }
         }
      }

      @JvmStatic
      fun disableCulling() {
         if (!cullingOverrideActive &&
         CULLING_ACCESS != null) {
            cullingOverrideActive = true
            savedCullingEnabled = CULLING_ACCESS.isCullingEnabled()
            CULLING_ACCESS.setCullingEnabled(false)
            disableTickCulling()
         }
      }

      private fun disableTickCulling() {
         if (tickCullingRestoreDelay == 0) {
            savedTickCullingEnabled = CULLING_ACCESS!!.isTickCullingEnabled()
         }
         tickCullingRestoreDelay = 2
         CULLING_ACCESS!!.setTickCullingEnabled(false)
      }

   }

}
