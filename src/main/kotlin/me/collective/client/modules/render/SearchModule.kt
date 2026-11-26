package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientLevelAddEntityEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.managers.BlockColorRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.managers.SocialRegistry
import me.collective.client.managers.SoundManager
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.ColorsModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.settings.options.ColorsSchemeChoice
import me.collective.client.audio.BuiltinSound
import me.collective.client.audio.SoundClip
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.collections.NamedIdentifier
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.inventory.EquipmentUtils
import me.collective.client.utils.world.ChunkScanner
import me.collective.client.utils.world.WorldUtils
import me.collective.client.render.ColorUtils
import me.collective.client.render.LineMeshRenderer
import me.collective.client.render.WorldBoxRenderer

import java.awt.Color
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.function.BiConsumer
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.fish.AbstractFish
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.VaultBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.MapColor.Brightness
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class SearchModule : Module("Search", "Helps finding whitelisted stuff.", ModuleCategory.RENDER) {
   private val rangeSetting: Setting<Float>
   private val blocksSetting: Setting<Boolean>
   private val selectedBlocksSetting: Setting<Set<Block>>
   private val heightSetting: Setting<HeightChoice>
   private val onlyOminousSetting: Setting<Boolean>
   private val ignoreNaturalSetting: Setting<Boolean>
   private val dungeonSetting: Setting<Boolean>
   private val fortressSetting: Setting<Boolean>
   private val bastionSetting: Setting<Boolean>
   private val ancientCitySetting: Setting<Boolean>
   private val trialChambersSetting: Setting<Boolean>
   private val entitiesSetting: Setting<Boolean>
   private val selectedEntitiesSetting: Setting<Set<EntityType<*>>>
   private val ignoreVehiclesSetting: Setting<Boolean>
   private val ignoreNakedsSetting: Setting<Boolean>
   private val soundSetting: Setting<Boolean>
   private val soundTypeSetting: Setting<NamedIdentifier>
   private val soundVolumeSetting: Setting<Float>
   private val renderSetting: Setting<Boolean>
   private val fillSetting: Setting<Int>
   private val outlineSetting: Setting<Int>
   private val tracersSetting: Setting<Int>
   private val blocksByPosition: MutableMap<BlockPos, SearchResult>
   private val knownBlockPositions: MutableSet<BlockPos>
   private val soundTimer: ElapsedTimer
   private val chunkScanner: ChunkScanner
   private var alertPending: Boolean = false
   private var minimumBuildHeight: Int = 0
   private var maximumBuildHeight: Int = 0

   private fun shouldSearchEntity(entity: Entity): Boolean {
      if (entity == ClientAccess.collectiveMinecraft.player) {
         return false
      }

      val selectedEntities: Set<*> = this.selectedEntitiesSetting.getValue() as Set<*>
      if (!selectedEntities.contains(entity.getType())) {
         return false
      }

      val ignoreVehicles: Boolean = this.ignoreVehiclesSetting.getValue()!!
      if (ignoreVehicles && entity.isVehicle()) {
         return false
      }

      if (entity is Player && this.ignoreNakedsSetting.getValue()!!) {
         var wearingArmor: Boolean = false
         for (armorPiece in EquipmentUtils.getArmorStacks(entity)) {
            if (!armorPiece.isEmpty()) {
               wearingArmor = true
               break
            }
         }
         if (!wearingArmor) {
            return false
         }
      }

      if (!this.rangeSetting.isAtMaximum()) {
         val range: Float = this.rangeSetting.getValue()!!
         if (this.isOutsideRange(entity.blockPosition(), range * range)) {
            return false
         }
      }
      return true
   }

   private fun getEntityColor(entity: Entity): Color {
      var color: Color = Color.GRAY
      if (entity is AbstractMinecart) {
         color = Color.blue
      }

      if (EntityUtils.isHostile(entity)) {
         color = Color.RED
      }

      if (entity is Player) {
         val friends: SocialRegistry = ClientContext.socialRegistry
         val nameComponent: Component = entity.getName()
         val playerName: String = nameComponent.getString()
         val fallbackColor: Color = color
         color = friends.getColor(playerName, fallbackColor)
      }

      if (entity is Animal || entity is AbstractFish) {
         color = Color.GREEN
      }

      return color
   }

   fun restartSearch() {
      this.blocksByPosition.clear()
      this.chunkScanner.stop()
      this.alertPending = false
      this.chunkScanner.start()
      this.rescanBlocks()
   }

   private fun resetChunkScanner() {
      this.chunkScanner.restartExecutor()
      this.blocksByPosition.clear()
   }

   private fun rescanBlocks() {
      this.blocksByPosition.clear()
      this.scanBlocksIfWorldAvailable()
   }

   private fun scanBlocksIfWorldAvailable() {
      if (!this.isWorldUnavailable()) {
         val scanner: ChunkScanner = this.chunkScanner

         scanner.scanLoadedChunks()
      }
   }

   private fun detectExistingEntities() {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      for (entity in level.entitiesForRendering()) {
         if (this.shouldSearchEntity(entity)) {
            this.alertPending = true
            return
         }
      }
   }

   override fun onEnable() {
      this.alertPending = false
      this.chunkScanner.start()
      this.rescanBlocks()
   }

   override fun onDisable() {
      this.blocksByPosition.clear()
      this.chunkScanner.stop()
      this.knownBlockPositions.clear()
   }

   private fun renderBlocks(event: WorldRenderEvent) {
      val drawFill: Boolean = !this.fillSetting.isAtMinimum()
      val drawOutline: Boolean = !this.outlineSetting.isAtMinimum()
      val drawTracers: Boolean = !this.tracersSetting.isAtMinimum()
      val limitRange: Boolean = !this.rangeSetting.isAtMaximum()
      val range: Float = this.rangeSetting.getValue()!!
      val squaredRange: Float = range * range

      for ((position, result) in this.blocksByPosition) {
         result.updateVisibility()
         if (limitRange && this.isOutsideRange(position, squaredRange)) {
            continue
         }

         if (result.visible) {
            if (drawFill) {
               val alpha: Float = this.fillSetting.getValue()!! * 0.01f
               WorldBoxRenderer.fillBox(
                  event.poseStack!!, result.bounds, ARGB.color(alpha, result.argb)
               )
            }
            if (drawOutline) {
               val alpha: Float = this.outlineSetting.getValue()!! * 0.01f
               val color: Int = ARGB.color(alpha, result.argb)
               WorldBoxRenderer.outlineBox(event.poseStack!!, result.bounds, color, 1.0f)
            }
         }

         if (drawTracers) {
            val alpha: Float = this.tracersSetting.getValue()!! * 0.01f
            LineMeshRenderer.addTracer(result.center, ARGB.color(alpha, result.argb))
         }
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      this.minimumBuildHeight = ClientAccess.collectiveMinecraft.level!!.getMinY()

      this.maximumBuildHeight = getMaximumBuildHeight()
      val soundEnabled: Boolean = this.soundSetting.getValue()!!
      if (soundEnabled && this.alertPending) {
         this.alertPending = false
         val timer: ElapsedTimer = this.soundTimer

         val soundInterval: Long = 100L
         if (timer.hasElapsedAndReset(soundInterval)) {
            val soundManager: SoundManager = ClientContext.soundManager
            val soundIdentifier: NamedIdentifier = this.soundTypeSetting.getValue()!!
            val sound: SoundClip = soundManager.getSound(soundIdentifier)
            val playbackVolume: Float = this.soundVolumeSetting.getValue()!!
            sound.play(playbackVolume)
         }
      }
   }

   @EventHandler
   private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {

      this.resetChunkScanner()
   }

   private fun renderEntities(event: WorldRenderEvent) {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      for (entity in level.entitiesForRendering()) {
         if (!this.shouldSearchEntity(entity)) {
            continue
         }

         val bounds: AABB = BoundingBoxUtils.getRenderBounds(entity, event.partialTick)
         val color: Color = (colorsModule!!.schemeSetting.getValue()!!)
            .transform(this.getEntityColor(entity))
         if (RotationManager.isVisible(bounds)) {
            if (!this.fillSetting.isAtMinimum()) {
               val alpha: Float = this.fillSetting.getValue()!! * 0.01f
               WorldBoxRenderer.fillBox(event.poseStack!!, bounds, ColorUtils.withAlpha(color, alpha))
            }
            if (!this.outlineSetting.isAtMinimum()) {
               val alpha: Float = this.outlineSetting.getValue()!! * 0.01f
               val outlineColor: Color = ColorUtils.withAlpha(color, alpha)
               WorldBoxRenderer.outlineBox(event.poseStack!!, bounds, outlineColor, 1.0f)
            }
         }

         if (!this.tracersSetting.isAtMinimum()) {
            val alpha: Float = this.tracersSetting.getValue()!! * 0.01f
            val tracerColor: Color = ColorUtils.withAlpha(color, alpha)
            val transformedTracerColor: Color = (colorsModule!!.schemeSetting.getValue()!!)
               .transform(tracerColor)
            LineMeshRenderer.addTracer(bounds.getCenter(), transformedTracerColor)
         }
      }
   }

   @EventHandler
   private fun onClientLevelAddEntityEvent(event: ClientLevelAddEntityEvent) {
      val soundEnabled: Boolean = this.soundSetting.getValue()!!
      if (soundEnabled) {
         val searchEntities: Boolean = this.entitiesSetting.getValue()!!
         if (searchEntities) {
            val entity: Entity = event.entity

            if (this.shouldSearchEntity(entity)) {
               this.alertPending = true
            }

            return
         }
      }
   }

   @EventHandler
   private fun onRenderWorld(event: WorldRenderEvent.Main) {
      LineMeshRenderer.begin(
         event.poseStack!!, 1.0f
      )
      val searchEntities: Boolean = this.entitiesSetting.getValue()!!
      if (searchEntities) {


         this.renderEntities(event)
      }

      synchronized(this.blocksByPosition) {
         val searchBlocks: Boolean = this.blocksSetting.getValue()!!
         if (searchBlocks) {

            this.renderBlocks(event)
         }
      }


      LineMeshRenderer.draw()
   }

   private fun shouldSearchBlock(state: BlockState): Boolean {
      if (state.isAir() || !(this.selectedBlocksSetting.getValue() as Set<*>).contains(state.getBlock())) {
         return false
      }
      if (this.onlyOminousSetting.getValue()!! && state.`is`(Blocks.VAULT)) {
         return state.getValueOrElse(VaultBlock.OMINOUS, false)
      }
      return true
   }

   private fun isOutsideRange(position: BlockPos, squaredRange: Float): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val eyePosition: Vec3 = player.getEyePosition()
      val x: Double = position.getX().toDouble()
      val y: Double = position.getY().toDouble()
      val z: Double = position.getZ().toDouble()
      return eyePosition.distanceToSqr(x, y, z) > squaredRange
   }

   @EventHandler
   private fun onRespawnPacket(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundRespawnPacket) {

         this.resetChunkScanner()
      }
   }

   init {
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 512.0f, 0.0f, 512.0f).withUnit("m"))
      this.blocksSetting = this.add(BooleanSetting("Blocks", true).markVisibilityDependency())
      this.selectedBlocksSetting = this.add(RegistrySetSetting<Block>("Selected", BuiltInRegistries.BLOCK,
         Blocks.NETHER_PORTAL, Blocks.ENDER_CHEST, Blocks.END_PORTAL).dependsOn(this.blocksSetting))
      this.heightSetting = this.add(EnumSetting<HeightChoice>("Height", HeightChoice.NEW).dependsOn(this.blocksSetting))
      this.onlyOminousSetting = this.add(BooleanSetting("OnlyOminous", false).dependsOn(this.blocksSetting))
      this.ignoreNaturalSetting = this.add(BooleanSetting("IgnoreNatural", false).markVisibilityDependency().dependsOn(this.blocksSetting))
      this.dungeonSetting = this.add(BooleanSetting("Dungeon", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting))
      this.fortressSetting = this.add(BooleanSetting("Fortress", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting))
      this.bastionSetting = this.add(BooleanSetting("Bastion", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting))
      this.ancientCitySetting = this.add(BooleanSetting("AncientCity", false).dependsOn(this.blocksSetting, this.ignoreNaturalSetting))
      this.trialChambersSetting = this.add(BooleanSetting("TrialChambers", true).dependsOn(this.blocksSetting, this.ignoreNaturalSetting))
      this.entitiesSetting = this.add(BooleanSetting("Entities", false).markVisibilityDependency())
      this.selectedEntitiesSetting = this.add(RegistrySetSetting<EntityType<*>>("Selected", BuiltInRegistries.ENTITY_TYPE)
         .dependsOn(this.entitiesSetting))
      this.ignoreVehiclesSetting = this.add(BooleanSetting("IgnoreVehicles", false).dependsOn(this.entitiesSetting))
      this.ignoreNakedsSetting = this.add(BooleanSetting("IgnoreNakeds", false).dependsOn(this.entitiesSetting))
      this.soundSetting = this.add(BooleanSetting("Sound", false).markVisibilityDependency())
      this.soundTypeSetting = this.add(IdentifierSetting("Type", BuiltinSound.WARNING.identifier).dependsOn(this.soundSetting))
      this.soundVolumeSetting = this.add(NumberSetting<Float>("Volume", 1.0f, 0.0f, 1.0f).dependsOn(this.soundSetting))
      this.renderSetting = this.add(BooleanSetting("Render", false).markAsGroup())
      this.fillSetting = this.add(NumberSetting<Int>("Fill", 0, 0, 100).withUnit("%").dependsOn(this.renderSetting))
      this.outlineSetting = this.add(NumberSetting<Int>("Outline", 100, 0, 100).withUnit("%").dependsOn(this.renderSetting))
      this.tracersSetting = this.add(NumberSetting<Int>("Tracers", 50, 0, 100).withUnit("%").dependsOn(this.renderSetting))
      this.blocksByPosition = Collections.synchronizedMap(HashMap<BlockPos, SearchResult>())
      this.knownBlockPositions = Collections.synchronizedSet(HashSet<BlockPos>())
      this.soundTimer = ElapsedTimer()
      this.blocksSetting.setConfigName("SearchBlocks")
      this.entitiesSetting.setConfigName("SearchEntities")
      this.selectedBlocksSetting.setConfigName("Blocks")
      this.selectedEntitiesSetting.setConfigName("Entities")
      this.fillSetting.setBoundaryLabel("None", SettingLabelCondition.MIN)
      this.outlineSetting.setBoundaryLabel("None", SettingLabelCondition.MIN)
      this.tracersSetting.setBoundaryLabel("None", SettingLabelCondition.MIN)
      this.rangeSetting.setBoundaryLabel("Unlimited", SettingLabelCondition.MAX)
      this.onlyOminousSetting.addVisibilityPredicate { ominousIgnoredValue ->
         (this.selectedBlocksSetting.getValue() as Set<*>).contains(Blocks.VAULT)
      }
      this.chunkScanner = ChunkScanner(
         1,
         BiConsumer { chunkPosition: ChunkPos, chunk: LevelChunk ->
            if (this.blocksByPosition.size > MAX_CACHED_BLOCKS) {
               // Java: return (skip chunk when the cache is full)
            } else {
               val firstY: Int = if (this.heightSetting.getValue() == HeightChoice.OLD)
                  0
               else
                  this.minimumBuildHeight
               for (localX in 0 until 16) {
                  val x: Int = chunkPosition.getMinBlockX() + localX
                  for (y in firstY until this.maximumBuildHeight) {
                     for (localZ in 0 until 16) {
                        scanPosition.set(x, y, chunkPosition.getMinBlockZ() + localZ)
                        val state: BlockState = chunk.getBlockState(scanPosition)

                        if (this.ignoreNaturalSetting.getValue()!!
                           && WorldUtils.isStructureChest(
                              chunk,
                              scanPosition,
                              this.fortressSetting.getValue()!!,
                              this.bastionSetting.getValue()!!,
                              this.ancientCitySetting.getValue()!!,
                              this.dungeonSetting.getValue()!!,
                              this.trialChambersSetting.getValue()!!
                           )
                        ) {
                           continue
                        }
                        if (!this.shouldSearchBlock(state) || !this.blocksSetting.getValue()!!) {
                           continue
                        }

                        val position: BlockPos = scanPosition.immutable()
                        this.knownBlockPositions.add(position)
                        this.blocksByPosition.put(position, SearchResult.create(position, state, chunk))

                        if (!this.rangeSetting.isAtMaximum()) {
                           val range: Float = this.rangeSetting.getValue()!!
                           if (this.isOutsideRange(position, range * range)) {
                              continue
                           }
                        }
                        this.alertPending = true
                     }
                  }
               }
            }
         },
         BiConsumer { unloadedChunkPosition: ChunkPos, unloadedChunk: LevelChunk ->
            synchronized(this.blocksByPosition) {
               this.blocksByPosition.keys.removeIf { position: BlockPos -> isPositionInChunk(position, unloadedChunkPosition) }
            }
            synchronized(this.knownBlockPositions) {
               this.knownBlockPositions.removeIf { position: BlockPos -> isPositionInChunk(position, unloadedChunkPosition) }
            }
         },
         BiConsumer { updatedPosition: BlockPos, updatedState: BlockState ->
            ClientAccess.collectiveMinecraft.execute {
               if (updatedState.isAir()) {
                  this.knownBlockPositions.remove(updatedPosition)
               }

               val updatedSelectedBlocks: Set<*> = this.selectedBlocksSetting.getValue() as Set<*>
               if (updatedSelectedBlocks.contains(updatedState.getBlock())) {
                  if (updatedState.isAir()) {
                     // Java: return
                  } else {
                     val updatedLevel = ClientAccess.collectiveMinecraft.level ?: return@execute
                     val updatedBlocks: MutableMap<BlockPos, SearchResult> = this.blocksByPosition
                     updatedBlocks.put(updatedPosition, SearchResult.create(updatedPosition, updatedState, updatedLevel))
                  val updatedNewlyKnown: Boolean = this.knownBlockPositions.add(updatedPosition)
                  val updatedCapturedRangeBoundaryOption: Setting<Float> = this.rangeSetting

                  var updatedOutsideRange: Boolean = false
                  if (!updatedCapturedRangeBoundaryOption.isAtMaximum()) {
                     val updatedRangeValue: Float = this.rangeSetting.getValue()!!
                     val updatedSecondRange: Float = this.rangeSetting.getValue()!!
                     val updatedSquaredRange: Float = updatedRangeValue * updatedSecondRange

                     updatedOutsideRange = this.isOutsideRange(updatedPosition, updatedSquaredRange)
                  }

                  if (!updatedOutsideRange) {
                     this.alertPending = updatedNewlyKnown
                  }
               }
            } else {
               this.blocksByPosition.remove(updatedPosition)
            }
            }
         }
      )
      this.blocksSetting.setChangeCallback(Runnable {
         if (this.blocksSetting.getValue()!!) {
            this.rescanBlocks()
         }
      })
      this.entitiesSetting.setChangeCallback(Runnable {
         if (this.entitiesSetting.getValue()!!) {
            this.detectExistingEntities()
         }
      })
      this.selectedBlocksSetting.setChangeCallback(Runnable { this.rescanBlocks() })
      this.onlyOminousSetting.setChangeCallback(Runnable { this.rescanBlocks() })
      this.heightSetting.setChangeCallback(Runnable { this.rescanBlocks() })
   }

   enum class HeightChoice(private val displayName: String) : NamedEntry {
      NEW("1.18"),
      OLD("1.12");

      override fun getName(): String {
         return this.displayName
      }
   }

   private class SearchResult(val center: Vec3, val bounds: AABB, val argb: Int) {
      var visible: Boolean = false

      fun updateVisibility() {
         val box: AABB = this.bounds

         this.visible = RotationManager.isVisible(
            box
         )
      }

      companion object {
         fun create(
            position: BlockPos, state: BlockState, shapeLevel: BlockGetter
         ): SearchResult {
            val shulkerTag: TagKey<Block> = BlockTags.SHULKER_BOXES
            val selectedShape: VoxelShape
            if (state.`is`(shulkerTag)) {
               selectedShape = Shapes.block()
            } else {
               selectedShape = state.getShape(shapeLevel, position)
            }

            val worldBounds: AABB
            if (selectedShape.isEmpty()) {
               worldBounds = AABB(position)
            } else {
               val shapeBounds: AABB = selectedShape.bounds()
               worldBounds = shapeBounds.move(position)
            }

            var color: Color = SearchModule.getBlockColor(state, shapeLevel)
            val colorsMod = SearchModule.colorsModule
            if (colorsMod != null) {
               val schemeOption: Setting<ColorsSchemeChoice> = colorsMod.schemeSetting
               val configuredScheme: ColorsSchemeChoice? = schemeOption.getValue()
               if (configuredScheme != null) {
                  color = configuredScheme.transform(color)
               }
            }
            return SearchResult(getBlockCenter(position), worldBounds, color.hashCode())
         }

         private fun getBlockCenter(position: BlockPos): Vec3 {
            return Vec3.atCenterOf(position)
         }
      }
   }

   companion object {
      internal var colorsModule: ColorsModule? = null
      private val scanPosition: MutableBlockPos = MutableBlockPos()
      private const val MAX_CACHED_BLOCKS: Int = 100_000

      internal fun getBlockColor(state: BlockState, mapLevel: BlockGetter): Color {
         val blockColors: BlockColorRegistry = ClientContext.blockColorRegistry
         val block: Block = state.getBlock()

         val configuredColor: Color? = blockColors.getColor(block)
         if (configuredColor != null) {
            return configuredColor
         }

         if (state.getBlock() == Blocks.NETHER_PORTAL) {
            return Color(128, 0, 255)
         }

         if (state.getBlock() == Blocks.ENDER_CHEST) {
            return Color(64, 32, 96)
         }

         val mapColor: MapColor = state.getMapColor(mapLevel, BlockPos.ZERO)
         val brightness: Brightness = Brightness.HIGH
         var argb: Int = mapColor.calculateARGBColor(brightness)
         if (state.getBlock() is BedBlock) {
            val bed: BedBlock = state.getBlock() as BedBlock
            val bedDye: DyeColor = bed.getColor()
            val bedMapColor: MapColor = bedDye.getMapColor()
            val bedBrightness: Brightness = Brightness.HIGH
            argb = bedMapColor.calculateARGBColor(bedBrightness)
         }

         if (mapColor == MapColor.NONE) {
            argb = -1
         }

         return Color(argb, false)
      }

      private fun isPositionInChunk(position: BlockPos, chunkPosition: ChunkPos): Boolean {
         return position.getX() >= chunkPosition.getMinBlockX() &&
         position.getX() <= chunkPosition.getMaxBlockX() &&
         position.getZ() >= chunkPosition.getMinBlockZ() &&
         position.getZ() <= chunkPosition.getMaxBlockZ()
      }

      private fun getMaximumBuildHeight(): Int {
         return WorldUtils.getTopY().toInt()
      }

      @JvmStatic
      fun bindModules(registry: ModuleRegistry) {
         colorsModule = registry.getModuleOrNull(ColorsModule::class.java)
      }
   }
}
