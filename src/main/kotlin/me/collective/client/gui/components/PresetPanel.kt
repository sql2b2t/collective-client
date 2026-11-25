package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientContext
import me.collective.client.config.PresetData
import me.collective.client.config.PresetUndoAction
import me.collective.client.gui.CloseState
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.PresetScreen
import me.collective.client.managers.PresetRegistry
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.StringSetting
import me.collective.client.settings.options.PresetCategory
import me.collective.client.utils.DesktopUtils
import org.apache.commons.lang3.SystemUtils
import java.io.IOException
import java.nio.file.Path

class PresetPanel(private val screen: PresetScreen) : Panel("Preset") {
   private val saveNameSetting = StringSetting("Save", "")
   private val categorySetting = EnumSetting("Category", PresetCategory.MODULES)
   private var mode: PresetPanelMode = PresetPanelMode.NEW
   private var selectedPreset: PresetData? = null
   private var listRefreshPending = false
   private var controlsRebuildPending = false

   init {
      this.saveNameSetting.setChangeCallback {
         try {
            val action = PresetUndoAction.beforeSave(
               this.selectedCategoryPresets, this.saveNameSetting.value
            )
            this.selectedCategoryPresets.savePreset(this.saveNameSetting.value)
            ClientContext.configurationManager.presetUndoState.push(action)
            this.requestListRefresh()
         } catch (saveFailure: IOException) {
            saveFailure.printStackTrace()
         }
         this.saveNameSetting.value = ""
      }

      this.categorySetting.setChangeCallback {
         this.screen.selectedCategory = this.categorySetting.value
         this.requestListRefresh()
      }
   }

   override fun init() {
      this.rebuildControls()
   }

   override fun render(poses: PoseStack, mouseX: Double, mouseY: Double) {
      if (this.controlsRebuildPending) {
         this.rebuildControls()
         this.controlsRebuildPending = false
      }

      if (this.listRefreshPending) {
         this.screen.listPanel.refreshPresets()
         this.listRefreshPending = false
      }

      super.render(poses, mouseX, mouseY)
   }

   fun rebuildControls() {
      this.children.clear()
      when (this.mode) {
         PresetPanelMode.NEW -> {
            this.addChild(
               EnumSettingComponent(
                  this,
                  CloseState.ALWAYS_OPEN,
                  this.categorySetting
               )
            )
            this.addChild(
               StringSettingComponent(
                  this,
                  CloseState.ALWAYS_OPEN,
                  this.saveNameSetting
               )
            )
            this.addChild(
               ActionButtonComponent(this, "Refresh", Runnable { this.requestListRefresh() })
            )
            this.addChild(
               ActionButtonComponent(
                  this,
                  "Undo",
                  Runnable {
                     try {
                        ClientContext.configurationManager.presetUndoState.undo()
                        this.requestListRefresh()
                     } catch (undoFailure: IOException) {
                        undoFailure.printStackTrace()
                     }
                  }
               )
            )
         }
         PresetPanelMode.EDIT -> {
            val preset = this.selectedPreset
            this.addChild(
               ButtonComponent(
                  this,
                  "Preset " + (preset?.name ?: "")
               )
            )
            this.addChild(
               ActionButtonComponent(
                  this,
                  "Load",
                  Runnable {
                     if (preset != null) {
                        val action = PresetUndoAction.beforeLoad(preset.name)
                        if (this.selectedCategoryPresets.applyPreset(preset.name)) {
                           ClientContext.configurationManager.presetUndoState.push(action)
                        }
                     }
                  }
               )
            )
            this.addChild(
               ActionButtonComponent(
                  this,
                  "Save",
                  Runnable {
                     if (preset != null) {
                        try {
                           val action = PresetUndoAction.beforeSave(
                              this.selectedCategoryPresets, preset.name
                           )
                           this.selectedCategoryPresets.savePreset(preset.name)
                           ClientContext.configurationManager.presetUndoState.push(action)
                           this.requestListRefresh()
                        } catch (ignoredSaveFailure: IOException) {
                        }
                     }
                  }
               )
            )
            this.addChild(
               ActionButtonComponent(
                  this,
                  "Delete",
                  Runnable {
                     if (preset != null) {
                        val action = PresetUndoAction.beforeDelete(
                           this.selectedCategoryPresets, preset.name
                        )
                        if (this.selectedCategoryPresets.deletePreset(preset.name)) {
                           ClientContext.configurationManager.presetUndoState.push(action)
                        }
                     }
                     this.setMode(PresetPanelMode.NEW)
                  }
               )
            )
            this.addChild(
               ActionButtonComponent(this, "Open", Runnable { this.openPresetLocation() })
            )
            this.addChild(
               ActionButtonComponent(
                  this,
                  "Back",
                  Runnable { this.setMode(PresetPanelMode.NEW) }
               )
            )
         }
      }

      this.requestListRefresh()
   }

   private fun requestListRefresh() {
      this.listRefreshPending = true
   }

   private val selectedCategoryPresets: PresetRegistry
      get() = ClientContext.configurationManager.getPresetRegistry(this.screen.selectedCategory)

   fun editPreset(preset: PresetData) {
      this.selectedPreset = preset
      this.setMode(PresetPanelMode.EDIT)
   }

   fun getSelectedPreset(): PresetData? {
      return if (this.mode != PresetPanelMode.EDIT) null else this.selectedPreset
   }

   fun setMode(mode: PresetPanelMode) {
      this.mode = mode
      this.controlsRebuildPending = true
   }

   private fun openPresetLocation() {
      try {
         if (!SystemUtils.IS_OS_WINDOWS) {
            DesktopUtils.openPath(this.screen.selectedCategory.directory)
         } else {
            var path: Path = this.screen.selectedCategory.directory.toAbsolutePath()
            val preset = this.selectedPreset
            if (preset != null) {
               path = path.resolve(preset.name + ".json")
            }
            ProcessBuilder(*createExplorerCommand(path)).start()
         }
      } catch (ignoredOpenFailure: Throwable) {
      }
   }

   companion object {
      private fun createExplorerCommand(path: Path): Array<String> {
         return arrayOf("explorer.exe", "/select,", path.toString())
      }
   }
}
