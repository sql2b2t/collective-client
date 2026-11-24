package me.collective.client.managers;

import me.collective.client.config.LegacySocialMigration;
import me.collective.client.config.ModulePresetCodec;
import me.collective.client.config.PresetUndoState;
import me.collective.client.render.BundledResourceInstaller;
import me.collective.client.utils.FileUtils;

import me.collective.client.ClientAccess;
import me.collective.client.commands.PanicCommand;
import me.collective.client.settings.JsonConfigurable;
import me.collective.client.settings.options.PresetCategory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigurationManager implements ClientAccess {
   public static final Path ROOT_DIRECTORY;
   public static final Path PRESETS_DIRECTORY;
   public static final Path SPAMMER_DIRECTORY;
   public static final Path KILLSAY_DIRECTORY;
   public static final Path TEXTURES_DIRECTORY;
   public static final Path MAPS_DIRECTORY;
   public static final Path CHUNKS_DIRECTORY;
   public static final Path SOUNDS_DIRECTORY;
   public static final Path SHADERS_DIRECTORY;
   public static final Path USER_HOME_MARKER_PATH;
   private static final List<Path> directories;
   private final ModulePresetCodec modulePresetCodec = new ModulePresetCodec();
   private final Map<PresetCategory, PresetRegistry> presetRegistries = new HashMap<>();
   private final BundledResourceInstaller bundledResources = new BundledResourceInstaller();
   private final PresetUndoState presetUndoState = new PresetUndoState();
   private final ConfigurableRegistry configurables = new ConfigurableRegistry();

   public ModulePresetCodec getModulePresetCodec() {
      return this.modulePresetCodec;
   }

   public PresetUndoState getPresetUndoState() {
      return this.presetUndoState;
   }

   public void load() {
      directories.clear();
      directories.addAll(List.of(ROOT_DIRECTORY, PRESETS_DIRECTORY, SPAMMER_DIRECTORY, KILLSAY_DIRECTORY,
         TEXTURES_DIRECTORY, MAPS_DIRECTORY, SOUNDS_DIRECTORY, SHADERS_DIRECTORY, CHUNKS_DIRECTORY));
      for (PresetCategory category : PresetCategory.values()) directories.add(category.getDirectory());

      this.prepareDirectories();

      try {

         LegacySocialMigration.migrate();
      } catch (Throwable ignoredBootstrapFailure) {
      }

      Iterator<?> configurableIterator = this.configurables.iterator();

      while (configurableIterator.hasNext()) {
         JsonConfigurable configurable = (JsonConfigurable)configurableIterator.next();

         try {
            Path configPath = ROOT_DIRECTORY.resolve(configurable.getConfigName());
            File configFile = configPath.toFile();
            if (configFile.exists()) {
               String configText = FileUtils.readUtf8(configPath);
               JsonElement configJson = JsonParser.parseString(configText);
               configurable.fromJson(configJson.getAsJsonObject());
            }
         } catch (Throwable loadFailure) {
            loadFailure.printStackTrace();
         }
      }

      for (PresetCategory presetCategory : PresetCategory.values()) {
         PresetRegistry presetRegistry = new PresetRegistry(presetCategory);
         this.presetRegistries.put(presetCategory, presetRegistry);

         presetRegistry.loadPresets();
      }
   }

   public void prepareDirectories() {
      try {
         Path legacyAutoEzPath = ROOT_DIRECTORY.resolve("autoez");
         File legacyAutoEz = legacyAutoEzPath.toFile();
         if (legacyAutoEz.isDirectory()) {
            File killsayTarget = KILLSAY_DIRECTORY.toFile();
            legacyAutoEz.renameTo(killsayTarget);
         }
      } catch (Exception ignoredMigrationFailure) {
      }

      for (Path path : directories) {
         try {
            Files.createDirectories(path);
         } catch (IOException failure) {
            ClientAccess.logger.warn("Could not create configuration directory {}", path.getFileName(), failure);
         }
      }
      this.bundledResources.installMissingResources();
   }

   static {

      Path parentDirectory = FabricLoader.getInstance().getGameDir();

      ROOT_DIRECTORY = me.collective.client.config.ClientDataDirectory.migrateLegacy(parentDirectory.resolve("collective"));
      parentDirectory = ROOT_DIRECTORY;

      PRESETS_DIRECTORY = parentDirectory.resolve("presets");
      parentDirectory = ROOT_DIRECTORY;

      SPAMMER_DIRECTORY = parentDirectory.resolve("spammer");
      parentDirectory = ROOT_DIRECTORY;

      KILLSAY_DIRECTORY = parentDirectory.resolve("killsay");
      parentDirectory = ROOT_DIRECTORY;

      TEXTURES_DIRECTORY = parentDirectory.resolve("textures");
      parentDirectory = ROOT_DIRECTORY;

      MAPS_DIRECTORY = parentDirectory.resolve("maps");
      parentDirectory = ROOT_DIRECTORY;

      CHUNKS_DIRECTORY = parentDirectory.resolve("chunks");
      parentDirectory = ROOT_DIRECTORY;

      SOUNDS_DIRECTORY = parentDirectory.resolve("sounds");
      parentDirectory = ROOT_DIRECTORY;

      SHADERS_DIRECTORY = parentDirectory.resolve("shaders");

      String userHome = System.getProperty("user.home");
      USER_HOME_MARKER_PATH = Path.of(userHome, "_____a");
      directories = new ArrayList<>();
   }

   public PresetRegistry getPresetRegistry(PresetCategory category) {
      return this.presetRegistries.get(category);
   }

   public void save() {
      if (!PanicCommand.unloaded) {
         Iterator<?> configurableIterator = this.configurables.iterator();

         while (configurableIterator.hasNext()) {
            JsonConfigurable configurable = (JsonConfigurable)configurableIterator.next();

            try {
               Path configPath = ROOT_DIRECTORY.resolve(configurable.getConfigName());
               Gson gsonSnapshot = ClientAccess.gson;
               JsonElement serialized = configurable.toJson();
               JsonObject object = serialized.getAsJsonObject();
               FileUtils.writeUtf8(configPath, gsonSnapshot.toJson(object));
            } catch (Throwable saveFailure) {
               saveFailure.printStackTrace();
            }
         }
      }
   }

}
