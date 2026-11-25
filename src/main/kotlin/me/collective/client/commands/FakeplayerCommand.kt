package me.collective.client.commands

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.EventStage
import me.collective.client.modules.misc.KillEffectsModule
import me.collective.client.modules.player.FreecamModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.modules.render.LogoutSpotsModule
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.ServerAddressUtils
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.network.InteractPacketUtils

import com.mojang.authlib.GameProfile
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import java.util.ArrayList
import java.util.UUID
import java.util.concurrent.TimeUnit
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Entity.RemovalReason
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Explosion
import net.minecraft.world.phys.Vec3

class FakeplayerCommand : Command("fakeplayer") {
   private val recordedFrames: MutableList<Frame> = ArrayList()
   private val logoutSpotTimer = ElapsedTimer()
   private var fakePlayer: SimulatedPlayer? = null
   private var recording: Boolean = false
   private var playing: Boolean = false
   private var playerName: String? = null
   private var playbackIndex: Int = 0
   private var logoutSpotActive: Boolean = false

   private fun toggleLogoutSpot() {
      if (this.logoutSpotActive) {
         this.logoutSpotActive = false
         logoutSpots.removeSpot("Herobrine")
         ChatUtils.printChat(Component.literal("Herobrine logout spot has been removed."), -1)
      } else {
         val localPlayer = ClientAccess.collectiveMinecraft.player
         val localLevel = ClientAccess.collectiveMinecraft.level
         if (localPlayer == null || localLevel == null) {
            ChatUtils.printChat(Component.literal("Not in a world."), -1)
            return
         }
         val player = this.fakePlayer ?: SimulatedPlayer(
            localPlayer, localLevel, GameProfile(UUID.randomUUID(), this.playerName ?: "Herobrine"))
         val address = ServerAddressUtils.getCurrentServerAddress()
         this.logoutSpotActive = true
         logoutSpots.putSpot("Herobrine", LogoutSpotsModule.LogoutSpot.capturePlayer(player, address))
         this.logoutSpotTimer.reset()
         ChatUtils.printChat(Component.literal("Herobrine logout spot has been added. It will automatically disappear in 5 minutes."), -1)
      }
      val currentFakePlayer = this.fakePlayer
      if (currentFakePlayer != null) {
         ClientAccess.collectiveMinecraft.level?.removeEntity(currentFakePlayer.id, RemovalReason.DISCARDED)
         this.fakePlayer = null
      }
   }

   private fun killFakePlayer() {
      val currentFakePlayer = this.fakePlayer
      if (currentFakePlayer != null) {
         killEffects.trackTarget(currentFakePlayer)
         currentFakePlayer.killed = true
      }
   }

   private fun getMiningIgnorePosition(): BlockPos? {
      val capturedMiningModule = speedmine

      val miningDamage = capturedMiningModule.getMiningProgress()
      val damageSetting = speedmine.damageSetting
      val damageThreshold = damageSetting.value
      val normalizedMiningDamage = miningDamage / damageThreshold
      if (speedmine.isToggled()) {
         if (!(normalizedMiningDamage < 1.0F)) {
            return capturedMiningModule.miningPos
         }
      }

      return null
   }

   init {
      this.playerName = "Herobrine"
      this.setAliases("dummy")
      ClientAccess.eventBus.subscribe(this)
   }

