package me.collective.client.managers

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min
import me.collective.client.ClientAccess
import me.collective.client.audio.BuiltinSound
import me.collective.client.audio.OfflineSoundAssets
import me.collective.client.audio.SoundClip
import me.collective.client.utils.collections.NamedIdentifier
import net.minecraft.world.phys.Vec3
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class SoundManager : ClientAccess {
   @Volatile
   private var soundsById: Map<NamedIdentifier, SoundClip> = mapOf(NamedIdentifier.NONE to SoundClip.NOOP)

   @Volatile
   private var killstreakSounds: Map<Int, List<SoundClip>> = emptyMap()

   init {
      OfflineSoundAssets.installKillstreaks(ConfigurationManager.SOUNDS_DIRECTORY.resolve("killstreaks"))
   }

   val soundIds: Set<NamedIdentifier>
      @JvmName("getSoundIdsProp")
      get() = this.soundsById.keys

   fun getSoundIds(): Set<NamedIdentifier> = this.soundIds

   private fun loadDirectory(directory: Path): List<SoundClip> {
      val clips = ArrayList<SoundClip>()
      try {
         Files.createDirectories(directory)
         Files.list(directory).use { paths ->
            for (file in paths.filter { Files.isRegularFile(it) }.filter { it.toString().endsWith(".ogg") }.toList()) {
               try {
                  clips.add(SoundClip(Files.readAllBytes(file)))
               } catch (failure: IOException) {
                  LOGGER.warn("Could not load sound {}", file.fileName, failure)
               }
            }
         }
      } catch (failure: IOException) {
         LOGGER.warn("Could not read sound directory {}", directory.fileName, failure)
      }
      return clips.toList()
   }

   private fun loadSoundCategory(directory: Path, sounds: MutableMap<NamedIdentifier, SoundClip>) {
      try {
         Files.list(directory).use { files ->
            for (file in files.filter { Files.isRegularFile(it) }.filter { it.toString().endsWith(".ogg") }.toList()) {
               val filename = file.fileName.toString()
               val name = filename.substring(0, filename.length - 4)
               try {
                  sounds[NamedIdentifier(directory.fileName.toString(), name)] = SoundClip(Files.readAllBytes(file))
               } catch (failure: Exception) {
                  LOGGER.warn("Could not load custom sound {}", filename, failure)
               }
            }
         }
      } catch (failure: IOException) {
         LOGGER.warn("Could not read sound category {}", directory.fileName, failure)
      }
   }

   fun reload() {
      val streaks = HashMap<Int, List<SoundClip>>()
      for (index in KILLSTREAK_DIRECTORIES.indices) {
         val directory = ConfigurationManager.SOUNDS_DIRECTORY.resolve("killstreaks").resolve(KILLSTREAK_DIRECTORIES[index])
         streaks[index + 2] = loadDirectory(directory)
      }
      this.killstreakSounds = streaks.toMap()

      val sounds = HashMap<NamedIdentifier, SoundClip>()
      sounds[NamedIdentifier.NONE] = SoundClip.NOOP
      for (bundled in BuiltinSound.values()) {
         if (bundled == BuiltinSound.NONE) continue
         val resource = "/assets/collective/sounds/${bundled.identifier.name}.ogg"
         try {
            SoundManager::class.java.getResourceAsStream(resource).use { stream ->
               if (stream == null) {
                  LOGGER.warn("Bundled sound is missing: {}", bundled.identifier.name)
               } else {
                  sounds[bundled.identifier] = SoundClip(stream.readAllBytes())
               }
            }
         } catch (failure: IOException) {
            LOGGER.warn("Could not load bundled sound {}", bundled.identifier.name, failure)
         }
      }
      try {
         Files.createDirectories(ConfigurationManager.SOUNDS_DIRECTORY)
         Files.list(ConfigurationManager.SOUNDS_DIRECTORY).use { directories ->
            for (directory in directories.filter { Files.isDirectory(it) }.toList()) {
               if (!directory.fileName.toString().equals("killstreaks", ignoreCase = true)) {
                  loadSoundCategory(directory, sounds)
               }
            }
         }
      } catch (failure: IOException) {
         LOGGER.warn("Could not list custom sounds", failure)
      }

      this.soundsById = sounds.toMap()
   }

   fun getSound(identifier: NamedIdentifier?): SoundClip = getSound(this, identifier)

   fun playSound(identifier: NamedIdentifier?, position: Vec3, volume: Float) {
      getSound(identifier).playAt(position, volume)
   }

   fun playSound(identifier: NamedIdentifier?, volume: Float) {
      getSound(identifier).play(volume)
   }

   fun getKillstreakSound(killCount: Int): SoundClip? {
      if (killCount < 2 || (killCount > 8 && killCount % 4 != 0)) return null
      val sounds = this.killstreakSounds.getOrDefault(min(killCount, 8), emptyList())
      return if (sounds.isEmpty()) null else sounds[ThreadLocalRandom.current().nextInt(sounds.size)]
   }

   companion object {
      private val LOGGER: Logger = LogManager.getLogger("Collective Sound System")
      private val KILLSTREAK_DIRECTORIES = listOf(
         "doublekill_2",
         "triplekill_3",
         "dominating_4",
         "megakill_5",
         "unstoppable_6",
         "wickedsick_7",
         "monsterkill_8up"
      )

      @JvmStatic
      fun getSound(manager: SoundManager, identifier: NamedIdentifier?): SoundClip {
         return if (identifier == null) SoundClip.NOOP else manager.soundsById.getOrDefault(identifier, SoundClip.NOOP)
      }
   }
}
