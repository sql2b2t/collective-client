package me.collective.client.managers

import com.google.gson.JsonParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import me.collective.client.ClientAccess
import me.collective.client.commands.PanicCommand
import me.collective.client.config.ClientDataDirectory
import me.collective.client.config.LegacySocialMigration
import me.collective.client.config.ModulePresetCodec
import me.collective.client.config.PresetUndoState
import me.collective.client.render.BundledResourceInstaller
import me.collective.client.settings.JsonConfigurable
import me.collective.client.settings.options.PresetCategory
import me.collective.client.utils.FileUtils
import net.fabricmc.loader.api.FabricLoader

class ConfigurationManager : ClientAccess {
   private val modulePresetCodecValue = ModulePresetCodec()
   private val presetRegistries: MutableMap<PresetCategory, PresetRegistry> = HashMap()
   private val bundledResources = BundledResourceInstaller()
   private val presetUndoStateValue = PresetUndoState()
   private val configurables = ConfigurableRegistry()

   val modulePresetCodec: ModulePresetCodec
      @JvmName("getModulePresetCodecProp")
      get() = this.modulePresetCodecValue

   fun getModulePresetCodec(): ModulePresetCodec = this.modulePresetCodecValue

   val presetUndoState: PresetUndoState
      @JvmName("getPresetUndoStateProp")
      get() = this.presetUndoStateValue

   fun getPresetUndoState(): PresetUndoState = this.presetUndoStateValue

   fun load() {
      directories.clear()
      directories.addAll(
         listOf(
            ROOT_DIRECTORY,
            PRESETS_DIRECTORY,
            SPAMMER_DIRECTORY,
            KILLSAY_DIRECTORY,
            TEXTURES_DIRECTORY,
            MAPS_DIRECTORY,
            SOUNDS_DIRECTORY,
            SHADERS_DIRECTORY,
            CHUNKS_DIRECTORY
         )
      )
      for (category in PresetCategory.values()) {
         directories.add(category.directory)
      }

      this.prepareDirectories()

      try {
         LegacySocialMigration.migrate()
      } catch (_: Throwable) {
      }

      for (item in this.configurables) {
         val configurable = item as JsonConfigurable
         try {
            val configPath = ROOT_DIRECTORY.resolve(configurable.getConfigName())
            val configFile = configPath.toFile()
            if (configFile.exists()) {
               val configText = FileUtils.readUtf8(configPath)
               val configJson = JsonParser.parseString(configText)
               configurable.fromJson(configJson.asJsonObject)
            }
         } catch (loadFailure: Throwable) {
            loadFailure.printStackTrace()
         }
      }

      for (presetCategory in PresetCategory.values()) {
         val presetRegistry = PresetRegistry(presetCategory)
         this.presetRegistries[presetCategory] = presetRegistry
         presetRegistry.loadPresets()
      }
   }

   fun prepareDirectories() {
      try {
         val legacyAutoEz = ROOT_DIRECTORY.resolve("autoez").toFile()
         if (legacyAutoEz.isDirectory) {
            legacyAutoEz.renameTo(KILLSAY_DIRECTORY.toFile())
         }
      } catch (_: Exception) {
      }

      for (path in directories) {
         try {
            Files.createDirectories(path)
         } catch (failure: IOException) {
            ClientAccess.logger.warn("Could not create configuration directory {}", path.fileName, failure)
         }
      }
      this.bundledResources.installMissingResources()
   }

   fun getPresetRegistry(category: PresetCategory): PresetRegistry =
      this.presetRegistries[category] ?: throw IllegalArgumentException("Unknown preset category: $category")

   fun save() {
      if (!PanicCommand.unloaded) {
         for (item in this.configurables) {
            val configurable = item as JsonConfigurable
            try {
               val configPath = ROOT_DIRECTORY.resolve(configurable.getConfigName())
               val serialized = configurable.toJson()
               val jsonObject = serialized.asJsonObject
               FileUtils.writeUtf8(configPath, ClientAccess.gson.toJson(jsonObject))
            } catch (saveFailure: Throwable) {
               saveFailure.printStackTrace()
            }
         }
      }
   }

   companion object {
      @JvmField
      val ROOT_DIRECTORY: Path

      @JvmField
      val PRESETS_DIRECTORY: Path

      @JvmField
      val SPAMMER_DIRECTORY: Path

      @JvmField
      val KILLSAY_DIRECTORY: Path

      @JvmField
      val TEXTURES_DIRECTORY: Path

      @JvmField
      val MAPS_DIRECTORY: Path

      @JvmField
      val CHUNKS_DIRECTORY: Path

      @JvmField
      val SOUNDS_DIRECTORY: Path

      @JvmField
      val SHADERS_DIRECTORY: Path

      @JvmField
      val USER_HOME_MARKER_PATH: Path

      private val directories: MutableList<Path> = ArrayList()

      init {
         val gameDir = FabricLoader.getInstance().gameDir
         ROOT_DIRECTORY = ClientDataDirectory.migrateLegacy(gameDir.resolve("collective"))
         PRESETS_DIRECTORY = ROOT_DIRECTORY.resolve("presets")
         SPAMMER_DIRECTORY = ROOT_DIRECTORY.resolve("spammer")
         KILLSAY_DIRECTORY = ROOT_DIRECTORY.resolve("killsay")
         TEXTURES_DIRECTORY = ROOT_DIRECTORY.resolve("textures")
         MAPS_DIRECTORY = ROOT_DIRECTORY.resolve("maps")
         CHUNKS_DIRECTORY = ROOT_DIRECTORY.resolve("chunks")
         SOUNDS_DIRECTORY = ROOT_DIRECTORY.resolve("sounds")
         SHADERS_DIRECTORY = ROOT_DIRECTORY.resolve("shaders")
         val userHome = System.getProperty("user.home")
         USER_HOME_MARKER_PATH = Path.of(userHome, "_____a")
      }
   }
}