   @Suppress("UNCHECKED_CAST")
   override fun exec(builder: LiteralArgumentBuilder<*>) {
      val builder = builder as LiteralArgumentBuilder<Any?>
      val nameArgument: ArgumentBuilder<Any?, *> = argument("name", FancyStringArgument.fancyString()).executes { context: com.mojang.brigadier.context.CommandContext<Any?> ->
         val requestedName = context.getArgument("name", String::class.java)
         this.toggleFakePlayer(requestedName)
         1
      }
      var commandBranch: LiteralArgumentBuilder<Any?> = builder.then(nameArgument)

      val recordAction: ArgumentBuilder<Any?, *> = literal("record").executes { context: com.mojang.brigadier.context.CommandContext<Any?> ->
         this.playing = false
         this.recording = !this.recording
         if (this.recording) {
            this.recordedFrames.clear()

            val startedMessage = Component.literal("Started recording.")

            ChatUtils.printChat(
               startedMessage, ChatUtils.createSignature(-1)
            )
         } else {
            val stoppedMessage = Component.literal("Stopped recording.")

            ChatUtils.printChat(
               stoppedMessage, ChatUtils.createSignature(-1)
            )
         }

         1
      }
      commandBranch = commandBranch.then(recordAction)

      val playAction: ArgumentBuilder<Any?, *> = literal("play").executes { context: com.mojang.brigadier.context.CommandContext<Any?> ->
         this.playbackIndex = -1
         this.playing = !this.playing
         this.recording = false
         1
      }
      commandBranch = commandBranch.then(playAction)

      val logoutAction: ArgumentBuilder<Any?, *> = literal("logout").executes { context: com.mojang.brigadier.context.CommandContext<Any?> ->
         this.toggleLogoutSpot()
         1
      }
      commandBranch = commandBranch.then(logoutAction)

      val killAction: ArgumentBuilder<Any?, *> = literal("kill").executes { context: com.mojang.brigadier.context.CommandContext<Any?> ->
         this.killFakePlayer()
         1
      }
      (commandBranch.then(killAction)).executes { context: com.mojang.brigadier.context.CommandContext<Any?> ->
         this.toggleFakePlayer("Herobrine")
         1
      }
   }

   @EventHandler(priority = 100)
   private fun interceptFakePlayerAttack(
      event: PacketEvent.Send
   ) {
      val currentFakePlayer = this.fakePlayer
      if (currentFakePlayer != null) {
         run {
            if (event.packet !is ClientboundDisconnectPacket) {
               if (event.packet !is ClientboundPlayerCombatKillPacket) {
                  return@run
               }
            }

            this.fakePlayer = null
         }

         val interactionPacket = event.packet as? ServerboundInteractPacket
         if (interactionPacket != null) {
            if (InteractPacketUtils.getAction(interactionPacket) ==
         InteractPacketUtils.Action.ATTACK
            ) {
               val target = InteractPacketUtils.getEntity(interactionPacket) ?: return
               val attackedEntityId = target.id
               if (attackedEntityId == currentFakePlayer.id) {
                  if (currentFakePlayer.hurtTime == 0) {
                     // Захватываем именно атакованную сущность: к моменту выполнения
                     // задачи поле fakePlayer может быть уже очищено.
                     val hurtPlayer = currentFakePlayer
                     ClientAccess.collectiveMinecraft.execute {
                        val hurtYaw = hurtPlayer.yRot
                        hurtPlayer.animateHurt(hurtYaw)
                        val previousHealth = hurtPlayer.health

                        val newHealth = previousHealth - 2.0F
                        hurtPlayer.health = newHealth
                     }
                  }

                  event.cancel()
               }
            }
         }
      }
   }

   @EventHandler
   private fun clearFakePlayerOnDisconnect(event: MinecraftDisconnectEvent) {
      this.fakePlayer = null
   }

   @EventHandler
   private fun updateRecordingAndPlayback(event: LocalPlayerSendPositionEvent) {
      val currentFakePlayer = this.fakePlayer
      if (currentFakePlayer != null) {
         val deathTicks = currentFakePlayer.deathTime

         if (deathTicks > 6) {
            this.fakePlayer = null
         }
      }

      if (this.logoutSpotActive) {
         val capturedExpirationTimer = this.logoutSpotTimer

         if (capturedExpirationTimer.hasElapsedAndReset(5L, TimeUnit.MINUTES)) {
            this.toggleLogoutSpot()
         }
      }

      if (event.stage == EventStage.Post &&
         this.recording
      ) {
         val recorder = ClientAccess.collectiveMinecraft.player ?: return
         this.recordedFrames.add(
            Frame(
               recordingPosition(),
               recorder.yRot,
               recorder.xRot,
               recorder.yHeadRot,
               recorder.deltaMovement,
               recorder.isShiftKeyDown,
               recorder.onGround()
            )
         )
      } else {
         val playbackFakePlayer = this.fakePlayer
         if (event.stage == EventStage.Pre &&
         playbackFakePlayer != null &&
         this.playing &&
         !this.recordedFrames.isEmpty()
         ) {
            this.playbackIndex = (this.playbackIndex + 1) % this.recordedFrames.size
            val frame = this.recordedFrames[this.playbackIndex]
            val framePitch = frame.pitch
            val frameYaw = frame.yaw
            val framePosition = frame.position
            playbackFakePlayer.moveTo(framePosition, frameYaw, framePitch)
            val frameVelocity = frame.velocity
            playbackFakePlayer.deltaMovement = frameVelocity
            val playbackYaw = frame.yaw
            playbackFakePlayer.yRot = playbackYaw
            val playbackPitch = frame.pitch
            playbackFakePlayer.xRot = playbackPitch
            val playbackHeadYaw = frame.headYaw
            playbackFakePlayer.yHeadRot = playbackHeadYaw
            val playbackSneaking = frame.sneaking
            playbackFakePlayer.isShiftKeyDown = playbackSneaking
            val playbackOnGround = frame.onGround
            playbackFakePlayer.setOnGround(playbackOnGround)
            if (playbackFakePlayer.isShiftKeyDown) {
               playbackFakePlayer.setPose(Pose.CROUCHING)
            } else {
               playbackFakePlayer.setPose(Pose.STANDING)
            }
         }
      }
   }

