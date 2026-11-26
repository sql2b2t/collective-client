package me.collective.client.modules.render

import net.minecraft.world.entity.EntityTypes
import com.mojang.blaze3d.PrimitiveTopology
import com.google.gson.JsonElement
import com.google.gson.JsonObject

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.managers.RotationManager
import me.collective.client.managers.ShaderRegistry
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.ColorsModule
import me.collective.client.gui.components.ColorSubmitNodeStorage
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.options.DimensionType
import me.collective.client.settings.options.ESPModeChoice
import me.collective.client.settings.options.ListFilterMode
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.math.MathUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.render.ClientRenderTypes
import me.collective.client.render.ColorOutlineBufferSource
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.RenderUtils
import me.collective.client.render.VertexGeometry
import me.collective.client.render.WorldBoxRenderer

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.render.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import java.awt.Color
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.item.Item
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.lighting.LayerLightEventListener
import net.minecraft.world.level.lighting.LevelLightEngine
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class ESPModule : Module("ESP", "Highlights entities through walls.", ModuleCategory.RENDER) {

   protected val entitiesSetting: Setting<Boolean>
   protected val entityDrawSetting: Setting<Boolean>
   @JvmField
   val entityModeSetting: Setting<EntityMode>
   @JvmField
   val entityShadowSetting: Setting<ShadowMode>
   @JvmField
   val entityLengthSetting: Setting<Float>
   @JvmField
   val entityOutlineSetting: Setting<Float>
   @JvmField
   val entityFillSetting: Setting<Float>
   @JvmField
   val entityTintSetting: Setting<Float>
   @JvmField
   val socialSetting: Setting<Boolean>
   @JvmField
   val infoSetting: Setting<Boolean>
   @JvmField
   val nameSetting: Setting<Boolean>
   @JvmField
   val heldItemSetting: Setting<Boolean>
   @JvmField
   val distanceSetting: Setting<Boolean>
   @JvmField
   val effectsSetting: Setting<Boolean>
   @JvmField
   val healthBarSetting: Setting<Boolean>
   @JvmField
   val healthTextSetting: Setting<Boolean>
   @JvmField
   val healthColorSetting: Setting<Boolean>
   private val entity2DRenderer: ESP2DRenderer
   private val renderEntities: MutableList<Entity> = ArrayList()
   val hostilesSetting: Setting<Boolean>
   val hostileColorSetting: Setting<Color>
   val playersSetting: Setting<Boolean>
   val playerColorSetting: Setting<Color>
   val animalsSetting: Setting<Boolean>
   val animalColorSetting: Setting<Color>
   val pearlsSetting: Setting<Boolean>
   val pearlFillSetting: Setting<Color>
   val pearlOutlineSetting: Setting<Color>
   protected val itemsSetting: Setting<Boolean>
   @JvmField
   val itemDrawSetting: Setting<Boolean>
   private val itemListSetting: Setting<Set<Item>>
   protected val selectionSetting: Setting<ListFilterMode>
   val modeSetting: Setting<ESPModeChoice>
   val rangeSetting: Setting<Int>
   protected val groupSetting: Setting<Boolean>
   protected val itemsTextSetting: Setting<Color>
   protected val backgroundSetting: Setting<Color>
   protected val scaleSetting: Setting<Float>
   val itemColorSetting: Setting<Color>
   val experienceSetting: Setting<Boolean>
   val experienceColorSetting: Setting<Color>
   protected val blocksSetting: Setting<Boolean>
   protected val blockDrawSetting: Setting<Boolean>
   @JvmField
   val blockFillOpacitySetting: Setting<Float>
   val chestsSetting: Setting<Boolean>
   val barrelsSetting: Setting<Boolean>
   val enderChestsSetting: Setting<Boolean>
   val shulkersSetting: Setting<Boolean>
   val bedsSetting: Setting<Boolean>
   val signsSetting: Setting<Boolean>
   val dispensersSetting: Setting<Boolean>
   val hoppersSetting: Setting<Boolean>
   val furnacesSetting: Setting<Boolean>
   val potsSetting: Setting<Boolean>
   protected val lootedSetting: Setting<Boolean>
   protected var othersSetting: Setting<Boolean>
   protected val mobOwnerSetting: Setting<Boolean>
   protected val lightSetting: Setting<Boolean>
   protected val chorusSetting: Setting<Boolean>
   protected val chorusTextSetting: Setting<Color>
   private val lineWidthSetting: Setting<Float>
   private var renderingBlockEntities: Boolean = false
   private val chorusPositions: MutableMap<Vec3, Long>
   private val visibleBlockEntities: ObjectArrayList<BlockEntityRenderEntry>
   private val blockRefreshTimer: ElapsedTimer
   @JvmField
   val renderHelper: ESPRenderHelper
   private val lootedStorage: LootedStorageTracker
   private var currentlyRenderingBlock: Block? = null

   init {
      this.entitiesSetting = this.add(BooleanSetting("Entities", true).markAsGroup().markVisibilityDependency())
      this.entityDrawSetting = this.add(BooleanSetting("Draw", true).dependsOn(this.entitiesSetting))
      this.entityModeSetting = this.add(EnumSetting<EntityMode>("Mode", EntityMode.BOX).dependsOn(this.entitiesSetting))
      this.entityModeSetting.setConfigName("EntityMode")
      this.entityShadowSetting = this.add(EnumSetting<ShadowMode>("Shadow", ShadowMode.BLACK) { ignored -> this.isEntity2D() }.dependsOn(this.entitiesSetting))
      this.entityLengthSetting = this.add(NumberSetting<Float>("Length", 1.0f, 0.1f, 1.0f) { ignored -> this.isEntity2D() }.dependsOn(this.entitiesSetting))
      this.entityOutlineSetting = this.add(NumberSetting<Float>("Outline", 1.0f, 0.0f, 1.0f).dependsOn(this.entitiesSetting))
      this.entityFillSetting = this.add(NumberSetting<Float>("Fill", 0.3f, 0.0f, 0.5f).dependsOn(this.entitiesSetting))
      this.entityTintSetting = this.add(NumberSetting<Float>("Tint", 0.3f, 0.0f, 0.5f) { ignored -> this.isEntity2D() }.dependsOn(this.entitiesSetting))
      this.infoSetting = this.add(BooleanSetting("Info", false) { ignored -> this.isEntity2D() }.markAsGroup().markVisibilityDependency().dependsOn(this.entitiesSetting))
      this.nameSetting = this.add(BooleanSetting("Name", true).dependsOn(this.infoSetting))
      this.heldItemSetting = this.add(BooleanSetting("HeldItem", false).dependsOn(this.infoSetting))
      this.distanceSetting = this.add(BooleanSetting("Distance", false).dependsOn(this.infoSetting))
      this.effectsSetting = this.add(BooleanSetting("Effects", true).dependsOn(this.infoSetting))
      this.healthBarSetting = this.add(BooleanSetting("HealthBar", false).markVisibilityDependency().dependsOn(this.infoSetting))
      this.healthTextSetting = this.add(BooleanSetting("Text", false).dependsOn(this.infoSetting, this.healthBarSetting))
      this.healthColorSetting = this.add(BooleanSetting("Color", false).dependsOn(this.infoSetting, this.healthBarSetting))
      this.entity2DRenderer = ESP2DRenderer(this)
      this.hostilesSetting = this.add(BooleanSetting("Hostiles", false).markVisibilityDependency().dependsOn(this.entitiesSetting))
      this.hostileColorSetting = this.add(ColorSetting("Color", Color(0xFFBD99FF.toInt(), true)).dependsOn(this.hostilesSetting, this.entitiesSetting))
      this.playersSetting = this.add(BooleanSetting("Players", false).markVisibilityDependency().dependsOn(this.entitiesSetting))
      this.socialSetting = this.add(BooleanSetting("Social", true).dependsOn(this.entitiesSetting, this.playersSetting))
      this.playerColorSetting = this.add(ColorSetting("Color", Color(0xFFBD99FF.toInt(), true)).dependsOn(this.playersSetting, this.entitiesSetting))
      this.animalsSetting = this.add(BooleanSetting("Animals", false).markVisibilityDependency().dependsOn(this.entitiesSetting))
      this.animalColorSetting = this.add(ColorSetting("Color", Color(0xFFBD99FF.toInt(), true)).dependsOn(this.animalsSetting, this.entitiesSetting))
      this.pearlsSetting = this.add(BooleanSetting("Pearls", false) { ignored -> this.entityModeSetting.getValue() == EntityMode.BOX }.markVisibilityDependency().dependsOn(this.entitiesSetting))
      this.pearlFillSetting = this.add(ColorSetting("Fill", Color(364747263, true)).dependsOn(this.pearlsSetting, this.entitiesSetting))
      this.pearlOutlineSetting = this.add(ColorSetting("Outline", Color(-910321153, true)).dependsOn(this.pearlsSetting, this.entitiesSetting))
      this.itemsSetting = this.add(BooleanSetting("Items", false).markAsGroup().markVisibilityDependency().dependsOn(this.entitiesSetting))
      this.itemDrawSetting = this.add(BooleanSetting("Draw", false).dependsOn(this.entitiesSetting, this.itemsSetting))
      this.itemDrawSetting.setConfigName("DrawItems")
      this.itemListSetting = this.add(RegistrySetSetting<Item>("List", BuiltInRegistries.ITEM).dependsOn(this.entitiesSetting, this.itemsSetting))
      this.selectionSetting = this.add(EnumSetting<ListFilterMode>("Selection", ListFilterMode.ANY).dependsOn(this.entitiesSetting, this.itemsSetting))
      this.modeSetting = this.add(EnumSetting<ESPModeChoice>("Mode", ESPModeChoice.PLAIN).dependsOn(this.entitiesSetting, this.itemsSetting))
      this.rangeSetting = this.add(NumberSetting<Int>("Range", 256, 1, 256).withUnit("m").dependsOn(this.entitiesSetting, this.itemsSetting))
      this.groupSetting = this.add(BooleanSetting("Group", false) { ignored -> this.showItemGroups(ignored) }.dependsOn(this.entitiesSetting, this.itemsSetting))
      this.itemsTextSetting = this.add(ColorSetting("Text", Color(-1, true)) { ignored -> this.showItemText(ignored) })
      this.backgroundSetting = this.add(ColorSetting("Background", Color(657930, true)) { ignored -> this.showTextOnlyOptions(ignored) })
      this.scaleSetting = this.add(NumberSetting<Float>("Scale", 1.0f, 0.5f, 2.0f) { ignored -> this.showTextOnlyOptions(ignored) })
      this.itemColorSetting = this.add(ColorSetting("Color", Color(0x15BD99FF, true)) { ignored -> this.showItemBoxes(ignored) })
      this.experienceSetting = this.add(BooleanSetting("Exp", false).markVisibilityDependency().dependsOn(this.entitiesSetting))
      this.experienceColorSetting = this.add(ColorSetting("Color", Color(0xFFBD99FF.toInt(), true)).dependsOn(this.experienceSetting, this.entitiesSetting))
      this.blocksSetting = this.add(BooleanSetting("Blocks", true).markAsGroup().markVisibilityDependency())
      this.blockDrawSetting = this.add(BooleanSetting("Draw", true).dependsOn(this.blocksSetting))
      this.blockFillOpacitySetting = this.add(NumberSetting<Float>("Fill", 0.0f, 0.0f, 1.0f).dependsOn(this.blocksSetting))
      this.chestsSetting = this.add(BooleanSetting("Chests", true).dependsOn(this.blocksSetting))
      this.barrelsSetting = this.add(BooleanSetting("Barrels", true).dependsOn(this.blocksSetting))
      this.enderChestsSetting = this.add(BooleanSetting("EChests", true).dependsOn(this.blocksSetting))
      this.shulkersSetting = this.add(BooleanSetting("Shulkers", true).dependsOn(this.blocksSetting))
      this.bedsSetting = this.add(BooleanSetting("Beds", false).dependsOn(this.blocksSetting))
      this.signsSetting = this.add(BooleanSetting("Signs", false).dependsOn(this.blocksSetting))
      this.dispensersSetting = this.add(BooleanSetting("Dispensers", false).dependsOn(this.blocksSetting))
      this.hoppersSetting = this.add(BooleanSetting("Hoppers", false).dependsOn(this.blocksSetting))
      this.furnacesSetting = this.add(BooleanSetting("Furnaces", false).dependsOn(this.blocksSetting))
      this.potsSetting = this.add(BooleanSetting("Pots", false).dependsOn(this.blocksSetting))
      this.lootedSetting = this.add(BooleanSetting("Looted", false).dependsOn(this.blocksSetting))
      this.othersSetting = this.add(BooleanSetting("Others", false).markAsGroup().markVisibilityDependency())
      this.mobOwnerSetting = this.add(BooleanSetting("MobOwner", false).dependsOn(this.othersSetting))
      this.lightSetting = this.add(BooleanSetting("Light", false).dependsOn(this.othersSetting))
      this.chorusSetting = this.add(BooleanSetting("Chorus", false).dependsOn(this.othersSetting))
      this.chorusTextSetting = this.add(ColorSetting("ChorusText", Color(-1, true)).dependsOn(this.othersSetting, this.chorusSetting))
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0f, 0.0f, 4.0f) { ignored -> this.entityModeSetting.getValue() == EntityMode.BOX }.dependsOn(this.entitiesSetting))
      this.chorusPositions = Collections.synchronizedMap(HashMap<Vec3, Long>())
      this.visibleBlockEntities = ObjectArrayList(64)
      this.blockRefreshTimer = ElapsedTimer()
      this.renderHelper = ESPRenderHelper(this)
      this.lootedStorage = LootedStorageTracker()
      this.pearlFillSetting.setConfigName("PearlsFill")
      this.pearlOutlineSetting.setConfigName("PearlsOutline")
      this.playerColorSetting.setConfigName("PlayersColor")
      this.hostileColorSetting.setConfigName("HostilesColor")
      this.animalColorSetting.setConfigName("AnimalsColor")
      this.experienceColorSetting.setConfigName("ExpColor")
      this.itemColorSetting.setConfigName("ItemsColor")
      this.healthTextSetting.setConfigName("HealthText")
      this.healthColorSetting.setConfigName("HealthColor")
      this.itemsTextSetting.setConfigName("ItemsText")
      this.modeSetting.setConfigName("ItemsMode")
      this.rangeSetting.setConfigName("ItemRange")
      this.blockFillOpacitySetting.setConfigName("StorageFill")
      this.blockFillOpacitySetting.setBoundaryLabel("None", SettingLabelCondition.MIN)
      this.entityDrawSetting.setConfigName("DrawEntities")
      this.blockDrawSetting.setConfigName("DrawBlocks")
      this.setDrawn(false)
   }

   private fun refreshBlockEntityCache() {
      val cameraPosition: Vec3 = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
      this.visibleBlockEntities.clear()
      val blockEntities: Iterator<BlockEntity> = ClientContext.worldStateManager.getBlockEntities().iterator()
      while (blockEntities.hasNext()) {
         val blockEntity: BlockEntity = blockEntities.next()
         if (this.renderHelper.shouldRenderBlockEntity(blockEntity)) {
            val blockBounds = AABB(blockEntity.getBlockPos())
            if (RotationManager.isVisible(blockBounds)) {
               val cacheSnapshot: ObjectArrayList<BlockEntityRenderEntry> = this.visibleBlockEntities
               val sortedEntry = BlockEntityRenderEntry(
                  blockEntity, -cameraPosition.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(blockEntity.getBlockPos()))
               )
               cacheSnapshot.add(sortedEntry)
            }
         }
      }
      this.visibleBlockEntities.sortWith(Comparator.comparingDouble { entry: BlockEntityRenderEntry -> entry.negativeDistanceSquared })
   }

   fun isRenderingBlock(block: Block): Boolean {
      return this.isRenderingBlockEntities() && block == this.currentlyRenderingBlock
   }

   fun isRenderingBlockEntities(): Boolean {
      return this.renderingBlockEntities
   }

   private fun renderItemLabel(event: WorldRenderEvent, itemEntity: ItemEntity) {
      var itemText: String = itemEntity.getItem().getHoverName().getString()
      if (itemEntity.getItem().getCount() > 1) {
         itemText = itemText + " x" + itemEntity.getItem().getCount()
      }
      val labelHelper: ESPRenderHelper = this.renderHelper
      val partialTick: Float = event.partialTick
      var labelPosition: Vec3 = itemEntity.getPosition(partialTick)
      labelPosition = labelPosition.add(0.0, 0.6000000238418579, 0.0)
      val labelScale: Float = this.scaleSetting.getValue()!!
      val configuredItemsText: Color = this.itemsTextSetting.getValue()!!
      val configuredBackground: Color = this.backgroundSetting.getValue()!!
      labelHelper.drawLabel(event, itemText, labelPosition, labelScale, configuredItemsText, configuredBackground)
   }

   private fun renderGroupBackground(
      poses: PoseStack, group: ESPItemGroup, scale: Double, height: Float
   ) {
      val backgroundRenderer: RenderUtils = RenderUtils.INSTANCE
      val backgroundPosition: Vec3 = group.getRenderBounds().getCenter().add(0.0, 0.5, 0.0)
      val maximumTextWidth: Float = group.getMaximumTextWidth()
      val backgroundWidth: Float = maximumTextWidth + 1.0f
      val backgroundOption: Setting<Color> = this.backgroundSetting
      backgroundRenderer.drawWorldRect(
         poses, backgroundPosition, 0.0f, 0.0f, backgroundWidth, height, scale, backgroundOption.getValue()
      )
   }

   @EventHandler
   private fun expireChorusPositions(event: LocalPlayerTickEvent) {
      synchronized(this.chorusPositions) {
         this.chorusPositions.entries.removeIf { chorusEntry -> System.currentTimeMillis() - chorusEntry.value!! > 3500L }
      }
   }

   private fun renderGroupedItemLabels(event: WorldRenderEvent, groups: List<ESPItemGroup>) {
      for (group in groups) {
         val configuredGroup: Boolean = this.groupSetting.getValue()!!
         if (!configuredGroup) {
            break
         }
         val groupBounds: AABB = group.getRenderBounds()
         var positionSnapshot: Vec3 = groupBounds.getCenter()
         val labelPosition: Vec3 = positionSnapshot.add(0.0, 0.5, 0.0)
         val gameRenderer: GameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
         positionSnapshot = gameRenderer.mainCamera().position()
         val scaleAsDouble: Double = this.scaleSetting.getValue()!!.toDouble()
         val cameraForScale: Vec3 = positionSnapshot
         val labelScale: Double = MathUtils.getDistanceScale(cameraForScale, labelPosition, scaleAsDouble)
         val lineHeight: Float = FontRenderer.INSTANCE
            .getHeight().toFloat()
         var lineY: Float = 0.0f

         if (group.getItemCounts().size == 1) {
            val singleRowPoses: PoseStack = event.poseStack!!
            val singleRowHeight: Float = group.getItemCounts().size *
         lineHeight
            this.renderGroupBackground(singleRowPoses, group, labelScale, singleRowHeight)
         } else {
            val firstRowPoses: PoseStack = event.poseStack!!
            this.renderGroupBackground(firstRowPoses, group, labelScale, lineHeight)
            val remainingRowsPoses: PoseStack = event.poseStack!!
            val remainingRowsHeight: Float = (
               group.getItemCounts().size - 1
               ) *
         -lineHeight
            val remainingRowHeight: Float = remainingRowsHeight - 2.0f
            this.renderGroupBackground(remainingRowsPoses, group, labelScale, remainingRowHeight)
         }
         for (itemEntry in group.getItemCounts().entries) {
            val itemText: String = ESPItemGroup.formatItemCount(itemEntry.key, itemEntry.value!!)
            val negativeTextWidth: Float = -FontRenderer.INSTANCE
               .getWidth(itemText)
            val textX: Float = negativeTextWidth / 2.0f
            val textRenderer: RenderUtils = RenderUtils.INSTANCE
            val textPoses: PoseStack = event.poseStack!!
            val itemsTextOption: Setting<Color> = this.itemsTextSetting
            textRenderer.drawWorldText(
               textPoses, itemText, labelPosition, 0.0f, 0.0f, textX, lineY, labelScale, itemsTextOption.getValue(), true
            )
            lineY -= lineHeight
         }
      }
   }

   @EventHandler(priority = -200)
   private fun onUseItemOn(event: MultiPlayerGameModeUseItemOnEvent) {
      if (this.lootedSetting.getValue()!!) {
         this.lootedStorage.onUseItemOn(event)
      }
   }

   @EventHandler
   private fun onLightRender(event: WorldRenderEvent.BeforeBufferSource) {
      if (this.lightSetting.getValue()!!) {
         this.renderLightLevels(event)
      }
   }

   private fun renderLightLevels(event: WorldRenderEvent) {
      var maximumSpawnLight: Int = 0
      val spawnPolicy: DimensionType = WorldUtils.getDimension()
      if (spawnPolicy.isNether()) {
         maximumSpawnLight = 11
      }
      val cameraPosition: Vec3 = ClientAccess.collectiveMinecraft.getEntityRenderDispatcher().camera!!.position()
      val poses: PoseStack = event.poseStack!!
      val tesselator: Tesselator = Tesselator.getInstance()
      val lineFormat = DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH
      val lineMode: PrimitiveTopology = PrimitiveTopology.LINES
      val lineBuffer: BufferBuilder = tesselator.begin(lineMode, lineFormat)
      val levelLightEngine: LevelLightEngine = ClientAccess.collectiveMinecraft.level!!.getLightEngine()
      val blockLightLayer: LightLayer = LightLayer.BLOCK
      val blockLight: LayerLightEventListener = levelLightEngine.getLayerListener(blockLightLayer)
      val eyePosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.getEyePosition()
      val positions: Iterator<BlockPos> = WorldUtils.collectSphereBlocks(eyePosition, 12.0f, true).iterator()
      while (positions.hasNext()) {
         val position: BlockPos = positions.next()
         val state: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(position)
         val aboveBlock: BlockPos = position.above()
         if (WorldUtils.isAir(aboveBlock)) {
            if (blockLight.getLightValue(position.above()) <= maximumSpawnLight) {
               val biome: Holder<Biome> = ClientAccess.collectiveMinecraft.level!!.getBiome(position)
               val mushroomFields: ResourceKey<Biome> = Biomes.MUSHROOM_FIELDS
               val mushroomBiome: Boolean = biome.`is`(mushroomFields)
               val spawnLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
               val spawnType: EntityType<*> = EntityTypes.ZOMBIE
               if (state.isValidSpawn(spawnLevel, position, spawnType) &&
         !mushroomBiome
               ) {
                  val distanceCameraPosition: Vec3 = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
                  val blockCenter: Vec3 = Vec3.atCenterOf(position)
                  val cameraDistance: Double = distanceCameraPosition.distanceTo(blockCenter)
                  val distancePastFadeStart: Double = cameraDistance - 8.0
                  val normalizedFadeDistance: Double = distancePastFadeStart / 2.0

                  val distanceAlpha: Float = 1.0f - Mth.clamp(normalizedFadeDistance, 0.0, 1.0).toFloat()
                  var markerRgba: Int = Color.red.hashCode()
                  if (cameraDistance >= 8.0) {
                     markerRgba = ColorUtils.toArgb(
                        Color.red, distanceAlpha
                     )
                  }
                  poses.pushPose()
                  val relativeX: Double = position.getX() - cameraPosition.x
                  val relativeY0: Double = position.getY() - cameraPosition.y
                  val relativeY: Double = relativeY0 + 1.02
                  val translateZ: Double = position.getZ() - cameraPosition.z
                  poses.translate(relativeX, relativeY, translateZ)
                  val markerPose: PoseStack.Pose = poses.last()
                  VertexGeometry.appendLine(lineBuffer, markerPose, 0.25f, 0.0f, 0.25f, 0.75f, 0.0f, 0.75f, 1.0f, markerRgba)
                  VertexGeometry.appendLine(lineBuffer, markerPose, 0.75f, 0.0f, 0.25f, 0.25f, 0.0f, 0.75f, 1.0f, markerRgba)
                  poses.popPose()
               }
            }
         }
      }
      val lineMesh: MeshData? = lineBuffer.build()
      if (lineMesh != null) {
         val meshRenderType: RenderType = ClientRenderTypes.worldLinesDepth()
         me.collective.client.render.MeshBuffer.drawMesh(meshRenderType, lineMesh)
      }
   }

   private fun renderEntityBoxes(event: WorldRenderEvent, entities: Iterable<Entity>) {
      val partialTick: Float = event.partialTick
      for (entity in entities) {
         val helperSnapshot: ESPRenderHelper = this.renderHelper
         if (helperSnapshot.shouldRenderEntity(entity)) {
            val fillPoses: PoseStack = event.poseStack!!
            val fillInterpolatedBounds: AABB = BoundingBoxUtils.getRenderBounds(entity, partialTick)
            val fillColorHelper: ESPRenderHelper = this.renderHelper
            WorldBoxRenderer.fillBox(fillPoses, fillInterpolatedBounds, fillColorHelper.getEntityColor(entity, true))
            val outlinePoses: PoseStack = event.poseStack!!
            val outlineInterpolatedBounds: AABB = BoundingBoxUtils.getRenderBounds(entity, partialTick)
            val outlineColorHelper: ESPRenderHelper = this.renderHelper
            val outlineColor: Color = outlineColorHelper.getEntityColor(entity, false)
            val configuredLineWidth: Float = this.lineWidthSetting.getValue()!!
            WorldBoxRenderer.outlineBox(outlinePoses, outlineInterpolatedBounds, outlineColor, configuredLineWidth)
         }
      }
   }

   private fun renderItemLabels(event: WorldRenderEvent, itemEntity: ItemEntity, itemGroups: MutableList<ESPItemGroup>) {
      val itemSelection: ListFilterMode = this.selectionSetting.getValue()!!
      val selectedItem: Item = itemEntity.getItem().getItem()
      val itemListOption: Setting<Set<Item>> = this.itemListSetting
      if (!ListFilterMode.matches(itemSelection, selectedItem, itemListOption.getValue()!!)) {
         return
      }
      val cameraPosition: Vec3 = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
      val itemDistance: Double = itemEntity.position().distanceTo(cameraPosition)
      val configuredRange: Int = this.rangeSetting.getValue()!!
      if (itemDistance > configuredRange) {
         return
      }
      val configuredGroup: Boolean = this.groupSetting.getValue()!!
      if (!configuredGroup) {
         this.renderItemLabel(event, itemEntity)
      }

      var addedToGroup: Boolean = false
      for (group in itemGroups) {
         if (group.tryAddItem(itemEntity)) {
            addedToGroup = true
            break
         }
      }
      if (!addedToGroup) {
         itemGroups.add(ESPItemGroup(itemEntity))
      }
   }

   @EventHandler(priority = -200)
   private fun onEntityRender(event: WorldRenderEvent.Main) {
      if (!this.isWorldUnavailable()) {
         val entities: MutableList<Entity> = this.renderEntities
         entities.clear()
         val entityLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         for (collectedEntity in entityLevel.entitiesForRendering()) {
            entities.add(collectedEntity)
         }
         val itemGroups: ArrayList<ESPItemGroup> = ArrayList()
         if (this.entityDrawSetting.getValue()!! && this.entityModeSetting.getValue() == EntityMode.BOX) {
            this.renderEntityBoxes(event, entities)
         }
         for (entity in entities) {
            if (entity is ItemEntity && this.itemDrawSetting.getValue()!!) {
               val itemMode: ESPModeChoice = this.modeSetting.getValue()!!
               if (itemMode == ESPModeChoice.BOTH || itemMode == ESPModeChoice.TEXT) {
                  this.renderItemLabels(event, entity, itemGroups)
               }
            }
            if (this.mobOwnerSetting.getValue()!!) {
               val helperSnapshot: ESPRenderHelper = this.renderHelper
               var ownerLabel: String? = helperSnapshot.getOwnerName(entity)
               if (ownerLabel != null) {
                  ownerLabel = "- " + ownerLabel + " -"
                  val ownerPartialTick: Float = event.partialTick
                  var ownerLabelPosition: Vec3 = entity.getPosition(ownerPartialTick).add(0.0, 0.2, 0.0)
                  if (entity !is ThrownEnderpearl) {
                     ownerLabelPosition = ownerLabelPosition.add(0.0, 1.3, 0.0)
                  }
                  val helperSnapshotForDraw: ESPRenderHelper = this.renderHelper
                  val ownerColor: Color = Color.white
                  helperSnapshotForDraw.drawLabel(
                     event, ownerLabel, ownerLabelPosition, 1.2f, ownerColor, null
                  )
               }
            }
         }
         val configuredGroup2: Boolean = this.groupSetting.getValue()!!
         if (configuredGroup2) {
            this.renderGroupedItemLabels(event, itemGroups)
         }
         val configuredChorus: Boolean = this.chorusSetting.getValue()!!
         if (configuredChorus) {
            synchronized(this.chorusPositions) {
               for (chorusEntry in this.chorusPositions.entries) {
                  val chorusHelperSnapshot: ESPRenderHelper = this.renderHelper
                  val teleportPosition: Vec3 = chorusEntry.key
                  val configuredChorusText: Color = this.chorusTextSetting.getValue()!!
                  chorusHelperSnapshot.drawLabel(
                     event, "Player teleport", teleportPosition, 1.0f, configuredChorusText, null
                  )
               }
            }
         }
         GuiDrawUtils.flush(true)
      } else {
         this.renderEntities.clear()
      }
   }

   @EventHandler(priority = -200)
   private fun onEntityHudRender(event: HudRenderEvent.Before) {
      if (!this.isWorldUnavailable() && this.entityDrawSetting.getValue()!!
         && this.entityModeSetting.getValue() == EntityMode.TWO_D
      ) {
         this.entity2DRenderer.render(event, this.renderEntities)
      } else if (this.isWorldUnavailable()) {
         this.renderEntities.clear()
      }
   }

   @EventHandler(priority = -99999999)
   private fun onBlockEntityRender(event: WorldRenderEvent.Overlay) {
      if (!this.isWorldUnavailable()) {
         if (this.renderHelper.hasStorageTargets()) {
            val configuredBlockDraw: Boolean = this.blockDrawSetting.getValue()!!
            if (configuredBlockDraw) {
               val blockShader: ShaderRegistry = ClientContext.shaderRegistry
               val blockRenderPass = Runnable {
                  this.renderBlockEntities(event)
               }
               blockShader.render("outline", blockRenderPass)
               return
            }
         }
      }
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundSoundPacket) {
         val soundPacket: ClientboundSoundPacket = event.packet as ClientboundSoundPacket
         if ((soundPacket.getSound().value() as SoundEvent) == SoundEvents.CHORUS_FRUIT_TELEPORT) {
            val chorusPositionMap: MutableMap<Vec3, Long> = this.chorusPositions
            val teleportPosition = Vec3(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ())
            val receivedAtMillis: Long = System.currentTimeMillis()
            chorusPositionMap.put(teleportPosition, receivedAtMillis)
         }
      }

      if (event.packet is ClientboundBlockUpdatePacket) {
         val configuredLooted: Boolean = this.lootedSetting.getValue()!!
         if (configuredLooted) {
            val blockUpdateTaskClient: Minecraft = ClientAccess.collectiveMinecraft
            val blockPacket: ClientboundBlockUpdatePacket = event.packet as ClientboundBlockUpdatePacket
            val blockUpdateTask = Runnable {
               val blockTrackerSnapshot: LootedStorageTracker = this.lootedStorage
               val deferredPosition: BlockPos = blockPacket.getPos()
               val updatedState: BlockState = blockPacket.getBlockState()
               blockTrackerSnapshot.onBlockUpdate(deferredPosition, updatedState)
            }
            blockUpdateTaskClient.execute(blockUpdateTask)
         }
      }
      if (event.packet is ClientboundSectionBlocksUpdatePacket) {
         val configuredLooted2: Boolean = this.lootedSetting.getValue()!!
         if (configuredLooted2) {
            val sectionUpdateTaskClient: Minecraft = ClientAccess.collectiveMinecraft
            val sectionPacket: ClientboundSectionBlocksUpdatePacket = event.packet as ClientboundSectionBlocksUpdatePacket
            val sectionUpdateTask = Runnable {
               val sectionTrackerForCallback: LootedStorageTracker = this.lootedStorage
               sectionPacket.runUpdates(sectionTrackerForCallback::onBlockUpdate)
            }
            sectionUpdateTaskClient.execute(sectionUpdateTask)
         }
      }
   }

   private fun renderBlockEntities(event: WorldRenderEvent) {
      event.poseStack!!.pushPose()

      this.renderingBlockEntities = true
      val cameraRenderState: CameraRenderState = ClientAccess.collectiveMinecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
      val cameraPosition: Vec3 = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
      val renderTarget: ColorOutlineBufferSource = ShaderRegistry.getBufferSource()
      EntityCullingOverrides.disableCulling()
      if (this.blockRefreshTimer.hasElapsedAndReset(10L)) {
         this.refreshBlockEntityCache()
      }
      for (entry in this.visibleBlockEntities) {
         val blockEntity: BlockEntity = entry.blockEntity
         val colorHelperSnapshot: ESPRenderHelper = this.renderHelper
         var highlightColor: Color = colorHelperSnapshot.getBlockEntityColor(blockEntity)
         val configuredLooted: Boolean = this.lootedSetting.getValue()!!
         if (configuredLooted) {
            if (this.lootedStorage.isLooted(blockEntity)) {
               highlightColor = LootedStorageTracker.getLootedColor()
            }
         }
         run {
            val opaqueColor = Color(
               (colorsModule!!.schemeSetting.getValue()!!).transform(highlightColor).hashCode(), false
            )
            val xPosition: BlockPos = blockEntity.getBlockPos()
            val relativeX: Double = xPosition.getX() - cameraPosition.x
            val yPosition: BlockPos = blockEntity.getBlockPos()
            val relativeY: Double = yPosition.getY() - cameraPosition.y
            val zPosition: BlockPos = blockEntity.getBlockPos()
            val relativeZ: Double = zPosition.getZ() - cameraPosition.z
            event.poseStack!!.pushPose()
            event.poseStack!!.translate(relativeX, relativeY, relativeZ)
            val colorBuffer: ColorSubmitNodeStorage = ShaderRegistry.getSubmitNodes()
            colorBuffer.setColorArgb(opaqueColor.hashCode())
            renderTarget.setColorArgb(opaqueColor.hashCode())
            val extractDispatcherSnapshot: BlockEntityRenderDispatcher = ClientAccess.collectiveMinecraft.getBlockEntityRenderDispatcher()
            val partialTick: Float = event.partialTick
            val blockRenderState: BlockEntityRenderState? = extractDispatcherSnapshot.tryExtractRenderState(blockEntity, partialTick, null, false)
            if (blockRenderState != null) {
               val rendererLookupDispatcherSnapshot: BlockEntityRenderDispatcher = ClientAccess.collectiveMinecraft.getBlockEntityRenderDispatcher()
               if (rendererLookupDispatcherSnapshot.getRenderer<BlockEntity, BlockEntityRenderState>(blockRenderState) != null) {
                  val submitDispatcher: BlockEntityRenderDispatcher = ClientAccess.collectiveMinecraft.getBlockEntityRenderDispatcher()
                  val submitPoses: PoseStack = event.poseStack!!
                  val submitNodes: ColorSubmitNodeStorage = ShaderRegistry.getSubmitNodes()
                  submitDispatcher.submit(blockRenderState, submitPoses, submitNodes, cameraRenderState)
                  ShaderRegistry.finishFeatureRendering()
                  return@run
               }
            }
            val fallbackDrawState: BlockState = blockEntity.getBlockState()
            this.currentlyRenderingBlock = fallbackDrawState.getBlock()
            val fallbackPoses: PoseStack = event.poseStack!!
            val fallbackModel = ClientAccess.collectiveMinecraft.getModelManager().getBlockStateModelSet().get(fallbackDrawState)
            val fallbackParts = java.util.ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>()
            fallbackModel.collectParts(net.minecraft.util.RandomSource.create(), fallbackParts)
            ShaderRegistry.getSubmitNodes().submitBlockModel(
               fallbackPoses,
               net.minecraft.client.renderer.Sheets.cutoutBlockItemSheet(),
               fallbackParts,
               intArrayOf(),
               16711935,
               0,
               0
            )
            this.currentlyRenderingBlock = null
         }
         event.poseStack!!.popPose()
      }
      ShaderRegistry.finishFeatureRendering()
      EntityCullingOverrides.restoreCulling()
      this.renderingBlockEntities = false
      event.poseStack!!.popPose()
   }

   private fun showItemGroups(ignored: Any?): Boolean {
      return this.modeSetting.getValue() == ESPModeChoice.TEXT ||
         this.modeSetting.getValue() == ESPModeChoice.BOTH
   }

   private fun showItemText(ignored: Any?): Boolean {
      return (this.modeSetting.getValue() == ESPModeChoice.TEXT ||
         this.modeSetting.getValue() == ESPModeChoice.BOTH) &&
         this.entitiesSetting.isExpanded() && this.itemsSetting.isExpanded()
   }

   private fun showTextOnlyOptions(ignored: Any?): Boolean {
      return this.modeSetting.getValue() == ESPModeChoice.TEXT &&
         this.entitiesSetting.isExpanded() && this.itemsSetting.isExpanded()
   }

   private fun showItemBoxes(ignored: Any?): Boolean {
      return (this.modeSetting.getValue() == ESPModeChoice.PLAIN ||
         this.modeSetting.getValue() == ESPModeChoice.BOTH) &&
         this.entitiesSetting.isExpanded() && this.itemsSetting.isExpanded()
   }

   override fun fromJson(json: JsonElement) {
      val moduleJson: JsonObject = json.getAsJsonObject()
      val settings: JsonObject? = moduleJson.getAsJsonObject("settings")
      val legacyDraw: Boolean = settings != null && !settings.has("DrawItems") && settings.has("Items")
      val legacyMode: Boolean = settings != null && settings.has("ItemsMode") &&
         "Box".equals(settings.get("ItemsMode")!!.getAsString(), ignoreCase = true)
      val legacyColors: Boolean = settings != null && listOf("Players", "Hostiles", "Animals", "Exp", "Items").any { group -> settings!!.has(group + "Outline") || settings!!.has(group + "Fill") }
      val legacyPearls: Boolean = settings != null && listOf("Fill", "Outline").any { key -> settings!!.has(key) && (settings!!.get(key)!!.isJsonArray() || settings!!.get(key)!!.isJsonObject()) }
      if (legacyDraw || legacyMode || legacyColors || legacyPearls) {
         val migrated: JsonObject = moduleJson.deepCopy()
         val migratedSettings: JsonObject = migrated.getAsJsonObject("settings")
         if (legacyDraw) migratedSettings.add("DrawItems", settings!!.get("Items")!!.deepCopy())
         if (legacyMode) migratedSettings.addProperty("ItemsMode", "Plain")
         for (group in listOf("Players", "Hostiles", "Animals", "Exp", "Items")) {
            if (!settings!!.has(group + "Color")) {
               val legacyColor: JsonElement? = if (settings.has(group + "Outline")) settings.get(group + "Outline") else settings.get(group + "Fill")
               if (legacyColor != null) migratedSettings.add(group + "Color", legacyColor.deepCopy())
            }
            migratedSettings.remove(group + "Outline")
            migratedSettings.remove(group + "Fill")
         }
         for (key in listOf("Fill", "Outline")) {
            val legacyColor: JsonElement? = settings!!.get(key)
            if (legacyColor != null && (legacyColor.isJsonArray() || legacyColor.isJsonObject())) {
               if (!settings.has("Pearls" + key)) migratedSettings.add("Pearls" + key, legacyColor.deepCopy())
               migratedSettings.remove(key)
            }
         }
         super.fromJson(migrated)
      } else {
         super.fromJson(json)
      }
   }

   private fun isEntity2D(): Boolean {
      return this.entityModeSetting.getValue() == EntityMode.TWO_D
   }

   enum class EntityMode(private val displayName: String) : NamedEntry {
      BOX("Box"), TWO_D("2D");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class ShadowMode(private val displayName: String) : NamedEntry {
      BLACK("Black"), DARK("Dark"), NONE("None");

      override fun getName(): String {
         return this.displayName
      }
   }

   private class BlockEntityRenderEntry(
      val blockEntity: BlockEntity,
      val negativeDistanceSquared: Double
   )

   companion object {
      private var colorsModule: ColorsModule? = null

      @JvmStatic
      fun bindModules(modules: ModuleRegistry) {
         colorsModule = modules.getModuleOrNull(ColorsModule::class.java)
      }
   }
}
