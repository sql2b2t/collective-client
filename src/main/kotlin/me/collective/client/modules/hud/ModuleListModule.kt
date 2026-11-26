package me.collective.client.modules.hud

import me.collective.client.modules.client.HUDModule.ModuleSortMode

import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.render.FontRenderer
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.Module
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.HudAnchor

import java.util.ArrayList
import java.util.Comparator

class ModuleListModule : HudWidgetModule("ModuleList", "arraylist") {
   @JvmField
   val onlyBoundSetting: Setting<Boolean>
   @JvmField
   val sortSetting: Setting<ModuleSortMode>
   private val moduleRows: MutableList<ModuleListHudRow>

   override fun onEnable() {
      this.moduleRows.clear()
      val registry: ModuleRegistry = ClientContext.moduleRegistry
      val registeredModules: List<Module> = registry.getRegistry() as List<Module>
      registeredModules.forEach { registeredModule ->
         this.moduleRows.add(ModuleListHudRow(this, registeredModule))
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val sortMode: Setting<ModuleSortMode> = this.sortSetting
      if (sortMode.value == ModuleSortMode.ALPHABET) {
         this.moduleRows.sortWith(
            Comparator.comparing<ModuleListHudRow, String> { row ->
               val module: Module = row.module
               module.infoString
            }
         )
      } else {
         this.moduleRows.sortWith(
            Comparator.comparingDouble<ModuleListHudRow> { row ->
               val font = FontRenderer.INSTANCE
               val module: Module = row.module
               -font.getWidth(module.infoString).toDouble()
            }
         )
      }
   }

   init {
      this.onlyBoundSetting = this.add(BooleanSetting("OnlyBound", false))

      this.sortSetting = this.add(EnumSetting<ModuleSortMode>("Sort", ModuleSortMode.LENGTH))
      this.moduleRows = ArrayList()
      this.setRenderer(
         HudTextListRenderer(this, this.moduleRows)
      )

      this.getRenderer()
         .setAnchor(HudAnchor.TOP_RIGHT)
   }
}
