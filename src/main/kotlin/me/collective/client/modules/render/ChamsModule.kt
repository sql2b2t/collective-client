package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EntityRenderEvent
import me.collective.client.events.GlintTextureTransformEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.PlayerTotemEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.RenderTypeDrawEvent
import me.collective.client.render.RenderTargetUtils
import me.collective.client.render.MaskShaderRenderer
import me.collective.client.utils.entity.SidePlayer
import me.collective.client.managers.RotationManager
import me.collective.client.render.MeshBuffer
import me.collective.client.render.ColorUtils
import kotlin.math.max
import me.collective.client.render.ClientRenderPipelines
import me.collective.client.render.RenderUtils
import me.collective.client.utils.combat.EndCrystalState
import me.collective.client.render.ChamsColorProvider
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.render.GameOptionsOverrides
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.render.EntityRenderRelation
import me.collective.client.render.MeshDrawContext
import me.collective.client.render.ClientRenderTypes
import me.collective.client.render.EntityChamsRenderer
import me.collective.client.render.ChamsBufferSource
import me.collective.client.render.ForcedEntityLayers
import me.collective.client.events.EventStage
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.ColorsModule
import me.collective.client.modules.combat.AutoCrystalModule
import me.collective.client.modules.player.FreecamModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.AutoCrystalInstantChoice
import me.collective.client.settings.options.ChamsStyle
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import java.util.HashMap
import java.util.function.BiConsumer
import java.util.function.Predicate
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderBuffers
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.WalkAnimationState
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

class ChamsModule : Module("Chams", "Wallhack on entities.", ModuleCategory.RENDER) {
   companion object {

      private val SHINE_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("collective-mount", "textures/shine.png")
      private val GLINT_COLOR_VECTOR: Vector3f = Vector3f(1.0f, 1.0f, 1.0f)

      private var autoCrystalModule: AutoCrystalModule? = null
      private var noRenderModule: NoRenderModule? = null
      private var animationsModule: AnimationsModule? = null
      private var freecamModule: FreecamModule? = null
      private var colorsModule: ColorsModule? = null

      @JvmField
      var renderingShineModels: Boolean = false

      @JvmField
      var renderingGlintBatch: Boolean = false

      private fun getStyleSetting(owner: ChamsModule, entity: Entity): Setting<ChamsStyle> {
         if (entity === ClientAccess.collectiveMinecraft.player!!) {
            return owner.selfSetting
         } else if (entity is EndCrystal) {
            return owner.crystalStyleSetting
         } else if (entity is Player) {
            return owner.playerStyleSetting
         } else {
            return if (entity is AgeableMob) owner.animalsSetting else owner.hostilesSetting
         }
      }

      private fun getRenderRange(owner: ChamsModule, entityType: EntityType<*>): Float {
         return if (entityType == EntityType.PLAYER) {
            owner.playersSetting.getValue().toFloat()
         } else if (entityType == EntityType.END_CRYSTAL) {
            owner.crystalsSetting.getValue().toFloat()
         } else {
            owner.othersSetting.getValue().toFloat()
         }
      }

      @JvmStatic
      fun bindReferencedModules(modules: ModuleRegistry) {
         autoCrystalModule = modules.getModule(AutoCrystalModule::class.java) as AutoCrystalModule?
         noRenderModule = modules.getModule(NoRenderModule::class.java) as NoRenderModule?
         animationsModule = modules.getModule(AnimationsModule::class.java) as AnimationsModule?
         freecamModule = modules.getModule(FreecamModule::class.java) as FreecamModule?
         colorsModule = modules.getModule(ColorsModule::class.java) as ColorsModule?
         ChamsColorProvider.bindModuleRegistry(modules)
      }
   }

