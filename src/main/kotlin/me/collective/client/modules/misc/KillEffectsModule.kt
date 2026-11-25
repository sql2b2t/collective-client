package me.collective.client.modules.misc

import java.awt.Color
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectList
import it.unimi.dsi.fastutil.objects.ObjectListIterator
import it.unimi.dsi.fastutil.objects.ObjectLists

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.audio.BuiltinSound
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.PlayerAttackEvent
import me.collective.client.managers.SoundManager
import me.collective.client.managers.SocialRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.render.ClientLightningState
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting
import me.collective.client.utils.collections.NamedIdentifier
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.Registry
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

class KillEffectsModule : Module("KillEffects", "Does various things when someone dies.", ModuleCategory.MISC) {
   @JvmField
   val killSoundSetting: Setting<Boolean>
   private val soundSetting: Setting<NamedIdentifier>
   private val selfSoundSetting: Setting<NamedIdentifier>
   private val killSoundVolumeSetting: Setting<Float>
   @JvmField
   val killStreakSetting: Setting<Boolean>
   private val killStreakVolumeSetting: Setting<Float>
   @JvmField
   val killSaySetting: Setting<Boolean>
   @JvmField
   val delaySetting: Setting<Float>
   @JvmField
   val pathSetting: Setting<String>
   @JvmField
   val onlyEnemiesSetting: Setting<Boolean>
   @JvmField
   val randomSetting: Setting<Boolean>
   @JvmField
   val refreshSetting: Setting<Boolean>
   @JvmField
   val thunderSetting: Setting<Boolean>
   private val lightningsSetting: Setting<Int>
   @JvmField
   val colorSetting: Setting<Color>
   private val particlesSetting: Setting<KillEffectsParticlesChoice>
   @JvmField
   val particleTypesSetting: Setting<Set<ParticleType<*>>>
   private val ignoreNakedsSetting: Setting<Boolean>
   private val selfSetting: Setting<Boolean>
   private val killSayMessages: KillSayMessages
   private val trackedTargets: ConcurrentHashMap<UUID, Int>
   private val expiredTargets: ObjectList<UUID>
   private var killStreak: Int

   private fun spawnKillParticles(position: Vec3) {
      val particleOption = this.particlesSetting
      val particleChoiceSnapshot = particleOption.value
      val particle: ParticleOptions? = particleChoiceSnapshot.chooseParticle(this)
      if (particle == null) {
         return
      }
      val emissionOption = this.particlesSetting
      val emissionChoice = emissionOption.value
      emissionChoice.emitParticles(particle, position)
   }

   override fun onEnable() {
      this.killStreak = 0
      this.killSayMessages.reloadMessages(true)
   }

   private fun spawnKillLightning(position: Vec3) {
      val thunderOption = this.thunderSetting
      val spawnThunder = thunderOption.value
      if (!spawnThunder) {
         return
      }
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return
      var index = 0
      while (true) {
         val lightningsOption = this.lightningsSetting
         val lightningCount = lightningsOption.value
         if (index >= lightningCount) {
            return
         }

         val lightning = LightningBolt(
            EntityType.LIGHTNING_BOLT, level
         )
         lightning.setPos(position)
         lightning.moveTo(position)
         (lightning as ClientLightningState).`collective$setClient`(true)
         level.addEntity(lightning)
         index++
      }
   }

   @EventHandler
   private fun onPlayerAttackEvent(event: PlayerAttackEvent) {
      if (event.stage == EventStage.Post) {
         return
      }
      if (this.isWorldUnavailable()) {
         return
      }
      val attackTarget = event.target
      if (attackTarget !is Player) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!! === attackTarget) {
         return
      }

      val friendManager: SocialRegistry = ClientContext.socialRegistry
      if (friendManager.isFriendPlayer(attackTarget)) {
         return
      }

      if (!PlayerStateUtils.hasArmor(attackTarget)) {
         val ignoreNakedsOption = this.ignoreNakedsSetting
         val ignoreNakeds = ignoreNakedsOption.value
         if (ignoreNakeds) {
            return
         }
      }

