package me.collective.client.modules.render


import java.util.function.Predicate
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.render.LineMeshRenderer
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.utils.world.Waypoint
import me.collective.client.managers.WaypointRegistry
import me.collective.client.render.RenderUtils
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.math.MathUtils

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import java.util.ArrayList
import java.util.Comparator
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Direction.Axis
import net.minecraft.util.Mth
import net.minecraft.world.level.LevelReader
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class WaypointsModule : Module("Waypoints", "Renders certain places you mark using ;waypoints.", ModuleCategory.RENDER) {
   private val distanceSetting: Setting<Float>
   private val lineWidthSetting: Setting<Float>
   private val boxSetting: Setting<Boolean>
   private val beamSetting: Setting<Boolean>
   private val tracersSetting: Setting<Boolean>
   internal val nameSetting: Setting<Boolean>
   private val infoSetting: Setting<InfoChoice>
   private val textScaleSetting: Setting<Float>
   private val textBackgroundSetting: Setting<Boolean>
   private val eyesAlignSetting: Setting<Boolean>
   private val colorSetting: Setting<Color>
   private val textSetting: Setting<Color>
   private val beamAlphaSetting: Setting<Int>
   private val visibleWaypoints: CopyOnWriteArrayList<Waypoint>

   init {
      this.distanceSetting = this.add(NumberSetting<Float>("Distance", 101.0F, 0.1F, 101.0F).withUnit("k"))
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0F, 0.1F, 3.0F))
      this.boxSetting = this.add(BooleanSetting("Box", true))
      this.beamSetting = this.add(BooleanSetting("Beam", false))
      this.tracersSetting = this.add(BooleanSetting("Tracers", false))
      this.nameSetting = this.add(BooleanSetting("Name", true))
      val infoVisibility: Predicate<Any> = Predicate { ignored -> this.nameSetting.getValue() }
      this.infoSetting = this.add(EnumSetting<InfoChoice>("Info", InfoChoice.NONE, infoVisibility))
      this.textScaleSetting = this.add(NumberSetting<Float>("TextScale", 1.0F, 0.5F, 3.0F, Predicate { ignored -> this.nameSetting.getValue() }))
      this.textBackgroundSetting = this.add(BooleanSetting("TextBackground", true, Predicate { ignored -> this.nameSetting.getValue() }))
      this.eyesAlignSetting = this.add(BooleanSetting("EyesAlign", false).dependsOn(this.nameSetting))
      this.colorSetting = this.add(ColorSetting("Color", Color(-457336321, true)))
      this.textSetting = this.add(ColorSetting("Text", Color(-1, true)))
      this.beamAlphaSetting = this.add(NumberSetting<Int>("BeamAlpha", 40, 0, 255))
      this.visibleWaypoints = CopyOnWriteArrayList()
      val distanceSettingForLabel: Setting<Float> = this.distanceSetting

      val unlimitedBoundary: SettingLabelCondition = SettingLabelCondition.MAX
      distanceSettingForLabel.setBoundaryLabel("Unlimited", unlimitedBoundary)

      this.setDrawn(false)
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (player.tickCount % 2 == 0) {
         return
      }

      val registry: WaypointRegistry = ClientContext.waypointRegistry
      val registeredWaypoints: List<Waypoint> = registry.getRegistry() as List<Waypoint>
      val invalidWaypoints: MutableList<Waypoint> = ArrayList()
      for (waypoint in registeredWaypoints) {
         if (waypoint.server == null ||
         waypoint.dimension == null || waypoint.getName() == null) {
            invalidWaypoints.add(waypoint)
         }
      }
      for (invalidWaypoint in invalidWaypoints) {
         registry.unregisterWaypoint(invalidWaypoint)
      }

      this.visibleWaypoints.clear()
      val minecraft: Minecraft = ClientAccess.collectiveMinecraft
      val currentServer: String? = if (minecraft.getCurrentServer() == null)
         null
      else
         minecraft.getCurrentServer()!!.ip
      for (waypoint in registeredWaypoints) {
         if (!waypoint.toggled) {
            continue
         }

         val waypointServer: String = waypoint.server
         val serverMatches = if (currentServer == null)
            waypointServer.equals("singleplayer", ignoreCase = true)
         else
            waypointServer.equals(currentServer, ignoreCase = true)
         if (serverMatches) {
            this.visibleWaypoints.add(waypoint)
         }
      }

      this.visibleWaypoints.sortWith(Comparator.comparingDouble(
         { waypoint: Waypoint -> player.distanceToSqr(waypoint.getPosition()) }
      ))
   }

   private fun getRenderPosition(cameraPosition: Vec3, waypointPosition: Vec3): Vec3 {
      val dx = waypointPosition.x - cameraPosition.x
      val dz = waypointPosition.z - cameraPosition.z
      val horizontalDistance = Math.hypot(dx, dz)
      if (horizontalDistance < FAR_WAYPOINT_DISTANCE) {
         return waypointPosition
      }

      val distanceScale = FAR_WAYPOINT_RADIUS / horizontalDistance
      val x = cameraPosition.x + dx * distanceScale
      val y = Mth.lerp(distanceScale, cameraPosition.y, waypointPosition.y)
      val z = cameraPosition.z + dz * distanceScale
      return Vec3(x, y, z)
   }

   private fun renderWaypoint(poseStack: PoseStack, cameraPosition: Vec3, waypoint: Waypoint) {
      val waypointPosition = getPositionInCurrentDimension(waypoint) ?: return

      val maximumDistance: Float = this.distanceSetting.getValue()!!
      if (!this.distanceSetting.isAtMaximum() &&
         waypointPosition.distanceTo(cameraPosition) > maximumDistance * DISTANCE_UNITS_PER_KILOMETER) {
         return
      }

      val renderPosition = this.getRenderPosition(cameraPosition, waypointPosition)
      if (this.beamSetting.getValue()!!) {
         var beamBounds = around(renderPosition, WAYPOINT_HALF_SIZE)
         val worldTop: Float = WorldUtils.getTopY()
         val world: LevelReader = ClientAccess.collectiveMinecraft.level!!
         beamBounds = beamBounds.setMaxY(worldTop.toDouble()).setMinY(world.getMinY().toDouble())
         val beamColor: Color = this.colorSetting.getValue()!!
         val beamAlpha: Int = this.beamAlphaSetting.getValue()!!
         WorldBoxRenderer.fillBox(poseStack, beamBounds, ColorUtils.withAlpha(beamColor, beamAlpha))
      }

      if (this.boxSetting.getValue()!!) {
         val box = around(waypointPosition, WAYPOINT_HALF_SIZE)
         val color: Color = this.colorSetting.getValue()!!
         val lineWidth: Float = this.lineWidthSetting.getValue()!!
         WorldBoxRenderer.outlineBox(poseStack, box, color, lineWidth)
      }

      if (this.tracersSetting.getValue()!!) {
         LineMeshRenderer.addTracer(renderPosition, this.colorSetting.getValue()!!)
      }

      if (this.nameSetting.getValue()!!) {
         this.renderWaypointName(poseStack, cameraPosition, renderPosition, waypointPosition, waypoint)
      }
   }

   private fun renderWaypointName(
      poseStack: PoseStack, cameraPosition: Vec3, renderPosition: Vec3,
      waypointPosition: Vec3, waypoint: Waypoint
   ) {
      var renderPosition = renderPosition
      if (this.eyesAlignSetting.getValue()!!) {
         renderPosition = renderPosition.with(Axis.Y, cameraPosition.y)
      }

      val label: String = waypoint.getName() + " " +
         (this.infoSetting.getValue()!!).formatInfo(waypointPosition)
      val textScale: Double = MathUtils.getDistanceScale(
         cameraPosition, renderPosition, this.textScaleSetting.getValue()!!.toDouble()
      )
      val textWidth: Float = FontRenderer.INSTANCE.getWidth(label)

      if (this.textBackgroundSetting.getValue()!!) {
         val textHeight: Float = FontRenderer.INSTANCE.getHeight() + 1.0F
         RenderUtils.INSTANCE.drawWorldRect(
            poseStack, renderPosition, -0.03F, WAYPOINT_HALF_SIZE.toFloat(),
            -textWidth, textHeight, textScale, Color(0, 0, 0, TEXT_BACKGROUND_ALPHA)
         )
      }

      RenderUtils.INSTANCE.drawWorldText(
         poseStack, label, renderPosition, 0.0F, 0.23F, -textWidth / 2.0F,
         0.0F, textScale, this.textSetting.getValue()!!, true
      )
   }

   private enum class InfoChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      COORDS("Coords") {
         override fun formatInfo(position: Vec3): String {
            val locale: Locale = Locale.US
            return String.format(locale, "X: %.1f, Y: %.1f, Z: %.1f", position.x(), position.y(), position.z())
         }
      },
      DISTANCE("Distance") {
         override fun formatInfo(position: Vec3): String {
            return String.format("%.1fm", position.distanceTo(ClientAccess.collectiveMinecraft.player!!.position()))
         }
      };

      override fun getName(): String {
         return this.displayName
      }

      open fun formatInfo(position: Vec3): String {
         return ""
      }

   }

   companion object {
      private const val DISTANCE_UNITS_PER_KILOMETER = 1_000
      private const val FAR_WAYPOINT_DISTANCE = 120.0
      private const val FAR_WAYPOINT_RADIUS = 100.0
      private const val WAYPOINT_HALF_SIZE = 0.25
      private const val TEXT_BACKGROUND_ALPHA = 100

      @JvmStatic
      fun getPositionInCurrentDimension(waypoint: Waypoint): Vec3? {
         val position: Vec3 = waypoint.getPosition()
         val currentDimension: String = WorldUtils.getDimension().getDisplayName().lowercase()
         val waypointDimension: String = waypoint.dimension

         if (waypointDimension == "overworld" && currentDimension == "nether") {
            return position.multiply(0.125, 1.0, 0.125)
         }
         if (waypointDimension == "nether" && currentDimension == "overworld") {
            return position.multiply(8.0, 1.0, 8.0)
         }
         return if (waypointDimension == currentDimension) position else null
      }

      private fun around(position: Vec3, halfSize: Double): AABB {
         return AABB(
            position.add(-halfSize, -halfSize, -halfSize),
            position.add(halfSize, halfSize, halfSize)
         )
      }
   }
}
