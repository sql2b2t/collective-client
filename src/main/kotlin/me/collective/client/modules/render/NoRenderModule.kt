package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientLevelAddEntityEvent
import me.collective.client.events.EntityRenderEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.StatusEffectQueryEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.LivingEntityModelSubmitEvent
import me.collective.client.events.NameTagRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.ParticleEngineAddEvent
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.settings.options.DimensionType
import me.collective.client.settings.options.ListFilterMode

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntConsumer
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ExplodeParticle
import net.minecraft.client.particle.HugeExplosionParticle
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.SmokeParticle
import net.minecraft.client.particle.SpellParticle
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.FogParameters
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.VineBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class NoRenderModule : Module("NoRender", "Removes the annoying stuff from your screen.", ModuleCategory.RENDER) {
   private val selfGroupSetting: Setting<Boolean>
   @JvmField
   val hurtCamSetting: Setting<Boolean>
   private val potionParticlesSetting: Setting<Boolean>
   @JvmField
   val darknessSetting: Setting<Boolean>
   @JvmField
   val blindnessSetting: Setting<Boolean>
   @JvmField
   val newSneakingSetting: Setting<Boolean>
   @JvmField
   val totemOverlaySetting: Setting<Boolean>
   @JvmField
   val handsSetting: Setting<Boolean>
   private val handOpacitySetting: Setting<Float>
   private val uiGroupSetting: Setting<Boolean>
   @JvmField
   val messageIndicatorSetting: Setting<Boolean>
   @JvmField
   val unsecureServerSetting: Setting<Boolean>
   @JvmField
   val tutorialToastSetting: Setting<Boolean>
   @JvmField
   val textShadowSetting: Setting<Boolean>
   @JvmField
   val bossBarsSetting: Setting<Boolean>
   @JvmField
   val scoreBoardSetting: Setting<Boolean>
   @JvmField
   val actionBarSetting: Setting<Boolean>
   @JvmField
   val tabIconsSetting: Setting<Boolean>
   @JvmField
   val heldTooltipsSetting: Setting<Boolean>
   @JvmField
   val tintSetting: Setting<Boolean>
   private val worldGroupSetting: Setting<Boolean>
   @JvmField
   val explosionsSetting: Setting<Boolean>
   @JvmField
   val worldBorderSetting: Setting<Boolean>
   @JvmField
   val skyLightSetting: Setting<Boolean>
   @JvmField
   val celestialBodiesSetting: Setting<Boolean>
   @JvmField
   val starsSetting: Setting<Boolean>
   @JvmField
   val mapsSetting: Setting<Boolean>
   private val chunkRadiusPacketsSetting: Setting<Boolean>
   @JvmField
   val blockFireSetting: Setting<Boolean>
   @JvmField
   val vinesSetting: Setting<Boolean>
   @JvmField
   val fogSetting: Setting<Boolean>
   @JvmField
   val rangeSetting: Setting<Float>
   @JvmField
   var skySetting: Setting<Boolean>
   @JvmField
   val particlesSetting: Setting<Boolean>
   @JvmField
   val particleTypesSetting: Setting<Set<ParticleType<*>>>
   @JvmField
   val particleSelectionSetting: Setting<ListFilterMode>
   private val blockBreakingParticlesSetting: Setting<Boolean>
   private val entityGroupSetting: Setting<Boolean>
   @JvmField
   val hurtSetting: Setting<Boolean>
   @JvmField
   val shadowSetting: Setting<Boolean>
   @JvmField
   val glowSetting: Setting<Boolean>
   @JvmField
   val nameTagsSetting: Setting<Boolean>
   @JvmField
   val interpolationSetting: Setting<Boolean>
   @JvmField
   val armorTrimSetting: Setting<Boolean>
   @JvmField
   val elytraSetting: Setting<Boolean>
   @JvmField
   val fireSetting: Setting<Boolean>
   @JvmField
   val othersSetting: Setting<Boolean>
   @JvmField
   val armorSetting: Setting<Boolean>
   @JvmField
   val armorOpacitySetting: Setting<Float>
   @JvmField
   val noClusterSetting: Setting<Boolean>
   @JvmField
   val clusterAlphaSetting: Setting<Int>
   @JvmField
   val wardensSetting: Setting<Boolean>
   @JvmField
   val wardenDistanceSetting: Setting<Int>
   @JvmField
   val itemsSetting: Setting<Boolean>
   @JvmField
   val limitSetting: Setting<Int>
   @JvmField
   val tileEntitiesSetting: Setting<Boolean>
   @JvmField
   val tileTypesSetting: Setting<Set<BlockEntityType<*>>>
   @JvmField
   val blockEntitySelectionSetting: Setting<ListFilterMode>
   @JvmField
   val tileDistanceSetting: Setting<Int>
   @JvmField
   val entitiesSetting: Setting<Boolean>
   @JvmField
   val entityTypesSetting: Setting<Set<EntityType<*>>>
   @JvmField
   val removalSetting: Setting<RemovalChoice>
   @JvmField
   val entitySelectionSetting: Setting<ListFilterMode>
   private var worldRenderPassActive: Boolean = false
   private var lastEntityAlpha: Float = 0.0f
   private var pendingCrystalFirePosition: BlockPos? = null

   override fun onToggle() {
      val tileEntities: Setting<Boolean> = this.tileEntitiesSetting
      if (tileEntities.getValue()!!) {
         WorldUtils.reloadRenderer()
      }
   }

   fun shouldHideBlock(state: BlockState): Boolean {
      if (!this.isToggled()) return false

      val fire: Setting<Boolean> = this.blockFireSetting
      if (fire.getValue()!!) {
         val fireBlock: Block = Blocks.FIRE
         if (state.`is`(fireBlock)) return true
      }

      if (!this.vinesSetting.getValue()!!) return false
      return this.isVineLikeBlock(state)
   }

   fun shouldHideEntity(entity: Entity?): Boolean {
      if (entity == null) return false
      val type: EntityType<*> = entity.getType()
      return this.shouldHideEntityType(type)
   }

   fun shouldHideTabIcons(): Boolean {
      if (!this.isToggled()) return false
      return this.tabIconsSetting.getValue()!!
   }

   fun getHandOpacity(): Float {
      if (this.isToggled()) {
         val hands: Setting<Boolean> = this.handsSetting
         if (hands.getValue()!! && !ClientContext.shaderRegistry.renderingIntoTarget && !ClientContext.shaderRegistry.renderingHands) {
            val alpha: Float = this.handOpacitySetting.getValue()!!
            if (alpha == 0.0f) return 0.0f
            return Math.max(alpha, 0.11f)
         }
      }

      return 1.0f
   }

   fun shouldHideScreenTint(): Boolean {
      if (!this.isToggled()) return false
      return this.tintSetting.getValue()!!
   }

   fun shouldHideCelestialBodies(): Boolean {
      if (!this.isToggled()) return false
      return this.celestialBodiesSetting.getValue()!!
   }

   fun shouldHideStars(): Boolean {
      if (!this.isToggled()) return false
      return this.starsSetting.getValue()!!
   }

   fun shouldHideMaps(): Boolean {
      if (!this.isToggled()) return false
      return this.mapsSetting.getValue()!! && this.worldRenderPassActive
   }

   fun getEntityAlpha(): Float {
      if (!this.isToggled()) {
         return 1.0f
      }
      return this.lastEntityAlpha
   }

   fun shouldHideEntityShadow(): Boolean {
      if (!this.isToggled()) return false
      return this.shadowSetting.getValue()!!
   }

   fun areHandsFullyHidden(): Boolean {
      if (!this.isToggled()) return false
      if (!this.handsSetting.getValue()!! || ClientContext.shaderRegistry.renderingHands || ClientContext.shaderRegistry.renderingIntoTarget) return false
      return this.handOpacitySetting.getValue()!! == 0.0f
   }

   @EventHandler
   private fun onWorldRenderFinished(event: WorldRenderEvent.Main) {
      this.worldRenderPassActive = false
   }

   fun shouldHideBlockBreakingParticles(): Boolean {
      if (!this.isToggled()) return false
      if (!this.particlesSetting.getValue()!!) return false
      return this.blockBreakingParticlesSetting.getValue()!!
   }

   private fun pruneExcessItems() {
      if (this.isWorldUnavailable()) return
      if (!this.itemsSetting.getValue()!!) return


      val excessIds = IntArrayList(50)
      var itemCount: Int = 0
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      for (entity in level.entitiesForRendering()) {
         if (entity is ItemEntity) {
            val previousCount: Int = itemCount++
            val maximum: Int = this.limitSetting.getValue()!!
            if (previousCount >= maximum) {
               val id: Int = entity.getId()
               excessIds.add(id)
            }
         }
      }
      val removeItem = IntConsumer { id ->
         val removalLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val entityId: Int = id
         val reason: Entity.RemovalReason = Entity.RemovalReason.KILLED
         removalLevel.removeEntity(entityId, reason)
      }
      excessIds.forEach(removeItem)
   }

   fun getArmorOpacity(): Float {
      if (this.isToggled()) {
         val armor: Setting<Boolean> = this.armorSetting
         if (armor.getValue()!!) {
            val alpha: Float = this.armorOpacitySetting.getValue()!!
            if (alpha == 0.0f) return 0.0f
            return Math.max(alpha, 0.11f)
         }
      }

      return 1.0f
   }

   @EventHandler
   private fun onEntityRenderCheck(event: EntityRenderEvent.Pre) {
      val entity: Entity = event.entity!!
      if (!this.shouldHideEntity(entity)) return
      if (event.entity == ClientAccess.collectiveMinecraft.player!!) return
      val removal: Setting<RemovalChoice> = this.removalSetting
      if (removal.getValue() == RemovalChoice.FULL) {
         event.cancel()
      }
   }

   fun applyFogSettings(fog: FogParameters, viewDistance: Int): FogParameters {
      val range: Setting<Float> = this.rangeSetting
      val start: Float
      val end: Float
      if (range.isAtMaximum()) {
         val distance: Float = Math.max(1024.0f, viewDistance * 160.0f)
         start = distance
         end = distance
      } else {
         start = 0.0f
         val configuredRange: Float = this.rangeSetting.getValue()!!
         val scaledRange: Float = 0.5f * configuredRange
         end = scaledRange * (viewDistance * 16)
      }
      return FogParameters(start, end, fog.shape(), fog.red(), fog.green(), fog.blue(), fog.alpha())
   }

   @EventHandler
   private fun onStatusEffectRender(event: StatusEffectQueryEvent) {
      val blindness: Setting<Boolean> = this.blindnessSetting
      if (blindness.getValue()!!) {
         if (event.effect == MobEffects.BLINDNESS) {
            event.cancel()
         }
      }
      val darkness: Setting<Boolean> = this.darknessSetting
      if (darkness.getValue()!!) {
         if (event.effect == MobEffects.DARKNESS) {
            event.cancel()
         }
      }
   }

   @EventHandler(priority = -100)
   private fun updateEntityAlpha(event: LivingEntityModelSubmitEvent) {
      val noCluster: Setting<Boolean> = this.noClusterSetting
      if (noCluster.getValue()!!) {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val playerPosition: Vec3 = player.position()
         val entityPosition: Vec3 = event.entity!!.position()
         val distance: Double = playerPosition.distanceTo(entityPosition)
         val width: Float = event.entity!!.getBbWidth()
         if (distance <= width) {
            if (event.entity != ClientAccess.collectiveMinecraft.player!!) {
               val distanceRatio: Float = (distance / width).toFloat()
               val alphaValue: Int = this.clusterAlphaSetting.getValue()!!
               val minimumAlpha: Float = alphaValue / 255.0f
               val alpha: Float = minimumAlpha + distanceRatio * (1.0f - minimumAlpha)
               event.alpha = alpha
               this.lastEntityAlpha = event.alpha
               return
            }
         }
         this.lastEntityAlpha = 1.0f
      } else {
         this.lastEntityAlpha = 1.0f
      }
   }

   @EventHandler
   private fun onClientLevelAddEntityEvent(event: ClientLevelAddEntityEvent) {
      if (event.entity !is ItemEntity) return
      if (this.isWorldUnavailable()) return
      if (!this.itemsSetting.getValue()!!) return
      val maximum: Int = this.limitSetting.getValue()!!
      if (maximum <= 0) {
         event.cancel()
         return
      }
      var itemCount: Int = 0
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      for (entity in level.entitiesForRendering()) {
         if (entity is ItemEntity) {
            itemCount++
            // Существующих >= maximum — новый entity превысит лимит (prune оставляет ровно maximum).
            if (itemCount >= maximum) {
               event.cancel()
               break
            }
         }
      }
   }

   fun shouldHideEntityType(type: EntityType<*>): Boolean {
      if (!this.isToggled()) return false
      if (!this.entitiesSetting.getValue()!!) return false
      val entitySelection: Setting<ListFilterMode> = this.entitySelectionSetting
      val selection: ListFilterMode = entitySelection.getValue()!!
      val selectedTypes: Collection<*> = this.entityTypesSetting.getValue()!!
      return !ListFilterMode.matches(selection, type, selectedTypes)
   }

   fun shouldHideParticle(options: ParticleOptions): Boolean {
      if (!this.isToggled()) return false
      if (!this.particlesSetting.getValue()!!) return false
      val selection: ListFilterMode = this.particleSelectionSetting.getValue()!!
      val type: ParticleType<*> = options.getType()
      val selectedTypes: Collection<*> = this.particleTypesSetting.getValue()!!
      return !ListFilterMode.matches(selection, type, selectedTypes)
   }

   fun shouldHideBlockEntityType(type: BlockEntityType<*>): Boolean {
      if (!this.isToggled()) return false
      if (!this.tileEntitiesSetting.getValue()!!) return false
      val selection: ListFilterMode = this.blockEntitySelectionSetting.getValue()!!
      val selectedTypes: Collection<*> = this.tileTypesSetting.getValue()!!
      return !ListFilterMode.matches(selection, type, selectedTypes)
   }

   @EventHandler
   private fun onNameTagRender(event: NameTagRenderEvent) {
      val nameTags: Setting<Boolean> = this.nameTagsSetting
      if (nameTags.getValue()!!) {
         event.cancel()
      }
   }

   @EventHandler
   private fun onParticleEngineAddEvent(event: ParticleEngineAddEvent) {
      val particle: Particle = event.particle
      if (particle is SmokeParticle) {
         val smoke: SmokeParticle = particle
         val speedSquared: Double = smoke.xd * smoke.xd + smoke.yd * smoke.yd + smoke.zd * smoke.zd
         val speed: Double = Math.sqrt(speedSquared)
         if (speed > 0.25) {
            val explosions: Setting<Boolean> = this.explosionsSetting
            if (explosions.getValue()!!) {
               event.cancel()
            }
         }
         if (speed <= 0.25) {
            val fire: Setting<Boolean> = this.blockFireSetting
            if (fire.getValue()!!) {
               event.cancel()
            }
         }
      }
      if (particle is HugeExplosionParticle || particle is ExplodeParticle) {
         val explosions: Setting<Boolean> = this.explosionsSetting
         if (explosions.getValue()!!) {
            event.cancel()
         }
      }
      val potions: Setting<Boolean> = this.potionParticlesSetting
      if (potions.getValue()!! && particle is SpellParticle) {
         event.cancel()
      }
   }

   private fun isVineLikeBlock(state: BlockState): Boolean {
      if (state.getBlock() is VineBlock) return true
      val caveVines: Block = Blocks.CAVE_VINES
      if (state.`is`(caveVines)) return true
      val caveVinesPlant: Block = Blocks.CAVE_VINES_PLANT
      if (state.`is`(caveVinesPlant)) return true
      val glowLichen: Block = Blocks.GLOW_LICHEN
      return state.`is`(glowLichen)
   }

   @EventHandler
   private fun onWorldRenderStarted(event: WorldRenderEvent.BeforeBufferSource) {
      this.worldRenderPassActive = true
   }

   @EventHandler
   private fun onPacketReceive(event: PacketEvent.Receive) {
      if (event.packet is ClientboundSetChunkCacheRadiusPacket) {
         val chunks: Setting<Boolean> = this.chunkRadiusPacketsSetting
         if (chunks.getValue()!!) {
            event.cancel()
         }
      }
      if (!this.fireSetting.getValue()!!) return
      if (WorldUtils.getDimension() != DimensionType.THE_END) return
      if (event.packet is ClientboundBlockUpdatePacket && this.pendingCrystalFirePosition != null) {
         val update: ClientboundBlockUpdatePacket = event.packet as ClientboundBlockUpdatePacket
         if (update.getPos() != this.pendingCrystalFirePosition) return
         val state: BlockState = update.getBlockState()
         val fireBlock: Block = Blocks.FIRE
         if (!state.`is`(fireBlock)) return
         this.pendingCrystalFirePosition = null
         event.cancel()
      }
      if (event.packet is ClientboundAddEntityPacket) {
         val spawn: ClientboundAddEntityPacket = event.packet as ClientboundAddEntityPacket
         if (spawn.getType() == EntityType.END_CRYSTAL) {
            val x: Double = spawn.getX()
            val y: Double = spawn.getY()
            val z: Double = spawn.getZ()
            this.pendingCrystalFirePosition = BlockPos.containing(x, y, z)
         }
      }
   }

   fun shouldHideBlockEntity(state: BlockState): Boolean {
      val typesByBlock: Map<Block, BlockEntityType<*>> = BLOCK_ENTITY_TYPES_BY_BLOCK
      val block: Block = state.getBlock()
      val type: BlockEntityType<*>? = typesByBlock[block]
      if (type == null) return false
      return this.shouldHideBlockEntityType(type)
   }

   init {
      this.selfGroupSetting = this.add(BooleanSetting("Self", false).markAsGroup())
      this.hurtCamSetting = this.add(BooleanSetting("HurtCam", true).dependsOn(this.selfGroupSetting))
      this.potionParticlesSetting = this.add(BooleanSetting("Potions", false).dependsOn(this.selfGroupSetting))
      this.darknessSetting = this.add(BooleanSetting("Darkness", true).dependsOn(this.selfGroupSetting))
      this.blindnessSetting = this.add(BooleanSetting("Blindness", true).dependsOn(this.selfGroupSetting))
      this.newSneakingSetting = this.add(BooleanSetting("NewSneaking", false).dependsOn(this.selfGroupSetting))
      this.totemOverlaySetting = this.add(BooleanSetting("TotemOverlay", false).dependsOn(this.selfGroupSetting))
      this.handsSetting = this.add(BooleanSetting("Hands", false).markVisibilityDependency().dependsOn(this.selfGroupSetting))
      this.handOpacitySetting = this.add(NumberSetting<Float>("Opacity", 0.0f, 0.0f, 1.0f).dependsOn(this.selfGroupSetting, this.handsSetting))
      this.uiGroupSetting = this.add(BooleanSetting("UI", false).markAsGroup())
      this.messageIndicatorSetting = this.add(BooleanSetting("MessageIndicator", true).dependsOn(this.uiGroupSetting))
      this.unsecureServerSetting = this.add(BooleanSetting("UnsecureServer", true).dependsOn(this.uiGroupSetting))
      this.tutorialToastSetting = this.add(BooleanSetting("TutorialToast", true).dependsOn(this.uiGroupSetting))
      this.textShadowSetting = this.add(BooleanSetting("TextShadow", false).dependsOn(this.uiGroupSetting))
      this.bossBarsSetting = this.add(BooleanSetting("BossBars", false).dependsOn(this.uiGroupSetting))
      this.scoreBoardSetting = this.add(BooleanSetting("ScoreBoard", false).dependsOn(this.uiGroupSetting))
      this.actionBarSetting = this.add(BooleanSetting("ActionBar", true).dependsOn(this.uiGroupSetting))
      this.tabIconsSetting = this.add(BooleanSetting("TabIcons", false).dependsOn(this.uiGroupSetting))
      this.heldTooltipsSetting = this.add(BooleanSetting("HeldTooltips", false).dependsOn(this.uiGroupSetting))
      this.tintSetting = this.add(BooleanSetting("Tint", false).dependsOn(this.uiGroupSetting))
      this.worldGroupSetting = this.add(BooleanSetting("World", false).markAsGroup())
      this.explosionsSetting = this.add(BooleanSetting("Explosions", true).dependsOn(this.worldGroupSetting))
      this.worldBorderSetting = this.add(BooleanSetting("WorldBorder", true).dependsOn(this.worldGroupSetting))
      this.skyLightSetting = this.add(BooleanSetting("SkyLight", false).dependsOn(this.worldGroupSetting))
      this.celestialBodiesSetting = this.add(BooleanSetting("CelestialBodies", false).dependsOn(this.worldGroupSetting))
      this.starsSetting = this.add(BooleanSetting("Stars", false).dependsOn(this.worldGroupSetting))
      this.mapsSetting = this.add(BooleanSetting("Maps", false).dependsOn(this.worldGroupSetting))
      this.chunkRadiusPacketsSetting = this.add(BooleanSetting("Chunks", false).dependsOn(this.worldGroupSetting))
      this.blockFireSetting = this.add(BooleanSetting("Fire", false).dependsOn(this.worldGroupSetting))
      this.vinesSetting = this.add(BooleanSetting("Vines", false).dependsOn(this.worldGroupSetting))
      this.fogSetting = this.add(BooleanSetting("Fog", false).markVisibilityDependency().dependsOn(this.worldGroupSetting))
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 3.0f, 0.1f, 10.0f).dependsOn(this.fogSetting, this.worldGroupSetting))
      this.skySetting = this.add(BooleanSetting("Sky", false).dependsOn(this.fogSetting, this.worldGroupSetting))
      this.particlesSetting = this.add(BooleanSetting("Particles", false).markVisibilityDependency().dependsOn(this.worldGroupSetting))
      this.particleTypesSetting = this.add(RegistrySetSetting<ParticleType<*>>("ParticleTypes", BuiltInRegistries.PARTICLE_TYPE)
         .dependsOn(this.particlesSetting, this.worldGroupSetting))
      this.particleSelectionSetting = this.add(EnumSetting<ListFilterMode>("Selection", ListFilterMode.BLACKLIST).dependsOn(this.particlesSetting, this.worldGroupSetting))
      this.blockBreakingParticlesSetting = this.add(BooleanSetting("NoBlockBreaking", false).dependsOn(this.particlesSetting, this.worldGroupSetting))
      this.entityGroupSetting = this.add(BooleanSetting("Entities", false).markAsGroup())
      this.hurtSetting = this.add(BooleanSetting("Hurt", true).dependsOn(this.entityGroupSetting))
      this.shadowSetting = this.add(BooleanSetting("Shadow", false).dependsOn(this.entityGroupSetting))
      this.glowSetting = this.add(BooleanSetting("Glow", true).dependsOn(this.entityGroupSetting))
      this.nameTagsSetting = this.add(BooleanSetting("NameTags", false).dependsOn(this.entityGroupSetting))
      this.interpolationSetting = this.add(BooleanSetting("Interpolation", false).dependsOn(this.entityGroupSetting))
      this.armorTrimSetting = this.add(BooleanSetting("ArmorTrim", false).dependsOn(this.entityGroupSetting))
      this.elytraSetting = this.add(BooleanSetting("Elytra", false).dependsOn(this.entityGroupSetting))
      this.fireSetting = this.add(BooleanSetting("Fire", true).markVisibilityDependency().dependsOn(this.entityGroupSetting))
      this.othersSetting = this.add(BooleanSetting("Others", false).dependsOn(this.entityGroupSetting, this.fireSetting))
      this.armorSetting = this.add(BooleanSetting("Armor", false).markVisibilityDependency().dependsOn(this.entityGroupSetting))
      this.armorOpacitySetting = this.add(NumberSetting<Float>("Opacity", 0.0f, 0.0f, 1.0f).dependsOn(this.entityGroupSetting, this.armorSetting))
      this.noClusterSetting = this.add(BooleanSetting("NoCluster", false).markVisibilityDependency().dependsOn(this.entityGroupSetting))
      this.clusterAlphaSetting = this.add(NumberSetting<Int>("ClusterAlpha", 120, 0, 255).dependsOn(this.noClusterSetting, this.entityGroupSetting))
      this.wardensSetting = this.add(BooleanSetting("Wardens", false).markVisibilityDependency().dependsOn(this.entityGroupSetting))
      this.wardenDistanceSetting = this.add(NumberSetting<Int>("WardenDistance", 32, 1, 128).withUnit("m").dependsOn(this.entityGroupSetting, this.wardensSetting))
      this.itemsSetting = this.add(BooleanSetting("Items", false).markVisibilityDependency().dependsOn(this.entityGroupSetting))
      this.limitSetting = this.add(NumberSetting<Int>("Limit", 128, 0, 256).dependsOn(this.entityGroupSetting, this.itemsSetting))
      this.tileEntitiesSetting = this.add(BooleanSetting("TileEntities", false).markVisibilityDependency().dependsOn(this.entityGroupSetting))
      this.tileTypesSetting = this.add(RegistrySetSetting<BlockEntityType<*>>("TileTypes", BuiltInRegistries.BLOCK_ENTITY_TYPE)
         .dependsOn(this.entityGroupSetting, this.tileEntitiesSetting))
      this.blockEntitySelectionSetting = this.add(EnumSetting<ListFilterMode>("Selection", ListFilterMode.BLACKLIST).dependsOn(this.entityGroupSetting, this.tileEntitiesSetting))
      this.tileDistanceSetting = this.add(NumberSetting<Int>("TileDistance", 64, 1, 256).withUnit("m").dependsOn(this.entityGroupSetting, this.tileEntitiesSetting))
      this.entitiesSetting = this.add(BooleanSetting("Entities", false).markVisibilityDependency().dependsOn(this.entityGroupSetting))
      this.entityTypesSetting = this.add(RegistrySetSetting<EntityType<*>>("EntityTypes", BuiltInRegistries.ENTITY_TYPE)
         .dependsOn(this.entitiesSetting, this.entityGroupSetting))
      this.removalSetting = this.add(EnumSetting<RemovalChoice>("Removal", RemovalChoice.PARTIAL).dependsOn(this.entityGroupSetting, this.entitiesSetting))
      this.entitySelectionSetting = this.add(EnumSetting<ListFilterMode>("Selection", ListFilterMode.BLACKLIST).dependsOn(this.entityGroupSetting, this.entitiesSetting))
      this.setDrawn(false)
      this.itemsSetting.setChangeCallback(this::pruneExcessItems)
      this.tileEntitiesSetting.setChangeCallback(WorldUtils::reloadRenderer)
      this.tileTypesSetting.setChangeCallback(WorldUtils::reloadRenderer)
      this.blockEntitySelectionSetting.setChangeCallback(WorldUtils::reloadRenderer)
      this.blockFireSetting.setChangeCallback(WorldUtils::reloadRenderer)
      this.vinesSetting.setChangeCallback(WorldUtils::reloadRenderer)
      this.blockFireSetting.setConfigName("FireBlock")
      this.entityGroupSetting.setConfigName("EntitiesPage")
      this.particleSelectionSetting.setConfigName("ParticleSelection")
      this.entitySelectionSetting.setConfigName("EntitySelection")
      this.handOpacitySetting.setConfigName("HandsOpacity")
      this.blockFireSetting.setConfigName("FireBlock")
   }

   enum class RemovalChoice(private val displayName: String) : NamedEntry {
      PARTIAL("Partial"),
      FULL("Remove");

      override fun getName(): String {
         return this.displayName
      }
   }

   companion object {
      private val BLOCK_ENTITY_TYPES_BY_BLOCK: Map<Block, BlockEntityType<*>>

      init {
         val typesByBlock = Object2ObjectOpenHashMap<Block, BlockEntityType<*>>()
         val blockEntityTypes = BuiltInRegistries.BLOCK_ENTITY_TYPE
         blockEntityTypes.forEach { blockEntityType ->
            val validBlocks = blockEntityType.validBlocks
            validBlocks.forEach { block -> typesByBlock.put(block, blockEntityType) }
         }
         BLOCK_ENTITY_TYPES_BY_BLOCK = typesByBlock
      }
   }
}