      if (EntityUtils.getHealthWithAbsorption(attackTarget) > 0.0f) {
         try {
            val targetsSnapshot = this.trackedTargets
            val uuidSnapshot = attackTarget.uuid
            val trackedTick = ClientAccess.collectiveMinecraft.player?.tickCount ?: return
            targetsSnapshot[uuidSnapshot] = trackedTick
         } catch (_: Exception) {
         }
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (this.killStreak == -1) {
         this.killStreak = 0
         val selfOption = this.selfSetting
         val selfEnabled = selfOption.value
         if (!selfEnabled) {
            return
         }

         val selfKillSoundOption = this.killSoundSetting
         val selfKillSoundEnabled = selfKillSoundOption.value
         if (selfKillSoundEnabled) {
            val selfVolumeCheckOption = this.killSoundVolumeSetting
            val selfVolumeCheck = selfVolumeCheckOption.value
            if (selfVolumeCheck > 0.0f) {
               val selfSoundManagerSnapshot: SoundManager = ClientContext.soundManager
               val selfSoundOption = this.selfSoundSetting
               val selfSoundId = selfSoundOption.value
               val selfSound = selfSoundManagerSnapshot.getSound(selfSoundId)
               if (selfSound != null) {
                  val selfVolumeReadOption = this.killSoundVolumeSetting
                  val selfPlaybackVolume = selfVolumeReadOption.value
                  selfSound.play(selfPlaybackVolume)
               }
            }
         }

         val lightningLocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
         var selfPositionSnapshot: Vec3 = lightningLocalPlayer.position()

         this.spawnKillLightning(selfPositionSnapshot)
         selfPositionSnapshot = lightningLocalPlayer.position()
         this.spawnKillParticles(selfPositionSnapshot)
      }

      this.expiredTargets.clear()
      synchronized(this.trackedTargets) {
         val targetKeySet = this.trackedTargets.keys
         val targetIds: Iterator<UUID> = targetKeySet.iterator()

         while (targetIds.hasNext()) {
            val uuid = targetIds.next()

            try {
               val targetLevel: ClientLevel = ClientAccess.collectiveMinecraft.level ?: continue
               val player = targetLevel.getPlayerByUUID(uuid)
               if (player == null) {
                  this.expiredTargets.add(uuid)
                  continue
               }
               val trackedAtTick = ClientAccess.collectiveMinecraft.player?.tickCount ?: continue
               val trackedTick = this.trackedTargets[uuid]!!
               val ageTicks = trackedAtTick - trackedTick

               if (ageTicks > 200) {
                  this.expiredTargets.add(uuid)
                  continue
               }
               if (!(player.health <= 0.0f)) {
                  continue
               }
               this.expiredTargets.add(uuid)
               this.killStreak++
               val messagesSnapshot = this.killSayMessages
               val playerName = player.name.string
               messagesSnapshot.sendKillMessage(player, playerName)
               val lightningPositionSnapshot: Vec3 = player.position()

               this.spawnKillLightning(lightningPositionSnapshot)
               val particlesPositionSnapshot: Vec3 = player.position()
               this.spawnKillParticles(particlesPositionSnapshot)
               val killSoundOption = this.killSoundSetting
               val killSoundEnabled = killSoundOption.value
               if (killSoundEnabled) {
                  val killVolumeCheckOption = this.killSoundVolumeSetting
                  val killVolumeCheck = killVolumeCheckOption.value
                  if (killVolumeCheck > 0.0f) {
                     val killSoundManagerSnapshot: SoundManager = ClientContext.soundManager
                     val killSoundIdOption = this.soundSetting
                     val killSoundId = killSoundIdOption.value
                     val killSound = killSoundManagerSnapshot.getSound(
                        killSoundId
                     )
                     if (killSound != null) {
                        val killVolumeReadOption = this.killSoundVolumeSetting
                        val killPlaybackVolume = killVolumeReadOption.value
                        killSound.play(killPlaybackVolume)
                     }
                  }
               }

               val streakOption = this.killStreakSetting
               val streakEnabled = streakOption.value
               if (streakEnabled) {
                  val currentStreak = this.killStreak

                  if (currentStreak >= 2) {
                     val streakVolumeCheckOption = this.killStreakVolumeSetting
                     val streakVolumeCheck = streakVolumeCheckOption.value
                     if (streakVolumeCheck > 0.0f) {
                        val streakSoundManagerSnapshot: SoundManager = ClientContext.soundManager
                        val streakCount = this.killStreak
                        val streakSound = streakSoundManagerSnapshot.getKillstreakSound(streakCount)
                        if (streakSound != null) {
                           val streakVolumeReadOption = this.killStreakVolumeSetting
                           val streakPlaybackVolume = streakVolumeReadOption.value
                           streakSound.play(streakPlaybackVolume)
                        }
                     }
                  }
               }
            } catch (_: Exception) {
            }
         }
      }

      synchronized(this.expiredTargets) {
         try {
            val expiredIterator: ObjectListIterator<UUID> = this.expiredTargets.iterator()

            while (expiredIterator.hasNext()) {
               val expiredUuid = expiredIterator.next()
               this.trackedTargets.remove(expiredUuid)
            }
         } catch (_: Exception) {
         }
      }
   }