   @JvmField
   val cullingSetting: Setting<Boolean>
   @JvmField
   val blendSetting: Setting<Boolean>
   private val globalExtraLayerSetting: Setting<Boolean>
   private val lineWidthSetting: Setting<Float>
   @JvmField
   val fadeSetting: Setting<Boolean>
   @JvmField
   val fadeRadiusSetting: Setting<Int>
   @JvmField
   val modelSetting: Setting<Boolean>
   @JvmField
   val xQZSetting: Setting<Boolean>
   @JvmField
   val opacitySetting: Setting<Int>
   @JvmField
   val shineSetting: Setting<Boolean>
   @JvmField
   val speedSetting: Setting<Float>
   private val strengthSetting: Setting<Float>
   @JvmField
   val progressSetting: Setting<Int>
   private val spreadSetting: Setting<Int>
   private val bloomSetting: Setting<Boolean>
   private val radiusSetting: Setting<Float>
   private val factorSetting: Setting<Float>
   private val overlapSetting: Setting<Float>
   private val animalsSetting: Setting<ChamsStyle>
   private val hostilesSetting: Setting<ChamsStyle>
   @JvmField
   val playerStyleSetting: Setting<ChamsStyle>
   @JvmField
   val selfSetting: Setting<ChamsStyle>
   @JvmField
   val crystalStyleSetting: Setting<ChamsStyle>
   private val popSetting: Setting<Boolean>
   private val wireframeSetting: Setting<ChamsWireframeChoice>
   private val extraLayerSetting: Setting<Boolean>
   private val easeSetting: Setting<Boolean>
   private val deathSetting: Setting<Boolean>
   private val animateSetting: Setting<Boolean>
   private val boostSetting: Setting<Boolean>
   private val timeSetting: Setting<Float>
   private val motionSetting: Setting<Float>
   @JvmField
   val playersSetting: Setting<Int>
   @JvmField
   val crystalsSetting: Setting<Int>
   @JvmField
   val othersSetting: Setting<Int>
   @JvmField
   val colorsSetting: Setting<Boolean>
   @JvmField
   val friendsSetting: Setting<Boolean>
   @JvmField
   val fillSetting: Setting<Color>
   @JvmField
   val outlineSetting: Setting<Color>
   @JvmField
   val shineColorSetting: Setting<Color>
   @JvmField
   internal val popFillSetting: Setting<Color>
   @JvmField
   internal val popLineSetting: Setting<Color>
   private val popGhostTimes: HashMap<SidePlayer, Long> = HashMap()

