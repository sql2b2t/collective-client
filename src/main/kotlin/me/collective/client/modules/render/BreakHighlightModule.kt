package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.utils.world.BlockBreakTracker
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.managers.RotationManager
import me.collective.client.managers.SocialRegistry
import me.collective.client.render.ColorUtils
import me.collective.client.utils.world.BlockBreakEntry
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.math.MathUtils

import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape

class BreakHighlightModule : Module("BreakHighlight", "Highlights the blocks that are being broken.", ModuleCategory.RENDER) {
   private val rangeSetting: Setting<Double>
   private val friendsSetting: Setting<Boolean>
   private val enemyColorSetting: Setting<Boolean>
   private val fillSetting: Setting<Color>
   private val outlineSetting: Setting<Color>

   private fun scaleBoxFromCenter(box: AABB, progress: Float): AABB {
      val scale: Float = Mth.clamp(progress, 0.0F, 1.0F)
      val signedWidth: Double = box.getXsize()
      val width: Double = Math.abs(signedWidth)
      val height: Double = Math.abs(box.getYsize())
      val depth: Double = Math.abs(box.getZsize())
      val center: Vec3 = box.getCenter()
      return AABB(
         center.subtract(scale * width / 2.0, scale * height / 2.0, scale * depth / 2.0),
         center.add(scale * width / 2.0, scale * height / 2.0, scale * depth / 2.0)
      )
   }

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      val trackerSnapshot: BlockBreakTracker = ClientContext.blockBreakTracker
      for (entry in trackerSnapshot.entries) {
         val positionForDistance: BlockPos = entry.getPosition()
         val distance: Double = MathUtils.distanceToBlockCenter(positionForDistance)
         val rangeSetting: Setting<Double> = this.rangeSetting
         val range: Double = rangeSetting.getValue()!!
         if (distance > range) {
            continue
         }
         val boxForVisibility = AABB(entry.getPosition())
         if (!RotationManager.isVisible(boxForVisibility)) {
            continue
         }
         val positionForMining: BlockPos = entry.getPosition()
         if (!WorldUtils.canMineBlock(positionForMining)) {
            continue
         }
         var fillColor: Color = this.fillSetting.getValue()!!
         var outlineColor: Color = this.outlineSetting.getValue()!!
         val stateLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val shapeStatePosition: BlockPos = entry.getPosition()
         val blockState: BlockState = stateLevel.getBlockState(shapeStatePosition)
         val shapeLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val shapePosition: BlockPos = entry.getPosition()
         val shape: VoxelShape = blockState.getShape(shapeLevel, shapePosition)
         val partialTickSnapshot: Float = event.partialTick
         val progress: Float = entry.getInterpolatedProgress(partialTickSnapshot)
         val breaker: Entity? = entry.getEntity()
         val localBounds: AABB = if (shape.isEmpty) {
            AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
         } else {
            shape.bounds()
         }
         var box: AABB = this.scaleBoxFromCenter(localBounds, progress)
         val offsetPosition: BlockPos = entry.getPosition()
         box = box.move(offsetPosition)
         if (breaker is Player) {
            val player: Player = breaker
            val registrySnapshot: SocialRegistry = ClientContext.socialRegistry
            val profile: GameProfile = player.getGameProfile()
            val playerNameSnapshot: String = profile.name
            val socialColor: Color? = registrySnapshot.getColor(
               playerNameSnapshot, null
            )
            val friendsSetting: Setting<Boolean> = this.friendsSetting
            val renderFriends: Boolean = friendsSetting.getValue()!!
            if (!renderFriends) {
               val friendRegistry: SocialRegistry = ClientContext.socialRegistry
               if (friendRegistry.isFriendPlayer(player)) {
                  continue
               }
            }
            if (socialColor != null) {
               val enemyRegistrySnapshot: SocialRegistry = ClientContext.socialRegistry
               val enemyProfile: GameProfile = player.getGameProfile()
               val enemyNameSnapshot: String = enemyProfile.name
               if (!enemyRegistrySnapshot.isEnemyName(enemyNameSnapshot) ||
         this.enemyColorSetting.getValue()!!
               ) {
                  val configuredFill: Color = this.fillSetting.getValue()!!
                  fillColor = ColorUtils.withAlpha(
                     socialColor, configuredFill.getAlpha()
                  )
                  val configuredOutline: Color = this.outlineSetting.getValue()!!
                  outlineColor = ColorUtils.withAlpha(
                     socialColor, configuredOutline.getAlpha()
                  )
               }
            }
         }
         val fillPoseStack: PoseStack = event.poseStack!!
         WorldBoxRenderer.fillBox(
            fillPoseStack, box, fillColor
         )
         val outlinePoseStack: PoseStack = event.poseStack!!
         WorldBoxRenderer.outlineBox(
            outlinePoseStack, box, outlineColor, 1.0F
         )
      }
   }

   init {
      this.rangeSetting = this.add(NumberSetting<Double>("Range", 10.0, 1.0, 25.0).withUnit("m"))
      this.friendsSetting = this.add(BooleanSetting("Friends", true))
      this.enemyColorSetting = this.add(BooleanSetting("EnemyColor", true))
      this.fillSetting = this.add(ColorSetting("Fill", Color(279542783, true)))
      this.outlineSetting = this.add(ColorSetting("Outline", Color(12425727, true)))
   }
}