   @EventHandler
   private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
      this.killStreak = 0
   }

   fun trackTarget(player: Player) {
      if (player === ClientAccess.collectiveMinecraft.player!!) {
         return
      }
      val friendManagerSnapshot: SocialRegistry = ClientContext.socialRegistry
      val nameSnapshot = player.name.string

      if (friendManagerSnapshot.isFriendName(nameSnapshot)) {
         return
      }
      try {
         val targetsSnapshot = this.trackedTargets
         val uuidSnapshot = player.uuid
         val trackedTick = ClientAccess.collectiveMinecraft.player?.tickCount ?: return
         targetsSnapshot[uuidSnapshot] = trackedTick
      } catch (_: Exception) {
      }
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundPlayerCombatKillPacket) {
         this.killStreak = -1
      }
   }

   init {
      val killSoundDefault = false
      val killSoundDefinition = BooleanSetting(("KillSound"), killSoundDefault)
      killSoundDefinition.markVisibilityDependency()
      this.killSoundSetting = this.add(killSoundDefinition)
      val soundPredicate = Predicate<Any> { this.killSoundSetting.isExpanded }
      val cssSound = BuiltinSound.CSS
      val defaultSound = cssSound.identifier
      this.soundSetting = this.add(IdentifierSetting(("Sound"), defaultSound, soundPredicate))
      val selfSoundPredicate = Predicate<Any> { this.killSoundSetting.isExpanded }
      val bodySplatSound = BuiltinSound.BODYSPLAT
      val defaultSelfSound = bodySplatSound.identifier
      this.selfSoundSetting = this.add(IdentifierSetting(("SelfSound"), defaultSelfSound, selfSoundPredicate))
      val killVolumePredicate = Predicate<Any> { this.killSoundSetting.isExpanded }
      this.killSoundVolumeSetting = this.add(NumberSetting<Float>("Volume", 0.5f, 0.0f, 1.0f, killVolumePredicate))
      val killStreakDefault = false
      val killStreakDefinition = BooleanSetting(("KillStreak"), killStreakDefault)
      killStreakDefinition.markVisibilityDependency()
      this.killStreakSetting = this.add(killStreakDefinition)
      val streakVolumePredicate = Predicate<Any> { this.killStreakSetting.isExpanded }
      this.killStreakVolumeSetting = this.add(NumberSetting<Float>("Volume", 0.4f, 0.0f, 1.0f, streakVolumePredicate))
      val killSayDefault = false
      val killSayDefinition = BooleanSetting(("KillSay"), killSayDefault)
      killSayDefinition.markVisibilityDependency()
      val registeredKillSay = this.add(killSayDefinition)
      this.killSaySetting = registeredKillSay
      val delayDefinition = NumberSetting<Float>("Delay", 0.0f, 0.0f, 5.0f)
      delayDefinition.withUnit(("s"))
      this.delaySetting = this.add(delayDefinition.dependsOn(registeredKillSay))
      val pathDefinition = StringSetting(("Path"), ("killsay.txt"))
      this.pathSetting = this.add(pathDefinition.dependsOn(registeredKillSay))
      val onlyEnemiesDefault = false
      val onlyEnemiesDefinition = BooleanSetting(("OnlyEnemies"), onlyEnemiesDefault)
      this.onlyEnemiesSetting = this.add(onlyEnemiesDefinition.dependsOn(registeredKillSay))
      val randomDefault = true
      val randomDefinition = BooleanSetting(("Random"), randomDefault)
      this.randomSetting = this.add(randomDefinition.dependsOn(registeredKillSay))
      val refreshDefault = false
      val refreshDefinition = BooleanSetting(("Refresh"), refreshDefault)
      this.refreshSetting = this.add(refreshDefinition.dependsOn(registeredKillSay))
      val thunderDefault = true
      val thunderDefinition = BooleanSetting(("Thunder"), thunderDefault)
      thunderDefinition.markVisibilityDependency()
      val registeredThunder = this.add(thunderDefinition)
      this.thunderSetting = registeredThunder
      val lightningsDefinition = NumberSetting<Int>("Lightnings", 1, 1, 4)
      this.lightningsSetting = this.add(lightningsDefinition.dependsOn(registeredThunder))
      val defaultColor = Color(-8355712, true)
      val colorDefinition = ColorSetting(("Color"), defaultColor)
      this.colorSetting = this.add(colorDefinition.dependsOn(registeredThunder))
      val defaultParticles = KillEffectsParticlesChoice.OFF
      this.particlesSetting = this.add(EnumSetting<KillEffectsParticlesChoice>(("Particles"), defaultParticles))
      val particleRegistry: Registry<ParticleType<*>> = BuiltInRegistries.PARTICLE_TYPE
      this.particleTypesSetting = this.add(RegistrySetSetting<ParticleType<*>>(("ParticleTypes"), particleRegistry))
      val targetsDefault = false
      val targetsDefinition = BooleanSetting(("Targets"), targetsDefault)
      targetsDefinition.markAsGroup()
      targetsDefinition.markVisibilityDependency()
      val registeredTargets = this.add(targetsDefinition)
      val ignoreNakedsDefault = true
      val ignoreNakedsDefinition = BooleanSetting(("IgnoreNakeds"), ignoreNakedsDefault)
      this.ignoreNakedsSetting = this.add(ignoreNakedsDefinition.dependsOn(registeredTargets))
      val selfDefault = false
      val selfDefinition = BooleanSetting(("Self"), selfDefault)
      this.selfSetting = this.add(selfDefinition.dependsOn(registeredTargets))
      this.killSayMessages = KillSayMessages(this)
      this.trackedTargets = ConcurrentHashMap()
      this.expiredTargets = ObjectLists.synchronize(ObjectArrayList<UUID>())
      this.killStreak = 0
      var callbackOption: Setting<*> = this.killStreakVolumeSetting

      callbackOption.setConfigName(("KillStreakVolume"))
      this.killSaySetting.setChangeCallback {
         val messagesSnapshot = this.killSayMessages

         val force = false
         messagesSnapshot.reloadMessages(force)
      }
      this.pathSetting.setChangeCallback {
         val messagesSnapshot = this.killSayMessages

         val force = true
         messagesSnapshot.reloadMessages(force)
      }
      this.refreshSetting.setChangeCallback {
         val refreshOption = this.refreshSetting
         val refresh = refreshOption.value
         if (refresh) {
            val messagesSnapshot = this.killSayMessages

            val force = true
            messagesSnapshot.reloadMessages(force)
            this.refreshSetting.setValue(false)
         }
      }
      this.randomSetting.setChangeCallback { this.killSayMessages.resetSequence() }
      callbackOption = this.particleTypesSetting
      val customParticlesPredicate = Predicate<Any> { ignoredParticleOption ->
         this.particlesSetting.value == KillEffectsParticlesChoice.CUSTOM
      }

      callbackOption.addVisibilityPredicate(customParticlesPredicate)

      this.setDrawn(false)
   }
}