   @EventHandler
   private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
      // Иначе призраки держат ссылки на сущности/мир прошлой сессии (утечка ClientLevel).
      synchronized(this.popGhostTimes) {
         this.popGhostTimes.clear()
      }
      ChamsBufferSource.INSTANCE.clearConsumers()
   }

   override fun onDisable() {
      synchronized(this.popGhostTimes) {
         this.popGhostTimes.clear()
      }
      ChamsBufferSource.INSTANCE.clearConsumers()
   }
   private var activeGlintRelation: EntityRenderRelation? = null
   private var renderingBaseModel: Boolean = false

   private fun renderBloomMask() {
      val fillColorForMix = fillSetting.getValue()
      val outlineColorForMix = outlineSetting.getValue()
      val maskColor = ColorUtils.blend(
         fillColorForMix, outlineColorForMix, 0.5f
      )
      val blendValue = blendSetting.getValue()
      val resolvedMaskOpacity: Float
      if (blendValue) {
         resolvedMaskOpacity = 1.0f
      } else {
         val capturedFillAlpha = fillSetting.getValue().alpha
         val outlineAlpha = outlineSetting.getValue().alpha
         val fillAlpha = capturedFillAlpha
         val maximumAlpha = max(fillAlpha, outlineAlpha)
         resolvedMaskOpacity = maximumAlpha / 255.0f
      }
      val maskOpacity = resolvedMaskOpacity
      RenderUtils.clearColor(
         MaskShaderRenderer.MASK_TARGET, maskColor
      )
      val mainTarget = ClientAccess.collectiveMinecraft.mainRenderTarget
      RenderTargetUtils.setMainRenderTarget(
         MaskShaderRenderer.MASK_TARGET
      )
      EntityChamsRenderer.flush()
      val blendEnabled = blendSetting.getValue()
      if (blendEnabled) {
         RenderUtils.blitToMain(
            MaskShaderRenderer.MASK_TARGET,
            ClientRenderPipelines.BLIT_LIGHTNING
         )
      } else {
         RenderUtils.blitToMain(MaskShaderRenderer.MASK_TARGET)
      }
      val bloomRadius = radiusSetting.getValue()
      val factorValue = factorSetting.getValue()
      val bloomFactor = factorValue
      val overlapSettingSnapshot = overlapSetting
      val overlapValue = overlapSettingSnapshot.getValue()
      MaskShaderRenderer.setParameters(bloomRadius, bloomFactor, maskOpacity, overlapValue)
      MaskShaderRenderer.renderEffect("bloom_mask")
      RenderTargetUtils.setMainRenderTarget(mainTarget)
      val compositeBlendOption = blendSetting
      val compositeBlendEnabled = compositeBlendOption.getValue()
      if (compositeBlendEnabled) {
         RenderUtils.blitToMain(
            MaskShaderRenderer.MASK_TARGET,
            ClientRenderPipelines.BLIT_LIGHTNING
         )
      } else {
         RenderUtils.blitToMain(MaskShaderRenderer.MASK_TARGET)
      }
   }

   @EventHandler(priority = -1000)
   private fun onWorldRenderFinished(event: WorldRenderEvent.Main) {
      val bloomValue = bloomSetting.getValue()
      if (bloomValue || blendSetting.getValue()) {
         EntityChamsRenderer.flush()
      }
      val lineWidthValue = lineWidthSetting.getValue()
      EntityChamsRenderer.setLineWidth(lineWidthValue.toDouble())
      val entityIterator = ClientAccess.collectiveMinecraft.level!!.entitiesForRendering().iterator()
      while (entityIterator.hasNext()) {
         val entity = entityIterator.next()
         if (this.shouldRenderEntityInRange(entity)) {
            val style = getStyleSetting(this, entity).getValue() as ChamsColorProvider
            if (style.isEnabled()) {
               val modelValue = modelSetting.getValue()
               if (modelValue) {
                  val xQZValue = xQZSetting.getValue()
                  if (xQZValue) {
                     val noRenderSnapshot = noRenderModule
                     if (!noRenderSnapshot!!.shouldHideEntity(entity)) {
                        val opacityValue = opacitySetting.getValue()
                        if (opacityValue > 0) {
                           this.renderingBaseModel = true
                           val baseOpacity = opacitySetting.getValue().toFloat()
                           RenderUtils.setRenderColor(
                              1.0f, 1.0f, 1.0f, baseOpacity / 100.0f
                           )
                           val renderer = RenderUtils.INSTANCE
                           val partialTick = event.partialTick
                           val poseStackSnapshot = event.poseStack!!
                           val renderBuffers = ClientAccess.collectiveMinecraft.renderBuffers()
                           renderer.renderEntity(
                              entity, partialTick, poseStackSnapshot, renderBuffers.bufferSource()
                           )
                           this.renderingBaseModel = false
                        }
                     }
                  }
               }
               if (entity is ForcedEntityLayers) {
                  val extraLayers = globalExtraLayerSetting.getValue()
                  entity.`collective$setForcedLayers`(!extraLayers)
               }
               val colorProvider = getStyleSetting(this, entity).getValue() as ChamsColorProvider
               val colorPoses = event.poseStack!!
               colorProvider.renderEntity(this, entity, colorPoses)
            }
         }
      }
      val throughWallOption = xQZSetting
      val throughWallEnabled = throughWallOption.getValue()
      if (throughWallEnabled) {
         val wallOpacityValue = opacitySetting.getValue()
         val wallOpacity = wallOpacityValue.toFloat()
         RenderUtils.setRenderColor(
            1.0f, 1.0f, 1.0f, wallOpacity / 100.0f
         )
         RenderUtils.INSTANCE
            .flushEntities()
         RenderUtils.setRenderColor(
            1.0f, 1.0f, 1.0f, 1.0f
         )
      }
      val popSettingSnapshot = popSetting
      val popValue = popSettingSnapshot.getValue()
      if (popValue) {
         this.renderPopGhosts(event)
      }
      val shineSettingSnapshot = shineSetting
      val shineValue = shineSettingSnapshot.getValue()
      if (shineValue) {
         val glintBatchConsumer = BiConsumer<EntityRenderRelation, MeshBuffer> { relation, buffer ->
            this.renderGlintBatch(relation, buffer)
         }
         EntityRenderRelation.forEachMesh(glintBatchConsumer)
      }
      val bloomOption = bloomSetting
      val bloomEnabled = bloomOption.getValue()
      if (bloomEnabled) {
         this.renderBloomMask()
      } else {
         val blendOption = blendSetting
         val blendEnabled = blendOption.getValue()
         if (blendEnabled) {
            EntityChamsRenderer.drawMeshes(
               ClientRenderTypes.worldFillLightning(),
               ClientRenderTypes.worldLinesLightning()
            )
         }
      }
   }

   @EventHandler
   private fun onGlintTextureTransform(event: GlintTextureTransformEvent) {
      if (renderingGlintBatch) {
         val capturedSpread = spreadSetting.getValue()
         val spread = capturedSpread
         val glintScale = 6.25f * GlintModule.getSpreadMultiplier(spread)
         event.scale = glintScale
      }
   }

   private fun renderGlintBatch(relation: EntityRenderRelation, buffer: MeshBuffer) {
      if (!buffer.isEmpty()) {
         this.activeGlintRelation = relation
         val optionSnapshot = ClientAccess.collectiveMinecraft.options.glintSpeed()
         val previousSpeed = optionSnapshot.get() as Double
         val glintStrengthOption = ClientAccess.collectiveMinecraft.options.glintStrength()
         val previousStrength = glintStrengthOption.get() as Double
         var speedOption = ClientAccess.collectiveMinecraft.options.glintSpeed()
         val requestedSpeed = speedSetting.getValue().toDouble()
         GameOptionsOverrides.forceSetOptionValue(speedOption, requestedSpeed)
         val capturedStrength = strengthSetting.getValue().toFloat()
         this.updateGlobalGlintStrength(capturedStrength.toDouble())
         renderingGlintBatch = true
         MeshDrawContext.setRenderType(ClientRenderTypes.shineGlint())
         buffer.draw()
         renderingGlintBatch = false
         this.updateGlobalGlintStrength(previousStrength)
         speedOption = ClientAccess.collectiveMinecraft.options.glintSpeed()
         speedOption.set(previousSpeed)
      }
   }

   fun shouldRenderGlintForEntity(entity: Entity?): Boolean {
      if (entity == null) return false
      val style = getStyleSetting(this, entity).getValue() as ChamsColorProvider
      val usesStyleOrIsSelf = style.isEnabled() || entity === ClientAccess.collectiveMinecraft.player
      if (this.isToggled() && usesStyleOrIsSelf) {
         val shineValue = shineSetting.getValue()
         if (shineValue && renderingShineModels) {
            return true
         }
      }
      return false
   }

   fun shouldRenderEntity(entity: Entity?, checkRange: Boolean): Boolean {
      if (entity == null) return false
      if (LogoutSpotsModule.isRenderingTexturedModel()) {
         return false
      }
      var outsideFrustum: Boolean
      try {
         val entityBounds = entity.boundingBox
         outsideFrustum = !RotationManager.isVisible(entityBounds)
         val cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera.position
         if (checkRange) {
            val entityPosition = entity.position()
            val cameraDistance = cameraPosition.distanceTo(entityPosition)
            val entityType = entity.type
            if (cameraDistance > getRenderRange(this, entityType)) {
               return false
            }
         }
      } catch (unavailableEntity: Exception) {
         return false
      }
      if (outsideFrustum) {
         return false
      }
      if (EntityUtils.isFirstPersonCameraEntity(entity)) {
         return false
      }
      if (entity is EndCrystal) {
         val crystal = entity
         val crystalAgeMillis = System.currentTimeMillis() - (crystal as EndCrystalState).getSpawnTime()
         val latency = ClientContext.serverStateManager.latencyMillis
         if (crystalAgeMillis > Math.max(latency, 50)) {
            val crystalTicks = crystal.tickCount
            if (crystalTicks < 10) {
               val autoCrystalSnapshot = autoCrystalModule
               if (autoCrystalSnapshot!!.isToggled()) {
                  val instantChoice = autoCrystalModule!!.instantSetting.getValue()
                  if (instantChoice.isEnabled() &&
         (crystal as EndCrystalState).isClientAttacked()) {
                     return false
                  }
               }
            }
         }
         val styleChoice = crystalStyleSetting.getValue()
         return styleChoice.isEnabled()
      } else if (entity is Player) {
         val freecamSnapshot = freecamModule
         var selfVisibleInCamera = true
         if (!freecamSnapshot!!.isToggled()) {
            selfVisibleInCamera = ClientAccess.collectiveMinecraft.options.cameraType != CameraType.FIRST_PERSON
         }
         if (entity !== ClientAccess.collectiveMinecraft.player!!) {
            val styleChoice2 = playerStyleSetting.getValue()
            return styleChoice2.isEnabled()
         } else {
            val styleChoice3 = selfSetting.getValue()
            return styleChoice3.isEnabled() && selfVisibleInCamera
         }
      } else {
         if (entity is AgeableMob) {
            val styleChoice4 = animalsSetting.getValue()
            if (styleChoice4.isEnabled()) {
               return true
            }
         }
         if (entity is Enemy) {
            val styleChoice5 = hostilesSetting.getValue()
            if (styleChoice5.isEnabled()) {
               return true
            }
         }
         return false
      }
   }

   fun shouldRenderThroughWalls(entity: Entity?): Boolean {
      if (entity == null) {
         return false
      }
      if (this.isToggled()) {
         val xQZValue = xQZSetting.getValue()
         if (xQZValue) {
            return this.shouldRenderEntityInRange(entity)
         }
      }
      return false
   }

   fun getDistanceFade(entity: Entity?): Float {
      if (entity == null) return 1.0f
      val fadePercent = fadeRadiusSetting.getValue().toFloat()
      val fadeStart = fadePercent / 100.0f
      val cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera.position
      val entityPosition = entity.position()
      val cameraDistance = cameraPosition.distanceTo(entityPosition)
      val entityType = entity.type
      var relativeDistance = cameraDistance / getRenderRange(this, entityType)
      if (relativeDistance < fadeStart) {
         return 1.0f
      } else {
         relativeDistance -= fadeStart
         val unclampedFade = relativeDistance / (1.0f - fadeStart)
         val fadeFraction = unclampedFade
         return (1.0 - Mth.clamp(fadeFraction, 0.0, 1.0)).toFloat()
      }
   }

   private fun updateGlobalGlintStrength(strength: Double) {
      RenderSystem.setShaderGlintAlpha(strength)
   }

   @EventHandler
   private fun onRenderTypeDrawEvent(event: RenderTypeDrawEvent) {
      if (EventStage.Post != event.stage &&
         renderingGlintBatch) {
         val capturedTextureManager: TextureManager = Minecraft.getInstance().textureManager
         val shineTexture = capturedTextureManager.getTexture(SHINE_TEXTURE)
         event.override = shineTexture.getId()
      }
   }

   @EventHandler
   private fun onEntityRender(event: EntityRenderEvent.Pre) {
      if (!ClientContext.shaderRegistry.renderingIntoTarget) {
         val entity = event.entity!!
         val positionSnapshot = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera.position
         val cullingValue = cullingSetting.getValue()
         var outsideCullingRange = false
         if (cullingValue) {
            val entityPosition = entity.position()
            val cameraDistance = positionSnapshot.distanceTo(entityPosition)
            val entityType = entity.type
            outsideCullingRange = cameraDistance > getRenderRange(this, entityType)
         }
         if (outsideCullingRange) {
            if (this.shouldRenderEntity(entity, false) &&
         !this.renderingBaseModel) {
               event.cancel()
            }
         }
         val modelValue = modelSetting.getValue()
         if (!modelValue) {
            if (this.shouldRenderEntityInRange(entity) &&
         !this.renderingBaseModel) {
               event.cancel()
            }
         }
      }
   }

   @EventHandler
   private fun onPlayerPop(event: PlayerTotemEvent) {
      // PlayerTotemEvent постится из netty-потока (через PacketEvent.Receive):
      // создание SidePlayer и чтение мира — только с клиентского потока.
      ClientAccess.collectiveMinecraft.execute {
         this.handlePlayerPop(event)
      }
   }

   private fun handlePlayerPop(event: PlayerTotemEvent) {
      val popValue = popSetting.getValue()
      if (popValue) {

         val expectedKind = PlayerTotemEvent.Kind.DEATH
         if (event.kind == expectedKind) {
            val deathValue = deathSetting.getValue()
            if (!deathValue) {
               return
            }
         }
         val player = event.player
         if (player !== ClientAccess.collectiveMinecraft.player!!) {
            val popLevel = ClientAccess.collectiveMinecraft.level ?: return
            val ghost = SidePlayer(
               popLevel
            )
            ghost.copyPlayerState(player)
            var walkAnimation: WalkAnimationState = ghost.walkAnimation
            val walkPosition = ghost.walkAnimation.position
            walkAnimation.position = walkPosition * 2.0f
            val animateValue = animateSetting.getValue()
            if (!animateValue) {
               ghost.walkAnimation.position = 0.0f
               ghost.walkAnimation.speed = 0.0f
               ghost.walkAnimation.speedOld = 0.0f
            } else {
               val boostValue = boostSetting.getValue()
               if (boostValue) {
                  val boostAnimation = true
                  ghost.setAnimationBoosted(boostAnimation)
                  ghost.attackAnim = 0.0f
                  walkAnimation = ghost.walkAnimation
                  val randomSample = Math.random()
                  val scaledRandom = randomSample * 0.800000011920929
                  walkAnimation.speed = (scaledRandom + 0.20000000298023224).toFloat()
                  ghost.walkAnimation.speedOld = ghost.walkAnimation.speed
                  ghost.walkAnimation.position = 0.0f
                  val mainHand = InteractionHand.MAIN_HAND
                  val mainHandEmptyStack = ItemStack.EMPTY
                  ghost.setItemInHand(mainHand, mainHandEmptyStack)
                  val offHand = InteractionHand.OFF_HAND
                  val offHandEmptyStack = ItemStack.EMPTY
                  ghost.setItemInHand(offHand, offHandEmptyStack)
               }
            }
            synchronized(this.popGhostTimes) {
               val ghostTimes = this.popGhostTimes
               val createdAt = System.currentTimeMillis()
               ghostTimes.putIfAbsent(ghost, createdAt)
            }
         }
      }
   }

   fun shouldRenderEntityInRange(entity: Entity?): Boolean {
      if (entity == null) return false
      val checkRange = true
      return this.shouldRenderEntity(entity, checkRange)
   }

   private fun getEntityRelation(entity: Entity?): EntityRenderRelation {
      if (entity == null) return EntityRenderRelation.DEFAULT
      val friendsValue = friendsSetting.getValue()
      if (!friendsValue) {
         return EntityRenderRelation.DEFAULT
      }
      val relationships = ClientContext.socialRegistry
      if (relationships.isFriendEntity(entity)) {
         return EntityRenderRelation.FRIEND
      }
      return if (relationships.isEnemyEntity(entity)) {
         EntityRenderRelation.ENEMY
      } else {
         EntityRenderRelation.DEFAULT
      }
   }

   fun usesStaticPlayerAnimation(): Boolean {
      val animationsSnapshot = animationsModule
      return animationsSnapshot!!.isStaticPlayerAnimationEnabled()
   }

   fun getGlintColor(): Vector3f {
      val relation = this.activeGlintRelation!!
      val relationColorSetting: Setting<Color>
      when (relation) {
         EntityRenderRelation.DEFAULT -> relationColorSetting = shineColorSetting
         EntityRenderRelation.FRIEND -> relationColorSetting = colorsModule!!.friendColorSetting
         EntityRenderRelation.ENEMY -> relationColorSetting = colorsModule!!.enemyColorSetting
         else -> throw MatchException(null, null)
      }
      var colorSetting = relationColorSetting
      val friendsValue = friendsSetting.getValue()
      if (!friendsValue) {
         colorSetting = shineColorSetting
      }
      val color = colorSetting.getValue()
      val capturedColorVector = GLINT_COLOR_VECTOR
      val capturedRed = color.red.toFloat()
      val capturedGreen = color.green.toFloat()
      val blue = color.blue.toFloat()
      capturedColorVector.set(capturedRed, capturedGreen, blue)
      val colorVectorForDivide = GLINT_COLOR_VECTOR
      colorVectorForDivide.div(255.0f)
      return GLINT_COLOR_VECTOR
   }

   private fun renderPopGhosts(event: WorldRenderEvent) {
      synchronized(this.popGhostTimes) {
         this.popGhostTimes.entries.removeIf { entry ->
            val timeSettingSnapshot = timeSetting
            val timeValue = timeSettingSnapshot.getValue()
            val durationSeconds = timeValue
            val expiryTime = entry.value + (durationSeconds * 1000.0f).toLong()
            expiryTime < System.currentTimeMillis()
         }
         this.popGhostTimes.forEach { (ghost, createdAt) ->
            val extraLayerValue = extraLayerSetting.getValue()
            if (!extraLayerValue) {
               (ghost as ForcedEntityLayers).`collective$setForcedLayers`(true)
            }
            val now = System.currentTimeMillis()
            val elapsedMillis = (now - createdAt).toFloat()
            val timeSettingSnapshot = timeSetting
            val timeValue = timeSettingSnapshot.getValue()
            val durationSeconds = timeValue
            val unclampedElapsedFraction = elapsedMillis / (durationSeconds * 1000.0f)
            val elapsedFraction = unclampedElapsedFraction
            var opacity = 1.0f - Mth.clamp(elapsedFraction, 0.0f, 1.0f)
            val easeValue = easeSetting.getValue()
            if (easeValue) {
               val remainingOpacity = (1.0f - opacity).toDouble()
               val easeInput = remainingOpacity
               opacity = (1.0 - Math.pow(easeInput, 3.0)).toFloat()
            }
            val ghostForX = ghost
            val capturedGhostX = ghostForX.getX()
            val ghostForBaseY = ghost
            val baseY = ghostForBaseY.getCapturedPosition().y
            val motionProgress = 1.0f - opacity
            val motionValue = motionSetting.getValue()
            val movedY = baseY + motionProgress * motionValue
            val ghostForZ = ghost
            val ghostZ = ghostForZ.getZ()
            val ghostY = movedY
            val ghostX = capturedGhostX
            val ghostForPosition = ghost
            ghostForPosition.setPos(ghostX, ghostY, ghostZ)
            val wireframe = wireframeSetting.getValue()
            val wireframeColors = wireframe.getColors(this)
            val lineColor = wireframeColors[0]
            val lineAlpha = lineColor.alpha.toFloat()
            val fadedLineColor = ColorUtils.withAlpha(
               lineColor, lineAlpha / 255.0f * opacity
            )
            val fillColor = wireframeColors[1]
            val fillAlpha = fillColor.alpha.toFloat()
            EntityChamsRenderer.setColors(
               fadedLineColor,
               ColorUtils.withAlpha(
                  fillColor, fillAlpha / 255.0f * opacity
               )
            )
            EntityChamsRenderer.renderEntity(
               event.poseStack!!, ghost
            )
         }
      }
   }

   @EventHandler(priority = 200)
   private fun onWorldRenderStarted(event: WorldRenderEvent.BeforeBufferSource) {
      EntityCullingOverrides.disableCulling()
      val entityIterator = ClientAccess.collectiveMinecraft.level!!.entitiesForRendering().iterator()
      while (entityIterator.hasNext()) {
         val entity = entityIterator.next()
         if (this.shouldRenderEntityInRange(entity)) {
            val shineValue = shineSetting.getValue()
            if (shineValue) {
               renderingShineModels = true
               event.poseStack!!.pushPose()
               RenderUtils.setRenderColor(
                  1.0f, 1.0f, 1.0f, 1.0f
               )
               RenderUtils.applyCameraRotation(
                  event.poseStack!!
               )
               EntityChamsRenderer.setRelation(
                  this.getEntityRelation(entity)
               )
               EntityChamsRenderer.renderEntity(
                  event.poseStack!!, entity
               )
               event.poseStack!!.popPose()
               renderingShineModels = false
            }
         }
      }
      EntityCullingOverrides.restoreCulling()
   }

   init {
      this.cullingSetting = this.add(BooleanSetting("Culling", false))
      this.blendSetting = this.add(BooleanSetting("Blend", false))
      this.globalExtraLayerSetting = this.add(BooleanSetting("ExtraLayer", true))
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0f, 0.1f, 5.0f))
      val fadeRegistered = this.add(BooleanSetting("Fade", false).markVisibilityDependency())
      this.fadeSetting = fadeRegistered
      this.fadeRadiusSetting = this.add(NumberSetting<Int>("FadeRadius", 75, 1, 100).withUnit("%").dependsOn(fadeRegistered))
      val modelRegistered = this.add(BooleanSetting("Model", true).markVisibilityDependency())
      this.modelSetting = modelRegistered
      val xQZRegistered = this.add(BooleanSetting("XQZ", false).dependsOn(modelRegistered))
      this.xQZSetting = xQZRegistered
      this.opacitySetting = this.add(NumberSetting<Int>("Opacity", 100, 0, 100).withUnit("%").dependsOn(modelRegistered, xQZRegistered))
      val shineRegistered = this.add(BooleanSetting("Shine", false).markVisibilityDependency().dependsOn(modelRegistered))
      this.shineSetting = shineRegistered
      this.speedSetting = this.add(NumberSetting<Float>("Speed", 0.5f, 0.0f, 2.0f).dependsOn(modelRegistered, shineRegistered))
      this.strengthSetting = this.add(NumberSetting<Float>("Strength", 1.0f, 0.0f, 1.0f).dependsOn(modelRegistered, shineRegistered))
      val progressVisibilityPredicate = Predicate<Any> {
         this.speedSetting.isAtMinimum()
      }
      this.progressSetting = this.add(NumberSetting<Int>("Progress", 0, 0, 100, progressVisibilityPredicate).withUnit("%")
         .dependsOn(modelRegistered, shineRegistered))
      this.spreadSetting = this.add(NumberSetting<Int>("Spread", 1, 1, 5).dependsOn(modelRegistered, shineRegistered))
      val bloomRegistered = this.add(BooleanSetting("Bloom", false).markVisibilityDependency())
      this.bloomSetting = bloomRegistered
      this.radiusSetting = this.add(NumberSetting<Float>("Radius", 8.0f, 1.0f, 20.0f).dependsOn(bloomRegistered))
      this.factorSetting = this.add(NumberSetting<Float>("Factor", 1.0f, 0.5f, 2.0f).dependsOn(bloomRegistered))
      this.overlapSetting = this.add(NumberSetting<Float>("Overlap", 1.0f, 0.0f, 3.0f).dependsOn(bloomRegistered))
      val targetsRegistered = this.add(BooleanSetting("Targets", true).markAsGroup().markVisibilityDependency())
      this.animalsSetting = this.add(EnumSetting<ChamsStyle>("Animals", ChamsStyle.OFF).dependsOn(targetsRegistered))
      this.hostilesSetting = this.add(EnumSetting<ChamsStyle>("Hostiles", ChamsStyle.OFF).dependsOn(targetsRegistered))
      this.playerStyleSetting = this.add(EnumSetting<ChamsStyle>("Players", ChamsStyle.OFF).dependsOn(targetsRegistered))
      this.selfSetting = this.add(EnumSetting<ChamsStyle>("Self", ChamsStyle.OFF).dependsOn(targetsRegistered))
      this.crystalStyleSetting = this.add(EnumSetting<ChamsStyle>("Crystals", ChamsStyle.OFF).dependsOn(targetsRegistered))
      val popRegistered = this.add(BooleanSetting("Pop", false).markVisibilityDependency().dependsOn(targetsRegistered))
      this.popSetting = popRegistered
      this.wireframeSetting = this.add(EnumSetting<ChamsWireframeChoice>("Wireframe", ChamsWireframeChoice.BOTH).dependsOn(targetsRegistered, popRegistered))
      this.extraLayerSetting = this.add(BooleanSetting("ExtraLayer", false).dependsOn(targetsRegistered, popRegistered))
      this.easeSetting = this.add(BooleanSetting("Ease", false).dependsOn(targetsRegistered, popRegistered))
      this.deathSetting = this.add(BooleanSetting("Death", false).dependsOn(targetsRegistered, popRegistered))
      val animateRegistered = this.add(BooleanSetting("Animate", true).dependsOn(targetsRegistered, popRegistered))
      this.animateSetting = animateRegistered
      this.boostSetting = this.add(BooleanSetting("Boost", false).dependsOn(targetsRegistered, popRegistered, animateRegistered))
      this.timeSetting = this.add(NumberSetting<Float>("Time", 1.0f, 0.0f, 5.0f).dependsOn(targetsRegistered, popRegistered))
      this.motionSetting = this.add(NumberSetting<Float>("Motion", 0.0f, -8.0f, 8.0f).dependsOn(targetsRegistered, popRegistered))
      val rangesRegistered = this.add(BooleanSetting("Ranges", false).markAsGroup().markVisibilityDependency())
      this.playersSetting = this.add(NumberSetting<Int>("Players", 60, 1, 300).withUnit("m").dependsOn(rangesRegistered))
      this.crystalsSetting = this.add(NumberSetting<Int>("Crystals", 60, 1, 300).withUnit("m").dependsOn(rangesRegistered))
      this.othersSetting = this.add(NumberSetting<Int>("Others", 60, 1, 300).withUnit("m").dependsOn(rangesRegistered))
      val colorsRegistered = this.add(BooleanSetting("Colors", false).markAsGroup().markVisibilityDependency())
      this.colorsSetting = colorsRegistered
      this.friendsSetting = this.add(BooleanSetting("Friends", false).dependsOn(colorsRegistered))
      this.fillSetting = this.add(ColorSetting("Fill", Color(935172607, true)).dependsOn(colorsRegistered))
      this.outlineSetting = this.add(ColorSetting("Outline", Color(-4351489, true)).dependsOn(colorsRegistered))
      this.shineColorSetting = this.add(ColorSetting("Shine", Color(2076023295, true)).dependsOn(colorsRegistered))
      this.popFillSetting = this.add(ColorSetting("PopFill", Color(935172607, true)).dependsOn(colorsRegistered))
      this.popLineSetting = this.add(ColorSetting("PopLine", Color(12425727, true)).dependsOn(colorsRegistered))
      this.playersSetting.setConfigName("PlayerRange")
      this.crystalsSetting.setConfigName("CrystalRange")
      this.othersSetting.setConfigName("OtherRange")
      this.globalExtraLayerSetting.setConfigName("ExtraLayerGlobal")
      this.wireframeSetting.setConfigName("PopMode")
      this.shineColorSetting.setConfigName("ShineColor")
      this.spreadSetting.setConfigName("ShineSpread")
      this.speedSetting.setBoundaryLabel("Static", SettingLabelCondition.MIN)
   }

}
