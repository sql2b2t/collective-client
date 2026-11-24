package me.collective.client.managers;

import me.collective.client.audio.BuiltinSound;
import me.collective.client.audio.SoundClip;
import me.collective.client.utils.collections.NamedIdentifier;

import me.collective.client.ClientAccess;
import me.collective.client.audio.OfflineSoundAssets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SoundManager implements ClientAccess {
   private static final Logger LOGGER = LogManager.getLogger("Collective Sound System");
   private static final List<String> KILLSTREAK_DIRECTORIES = List.of(
      "doublekill_2", "triplekill_3", "dominating_4", "megakill_5",
      "unstoppable_6", "wickedsick_7", "monsterkill_8up"
   );
   private volatile Map<NamedIdentifier, SoundClip> soundsById = Map.of(NamedIdentifier.NONE, SoundClip.NOOP);
   private volatile Map<Integer, List<SoundClip>> killstreakSounds = Map.of();

   public SoundManager() {
      OfflineSoundAssets.installKillstreaks(ConfigurationManager.SOUNDS_DIRECTORY.resolve("killstreaks"));
   }

   public Set<NamedIdentifier> getSoundIds() {
      return this.soundsById.keySet();
   }

   private List<SoundClip> loadDirectory(Path directory) {
      List<SoundClip> clips = new ArrayList<>();
      try {
         Files.createDirectories(directory);
         try (var paths = Files.list(directory)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".ogg")).toList()) {
               try {
                  clips.add(new SoundClip(Files.readAllBytes(file)));
               } catch (IOException failure) {
                  LOGGER.warn("Could not load sound {}", file.getFileName(), failure);
               }
            }
         }
      } catch (IOException failure) {
         LOGGER.warn("Could not read sound directory {}", directory.getFileName(), failure);
      }
      return List.copyOf(clips);
   }

   private void loadSoundCategory(Path directory, Map<NamedIdentifier, SoundClip> sounds) {
      try (var files = Files.list(directory)) {
         for (Path file : files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".ogg")).toList()) {
            String filename = file.getFileName().toString();
            String name = filename.substring(0, filename.length() - 4);
            try {
               sounds.put(new NamedIdentifier(directory.getFileName().toString(), name), new SoundClip(Files.readAllBytes(file)));
            } catch (IOException | RuntimeException failure) {
               LOGGER.warn("Could not load custom sound {}", filename, failure);
            }
         }
      } catch (IOException failure) {
         LOGGER.warn("Could not read sound category {}", directory.getFileName(), failure);
      }
   }

   public void reload() {
      Map<Integer, List<SoundClip>> streaks = new HashMap<>();
      for (int index = 0; index < KILLSTREAK_DIRECTORIES.size(); index++) {
         Path directory = ConfigurationManager.SOUNDS_DIRECTORY.resolve("killstreaks").resolve(KILLSTREAK_DIRECTORIES.get(index));
         streaks.put(index + 2, loadDirectory(directory));
      }
      this.killstreakSounds = Map.copyOf(streaks);
      Map<NamedIdentifier, SoundClip> sounds = new HashMap<>();
      sounds.put(NamedIdentifier.NONE, SoundClip.NOOP);
      for (BuiltinSound bundled : BuiltinSound.values()) {
         if (bundled == BuiltinSound.NONE) continue;
         String resource = "/assets/collective/sounds/" + bundled.getIdentifier().getName() + ".ogg";
         try (InputStream stream = SoundManager.class.getResourceAsStream(resource)) {
            if (stream == null) {
               LOGGER.warn("Bundled sound is missing: {}", bundled.getIdentifier().getName());
               continue;
            }
            sounds.put(bundled.getIdentifier(), new SoundClip(stream.readAllBytes()));
         } catch (IOException failure) {
            LOGGER.warn("Could not load bundled sound {}", bundled.getIdentifier().getName(), failure);
         }
      }
      try {
         Files.createDirectories(ConfigurationManager.SOUNDS_DIRECTORY);
         try (var directories = Files.list(ConfigurationManager.SOUNDS_DIRECTORY)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
               if (!directory.getFileName().toString().equalsIgnoreCase("killstreaks")) loadSoundCategory(directory, sounds);
            }
         }
      } catch (IOException failure) {
         LOGGER.warn("Could not list custom sounds", failure);
      }

      this.soundsById = Map.copyOf(sounds);
   }

   public SoundClip getSound(NamedIdentifier identifier) {
      return getSound(this, identifier);
   }

   public static SoundClip getSound(SoundManager manager, NamedIdentifier identifier) {
      Objects.requireNonNull(manager);
      return identifier == null ? SoundClip.NOOP : manager.soundsById.getOrDefault(identifier, SoundClip.NOOP);
   }

   public void playSound(NamedIdentifier identifier, Vec3 position, float volume) {
      getSound(identifier).playAt(position, volume);
   }

   public void playSound(NamedIdentifier identifier, float volume) {
      getSound(identifier).play(volume);
   }

   public SoundClip getKillstreakSound(int killCount) {
      if (killCount < 2 || killCount > 8 && killCount % 4 != 0) return null;
      List<SoundClip> sounds = this.killstreakSounds.getOrDefault(Math.min(killCount, 8), List.of());
      return sounds.isEmpty() ? null : sounds.get(ThreadLocalRandom.current().nextInt(sounds.size()));
   }
}