   private fun toggleFakePlayer(requestedName: String) {
      this.recordedFrames.clear()
      this.recording = false
      this.playing = false
      val currentFakePlayer = this.fakePlayer
      if (currentFakePlayer == null) {
         if (this.logoutSpotActive) {
            this.toggleLogoutSpot()
         }

         val localPlayer = ClientAccess.collectiveMinecraft.player
         val localLevel = ClientAccess.collectiveMinecraft.level
         if (localPlayer == null || localLevel == null) {
            ChatUtils.printChat(Component.literal("Not in a world."), -1)
            return
         }

         val spawnedFakePlayer = SimulatedPlayer(
            localPlayer,
            localLevel,
            GameProfile(UUID.randomUUID(), requestedName)
         )
         this.fakePlayer = spawnedFakePlayer
         if (freecam.isToggled()) {
            val cameraX = freecam.cameraPosition!!.x
            val cameraY = freecam.cameraPosition!!.y
            val cameraZ = freecam.cameraPosition!!.z
            val playerYaw = localPlayer.yRot
            val spawnPitch = localPlayer.xRot
            spawnedFakePlayer.moveTo(cameraX, cameraY, cameraZ, playerYaw, spawnPitch)
         }

         spawnedFakePlayer.setId(-9344)
         localLevel.addEntity(spawnedFakePlayer)
         spawnedFakePlayer.setOnGround(localPlayer.onGround())
         this.playerName = requestedName
         val spawnedMessage = requestedName + " has been spawned."
         val spawnComponent = Component.literal(spawnedMessage)

         ChatUtils.printChat(
            spawnComponent, ChatUtils.createSignature(-1)
         )
      } else {
         ClientAccess.collectiveMinecraft.level?.removeEntity(-9344, RemovalReason.DISCARDED)
         this.fakePlayer = null
         val removedName = this.playerName ?: requestedName
         val removedMessage = removedName + " has been removed."
         val removeComponent = Component.literal(removedMessage)

         ChatUtils.printChat(
            removeComponent, ChatUtils.createSignature(-1)
         )
      }
   }

   private data class Frame(
      val position: Vec3,
      val yaw: Float,
      val pitch: Float,
      val headYaw: Float,
      val velocity: Vec3,
      val sneaking: Boolean,
      val onGround: Boolean
   )

