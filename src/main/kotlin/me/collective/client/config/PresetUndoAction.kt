package me.collective.client.config

import com.google.gson.JsonElement
import me.collective.client.managers.PresetRegistry
import me.collective.client.settings.options.PresetCategory
import java.io.IOException

sealed interface PresetUndoAction {
    @Throws(IOException::class)
    fun undo()

    companion object {
        @JvmStatic
        fun beforeLoad(name: String): PresetUndoAction {
            return Load(name, PresetCategory.ALL.toJson())
        }

        @JvmStatic
        fun beforeSave(registry: PresetRegistry, name: String): PresetUndoAction {
            return Save(registry, name, registry.getPreset(name))
        }

        @JvmStatic
        fun beforeDelete(registry: PresetRegistry, name: String): PresetUndoAction? {
            val preset: PresetData? = registry.getPreset(name)
            return if (preset == null) null else Delete(registry, preset)
        }

        @JvmStatic
        fun beforeRename(registry: PresetRegistry, oldName: String, newName: String): PresetUndoAction {
            return Rename(registry, oldName, newName, registry.getPreset(newName), registry.getPreset(oldName))
        }
    }

    data class Load(
        @get:JvmName("name") val name: String,
        val snapshot: JsonElement
    ) : PresetUndoAction {
        override fun undo() {
            PresetCategory.ALL.fromJson(this.snapshot)
        }
    }

    data class Save(
        val registry: PresetRegistry,
        @get:JvmName("name") val name: String,
        val previous: PresetData?
    ) : PresetUndoAction {
        @Throws(IOException::class)
        override fun undo() {
            if (this.previous == null) {
                this.registry.deletePresetForUndo(this.name)
            } else {
                this.registry.restorePreset(this.previous, this.name)
            }
        }
    }

    data class Delete(
        val registry: PresetRegistry,
        @get:JvmName("preset") val preset: PresetData
    ) : PresetUndoAction {
        @Throws(IOException::class)
        override fun undo() {
            this.registry.restorePreset(this.preset)
        }
    }

    data class Rename(
        val registry: PresetRegistry,
        @get:JvmName("oldName") val oldName: String,
        @get:JvmName("newName") val newName: String,
        val previousTarget: PresetData?,
        val source: PresetData?
    ) : PresetUndoAction {
        @Throws(IOException::class)
        override fun undo() {
            this.registry.renamePresetForUndo(this.newName, this.oldName, this.previousTarget, this.source)
        }
    }
}
