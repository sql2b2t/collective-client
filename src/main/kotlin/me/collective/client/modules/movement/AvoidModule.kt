package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.AbstractClientPlayerTickEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.render.FontRenderer
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.managers.TickRateController
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientChunkCache
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Direction.Axis
import net.minecraft.world.level.ClipBlockStateContext
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.HitResult.Type
import net.minecraft.world.phys.Vec3

class AvoidModule : Module("Avoid", "Prevents you from getting into dangerous situations.", ModuleCategory.MOVEMENT, "AntiVoid") {
   private val unloadSetting: Setting<Boolean>
   private val timerSetting: Setting<Float>
   private val voidSetting: Setting<Boolean>
   private val modeSetting: Setting<ModeChoice>
   private val heightSetting: Setting<Int>

   override fun getInfo(): String? {
      if (!this.voidSetting.value) {
         return null
      }
      return FontRenderer.capitalize(this.modeSetting.value)
   }

   override fun onDisable() {
      ClientContext.tickRateController.releaseMultiplier(this)
   }

   @EventHandler
   private fun onClientPlayerTick(event: AbstractClientPlayerTickEvent) {
      if (this.unloadSetting.value && !this.areNearbyChunksLoaded()) {
         val timer: TickRateController = ClientContext.tickRateController
         val multiplier: Float = this.timerSetting.value
         timer.requestMultiplier(this, multiplier)
      } else {
         val timer: TickRateController = ClientContext.tickRateController
         timer.releaseMultiplier(this)
      }
   }

   private fun areNearbyChunksLoaded(): Boolean {
      val chunks: ClientChunkCache = ClientAccess.collectiveMinecraft.level!!.getChunkSource()
      val playerChunk: ChunkPos = ClientAccess.collectiveMinecraft.player!!.chunkPosition()
      for (x in -1..1) {
         for (z in -1..1) {
            if (!chunks.hasChunk(playerChunk.x + x, playerChunk.z + z)) {
               return false
            }
         }
      }
      return true
   }

   @EventHandler
   private fun onPlayerTick(event: LocalPlayerTickEvent) {
      if (this.voidSetting.value) {
         this.preventVoidFall()
      }
   }

   private fun preventVoidFall(): Unit {
      if (ClientAccess.collectiveMinecraft.player!!.onGround()) {
         return
      }
      if (PlayerStateUtils.getTrackedFallDistance() <= 0.0f) {
         return
      }
      val rayLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val rayContext: ClipBlockStateContext = ClipBlockStateContext(
         ClientAccess.collectiveMinecraft.player!!.position(),
         Vec3(ClientAccess.collectiveMinecraft.player!!.getX(), (ClientAccess.collectiveMinecraft.level!!.getMinY() - 1).toDouble(), ClientAccess.collectiveMinecraft.player!!.getZ()),
         Predicate { state -> state.isSolid() }
      )
      if (rayLevel.isBlockInLine(rayContext).getType() != Type.MISS) {
         return
      }
      val minimumY: Int = ClientAccess.collectiveMinecraft.level!!.getMinY()
      val configuredHeight: Int = this.heightSetting.value
      val triggerY: Int = minimumY + configuredHeight
      if (triggerY < ClientAccess.collectiveMinecraft.player!!.getY()) {
         return
      }
      if (this.modeSetting.value == ModeChoice.TELEPORT) {
         ClientAccess.collectiveMinecraft.player!!.setDeltaMovement(0.0, 10.0, 0.0)
      } else {
         val stopPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val stoppedVelocity: Vec3 = stopPlayer.getDeltaMovement().with(Axis.Y, 0.0)
         stopPlayer.setDeltaMovement(stoppedVelocity)
         ClientAccess.collectiveMinecraft.player!!.setOnGround(true)
      }
      return
   }

   init {
      this.unloadSetting = this.add(BooleanSetting("Unload", false).markVisibilityDependency())
      this.timerSetting = this.add(NumberSetting<Float>("Timer", 0.25f, 0.1f, 0.5f).dependsOn(this.unloadSetting))
      this.voidSetting = this.add(BooleanSetting("Void", true).markVisibilityDependency())
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.CANCEL).dependsOn(this.voidSetting))
      this.heightSetting = this.add(NumberSetting<Int>("Height", 30, 1, 100).withUnit("m").dependsOn(this.voidSetting))
      this.setDrawn(false)
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      TELEPORT("Teleport"),
      CANCEL("Cancel");

      override fun getName(): String {
         return this.displayName
      }
   }
}