   class SimulatedPlayer(
      source: Player, level: ClientLevel, profile: GameProfile
   ) : RemotePlayer(level, profile) {
      private var useProtectedSkin: Boolean
      var killed: Boolean = false

      override fun isAlive(): Boolean {
         return !this.killed
      }

      override fun tick() {
         if (this.killed) {
            val previousDeathTime = this.deathTime++
            if (previousDeathTime > 6) {
               this.remove(RemovalReason.KILLED)
            }

            this.absorptionAmount = 0.0F
            this.setHealth(0.0F)
         } else {
            val absorptionAttribute = this.getAttribute(Attributes.MAX_ABSORPTION)!!
            absorptionAttribute.setBaseValue(16.0)
            this.tickCount++
            val currentTick = this.tickCount
            if (currentTick % 80 == 0) {
               val resistance = MobEffects.DAMAGE_RESISTANCE
               val resistanceEffect = MobEffectInstance(resistance, 900, 1)
               this.addEffect(resistanceEffect)
               this.setHealth(2e+01F)
               this.absorptionAmount = 16.0F
            }

            val capturedOffhand = this.offhandItem
            val totem = Items.TOTEM_OF_UNDYING
            if (!capturedOffhand.`is`(totem)) {
               val capturedInventory = this.inventory
               val replacementTotem = ItemStack(Items.TOTEM_OF_UNDYING, 1)
               capturedInventory.setItem(40, replacementTotem)
            }

            super.tick()
         }
      }

      override fun isPushable(): Boolean {
         return false
      }

      override fun isAttackable(): Boolean {
         return true
      }

      override fun ignoreExplosion(ignoredExplosion: Explosion): Boolean {
         return false
      }

      init {
         this.useProtectedSkin = profile.name == protectedSkinName
         this.copyPosition(source)
         this.yRotO = this.yRot
         this.xRotO = this.xRot
         this.yHeadRot = source.yHeadRot
         this.yHeadRotO = this.yHeadRot
         this.yBodyRot = source.yBodyRot
         this.yBodyRotO = this.yBodyRot
         this.setPose(source.pose)
         this.setHealth(2e+01F)
         val capturedInventory = this.inventory
         val sourceInventory = source.inventory
         capturedInventory.replaceWith(sourceInventory)
         this.isInvulnerable = false
         if (source.isFallFlying) {
            this.startFallFlying()
         }
      }

      override fun fireImmune(): Boolean {
         return true
      }

      override fun setHealth(health: Float) {
         super.setHealth(health)
         if (this.health <= 0.0F && !this.killed) {
            super.setHealth(9.0F)
            this.removeAllEffects()
            val regeneration = MobEffects.REGENERATION
            val regenerationEffect = MobEffectInstance(regeneration, 900, 1)
            this.addEffect(regenerationEffect)
            val resistance = MobEffects.DAMAGE_RESISTANCE
            val resistanceEffect = MobEffectInstance(resistance, 900, 1)
            this.addEffect(resistanceEffect)
            val particles = ClientAccess.collectiveMinecraft.particleEngine
            val totemParticles: SimpleParticleType = ParticleTypes.TOTEM_OF_UNDYING
            particles.createTrackingEmitter(this, totemParticles, 30)
            val capturedSoundLevel = ClientAccess.collectiveMinecraft.level!!
            val capturedSoundPlayer = ClientAccess.collectiveMinecraft.player!!
            val capturedSoundPosition = this.blockPosition()
            val totemSound = SoundEvents.TOTEM_USE
            val playerSoundCategory = SoundSource.PLAYERS
            capturedSoundLevel.playSound(capturedSoundPlayer, capturedSoundPosition, totemSound, playerSoundCategory, 1.0F, 1.0F)
            val capturedEventBus = ClientAccess.eventBus
            val totemEvent = PacketEvent.Receive(
               ClientboundEntityEventPacket(this, 35.toByte())
            )
            capturedEventBus.post(totemEvent)
         }
      }

      protected override fun tickDeath() {
         this.deathTime = 0
      }

      protected override fun doPush(ignoredEntity: Entity) {
      }

      override fun getSkin(): PlayerSkin {
         if (this.useProtectedSkin) {
            val originalSkin = super.getSkin()
            return PlayerSkin(
               protectedSkinTexture,
               null,
               originalSkin.capeTexture(),
               originalSkin.elytraTexture(),
               PlayerSkin.Model.WIDE,
               originalSkin.secure()
            )
         } else {
            return super.getSkin()
         }
      }

      companion object {
         private const val protectedSkinName: String = "Herobrine"
      }
   }

   companion object {
      private val protectedSkinTexture: ResourceLocation = ResourceLocation.fromNamespaceAndPath("collective", "textures/skin_protect.png")
      private val freecam: FreecamModule get() = ClientContext.getModule(FreecamModule::class.java)
      private val speedmine: SpeedMineModule get() = ClientContext.getModule(SpeedMineModule::class.java)
      private val logoutSpots: LogoutSpotsModule get() = ClientContext.getModule(LogoutSpotsModule::class.java)
      private val killEffects: KillEffectsModule get() = ClientContext.getModule(KillEffectsModule::class.java)

      private fun recordingPosition(): Vec3 {
         val player = ClientAccess.collectiveMinecraft.player!!
         return player.position()
      }
   }
}
