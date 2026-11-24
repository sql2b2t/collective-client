package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.bus.EventBus
import me.collective.client.render.GameOptionsOverrides
import me.collective.client.settings.Setting

import net.minecraft.client.OptionInstance
import net.minecraft.client.Options

class ZoomOptionsUpdater(zoomModule: ZoomModule) : ClientAccess {
   private val zoomModule: ZoomModule
   private var originalSmoothCamera: Boolean? = null

   init {
      this.zoomModule = zoomModule
   }

   @EventHandler
   private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
      val fovModule: ZoomModule = this.zoomModule
      val fov: Int = fovModule.calculateFov()
      val fovOption: OptionInstance<*> = ClientAccess.collectiveMinecraft.options.fov()
      GameOptionsOverrides.forceSetOptionValue(fovOption, fov)
      val cameraOptions: Options = ClientAccess.collectiveMinecraft.options
      // Запоминаем пользовательский smoothCamera и возвращаем его при отписке:
      // раньше настройка безвозвратно перетиралась и попадала в options.txt.
      if (this.originalSmoothCamera == null) {
         this.originalSmoothCamera = cameraOptions.smoothCamera
      }
      val toggleModule: ZoomModule = this.zoomModule
      var smoothCamera: Boolean = false
      if (toggleModule.isToggled()) {
         val smoothCameraSetting: Setting<Boolean> = this.zoomModule.smoothCameraSetting
         smoothCamera = smoothCameraSetting.value!!
      }
      cameraOptions.smoothCamera = smoothCamera
      if (fov >= this.zoomModule.baselineFov) {
         val currentToggleModule: ZoomModule = this.zoomModule
         if (!currentToggleModule.isToggled()) {
            val bus: EventBus = ClientAccess.eventBus
            bus.unsubscribe(this)
            this.zoomModule.fovUpdateActive = false
            this.originalSmoothCamera?.let { restored -> cameraOptions.smoothCamera = restored }
            this.originalSmoothCamera = null
            return
         }
      }
      this.zoomModule.fovUpdateActive = true
   }

}
