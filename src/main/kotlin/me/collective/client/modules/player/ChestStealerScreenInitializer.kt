package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.TaskScheduler
import me.collective.client.settings.options.ChestStealerModeChoice

import me.collective.mixin.AbstractContainerScreenAccessor
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Button.Builder
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen
import net.minecraft.network.chat.Component
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit

internal class ChestStealerScreenInitializer(private val module: ChestStealerModule) : ClientAccess, AfterInit {
   override fun afterInit(minecraft: Minecraft, screen: Screen, screenWidth: Int, screenHeight: Int) {
      if (screen is ContainerScreen || screen is ShulkerBoxScreen) {
         if (this.module.isToggled()) {
            if (this.module.onlyButtonsSetting.value!!) {
               this.module.requestMode(null)
               val buttonWidth = 45
               var buttonX = (screen.width - (screen as AbstractContainerScreenAccessor).bgWidth) / 2 - buttonWidth
               buttonX -= 2
               var buttonY = (screen.height - (screen as AbstractContainerScreenAccessor).bgHeight) / 2

               for (mode in ChestStealerModeChoice.entries) {
                  val buttonBuilder = Builder(
                     Component.literal(mode.getName()),
                     Button.OnPress { _ ->
                        if (mode == this.module.getRequestedMode()) {
                           val scheduler = ClientContext.taskScheduler
                           val clearFocus = Runnable {
                              (screen as AbstractContainerEventHandler).setFocused(null)
                           }
                           scheduler!!.scheduleRotationTask(clearFocus, 0)
                           this.module.requestMode(null)
                        } else {
                           this.module.requestMode(mode)
                        }
                     }
                  )

                  buttonBuilder.bounds(buttonX, buttonY, buttonWidth, 15)
                  buttonY += 17
                  Screens.getWidgets(screen).add(buttonBuilder.build())
               }

               return
            }
         }
      }
   }
}
