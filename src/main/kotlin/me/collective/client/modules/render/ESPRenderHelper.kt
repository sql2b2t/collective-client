package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.WorldRenderEvent
import me.collective.client.managers.BlockColorRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.render.RenderUtils
import me.collective.client.render.FontRenderer
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.settings.options.ESPModeChoice
import me.collective.client.utils.math.MathUtils
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.awt.Color
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.HashMap
import java.util.UUID
import java.util.concurrent.TimeUnit
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity
import net.minecraft.world.level.block.entity.DispenserBlockEntity
import net.minecraft.world.level.block.entity.EnderChestBlockEntity
import net.minecraft.world.level.block.entity.HopperBlockEntity
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.MapColor.Brightness
import net.minecraft.world.phys.Vec3

class ESPRenderHelper(private val module: ESPModule) : ClientAccess {
   private val ownerNames: HashMap<UUID, String?> = HashMap()

   fun hasStorageTargets(): Boolean {
      return this.module.chestsSetting.getValue()!!
         || this.module.enderChestsSetting.getValue()!!
         || this.module.shulkersSetting.getValue()!!
         || this.module.bedsSetting.getValue()!!
         || this.module.signsSetting.getValue()!!
         || this.module.dispensersSetting.getValue()!!
         || this.module.hoppersSetting.getValue()!!
         || this.module.furnacesSetting.getValue()!!
   }

   internal fun getOwnerName(entity: Entity): String? {
      var ownerUuid: UUID? = null
      if (entity is ThrownEnderpearl) {
         val pearl = entity
         if (pearl.getOwner() != null) {
            return pearl.getOwner()!!.getName().getString()
         }
      }
      if (entity is TamableAnimal) {
         val tamedAnimal = entity
         if (tamedAnimal.getOwnerReference() != null) {
            ownerUuid = tamedAnimal.getOwnerReference()!!.getUUID()
         }
      }
      if (ownerUuid != null) {
         val ownerPlayer = ClientAccess.collectiveMinecraft.level!!.getPlayerByUUID(ownerUuid)
         if (ownerPlayer != null) {
            val ownerName = ownerPlayer.getGameProfile().name()
            synchronized(this.ownerNames) {
               this.ownerNames.putIfAbsent(ownerUuid, ownerName)
            }
            return ownerName
         }
         synchronized(this.ownerNames) {
            if (this.ownerNames.containsKey(ownerUuid)) {
               return this.ownerNames[ownerUuid]
            }
            this.ownerNames[ownerUuid] = null
         }
         val requestedUuid = ownerUuid
         ClientAccess.executorService.submit { this.fetchOwnerName(requestedUuid) }
      }
      return null
   }

   internal fun drawLabel(
      event: WorldRenderEvent, text: String, position: Vec3, scale: Float, textColor: Color, backgroundColor: Color?
   ) {
      val cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
      val distanceScale = MathUtils.getDistanceScale(cameraPosition, position, scale.toDouble())
      if (backgroundColor != null) {
         RenderUtils.INSTANCE.drawWorldRect(
            event.poseStack!!,
            position,
            0.0F,
            0.0F,
            FontRenderer.INSTANCE
               .getWidth(text),
            FontRenderer.INSTANCE
               .getHeight().toFloat(),
            distanceScale,
            backgroundColor
         )
      }
      RenderUtils.INSTANCE.drawWorldText(
         event.poseStack!!, text, position, 0.0F, 0.0F, distanceScale, textColor, true
      )
   }

   private fun fetchOwnerName(ownerUuid: UUID) {
      try {
         HttpClient.newHttpClient().use { httpClient ->
            var requestBuilder: HttpRequest.Builder = HttpRequest.newBuilder()
            requestBuilder = requestBuilder.uri(URI(
               "https://sessionserver.mojang.com/session/minecraft/profile/" + ownerUuid.toString().replace("-", "")
            ))
            val request: HttpRequest = requestBuilder.timeout(Duration.of(3L, ChronoUnit.SECONDS)).GET().build()
            val profileJson: JsonObject = JsonParser.parseString(
               httpClient.sendAsync(request, BodyHandlers.ofString()).orTimeout(15L, TimeUnit.SECONDS).join().body()
            ).getAsJsonObject()
            if (profileJson.has("name")) {
               synchronized(this.ownerNames) {
                  this.ownerNames[ownerUuid] = profileJson.get("name").getAsString()
               }
            }
         }
      } catch (ignoredLookupFailure: Exception) {
      }
   }

   internal fun shouldRenderBlockEntity(blockEntity: BlockEntity?): Boolean {
      if (blockEntity == null) {
         return false
      }
      return blockEntity is ChestBlockEntity && this.module.chestsSetting.getValue()!!
         || blockEntity is BarrelBlockEntity && this.module.barrelsSetting.getValue()!!
         || blockEntity.blockState.block is BedBlock && this.module.bedsSetting.getValue()!!
         || blockEntity is EnderChestBlockEntity && this.module.enderChestsSetting.getValue()!!
         || blockEntity is ShulkerBoxBlockEntity && this.module.shulkersSetting.getValue()!!
         || blockEntity is AbstractFurnaceBlockEntity && this.module.furnacesSetting.getValue()!!
         || blockEntity is DispenserBlockEntity && this.module.dispensersSetting.getValue()!!
         || blockEntity is HopperBlockEntity && this.module.hoppersSetting.getValue()!!
         || blockEntity is SignBlockEntity && this.module.signsSetting.getValue()!!
         || blockEntity is DecoratedPotBlockEntity && this.module.potsSetting.getValue()!!
   }

   internal fun getBaseEntityColor(entity: Entity): Color {
      if (entity is Player) {
         if (this.module.socialSetting.getValue()!!) {
            if (ClientContext.socialRegistry.isFriendEntity(entity)) {
               return ClientContext.socialRegistry.getFriendColor()
            }
            if (ClientContext.socialRegistry.isEnemyEntity(entity)) {
               return ClientContext.socialRegistry.getEnemyColor()
            }
         }
         return this.module.playerColorSetting.getValue()!!
      }
      if (entity is ThrownExperienceBottle) return this.module.experienceColorSetting.getValue()!!
      if (entity is ItemEntity) return this.module.itemColorSetting.getValue()!!
      if (EntityUtils.isHostile(entity)) return this.module.hostileColorSetting.getValue()!!
      if (EntityUtils.isPassive(entity)) return this.module.animalColorSetting.getValue()!!
      return Color.white
   }

   internal fun getEntityColor(entity: Entity, fill: Boolean): Color {
      if (entity is ThrownEnderpearl) {
         return (if (fill) this.module.pearlFillSetting else this.module.pearlOutlineSetting).getValue()!!
      }
      val color = ARGB.color((if (fill) this.module.entityFillSetting else this.module.entityOutlineSetting).getValue(), this.getBaseEntityColor(entity).getRGB())
      val age = Mth.clamp((entity.tickCount + RenderUtils.getPartialTick()) / 12.0F, 0.0F, 1.0F)
      val opacity = age * age * (3.0F - 2.0F * age)
      val alpha = if (entity is ThrownExperienceBottle) ARGB.alpha(color) else (opacity * ARGB.alpha(color)).toInt()
      return Color(ARGB.color(alpha, color), true)
   }

   internal fun shouldRenderEntity(entity: Entity?): Boolean {
      if (entity == null || entity.getBoundingBox() == null) {
         return false
      }
      if (!RotationManager.isVisible(entity.getBoundingBox().inflate(0.1))) {
         return false
      }
      val cameraPosition = ClientAccess.collectiveMinecraft.gameRenderer.mainCamera().position()
      if (entity is ItemEntity) {
         val itemDistance = entity.position().distanceTo(cameraPosition)
         val configuredRange: Int = this.module.rangeSetting.getValue()!!
         if (itemDistance > configuredRange) {
            return false
         }
      }
      if (entity is Player && this.module.playersSetting.getValue()!!
         && entity !== ClientAccess.collectiveMinecraft.player!!) {
         return true
      }
      if (entity is ItemEntity && this.module.itemDrawSetting.getValue()!!
         && this.module.modeSetting.getValue()!! != ESPModeChoice.TEXT) {
         return true
      }
      if (entity is ThrownExperienceBottle && this.module.experienceSetting.getValue()!!) {
         return true
      }
      if (EntityUtils.isPassive(entity) && this.module.animalsSetting.getValue()!!) {
         return true
      }
      if (EntityUtils.isHostile(entity) && this.module.hostilesSetting.getValue()!!) {
         return true
      }
      return entity is ThrownEnderpearl && this.module.entityModeSetting.getValue() == ESPModule.EntityMode.BOX &&
         this.module.pearlsSetting.getValue()!!
   }

   internal fun getBlockEntityColor(blockEntity: BlockEntity): Color {
      val levelSnapshot: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val blockPosition: BlockPos = blockEntity.getBlockPos()
      val blockState: BlockState = levelSnapshot.getBlockState(blockPosition)
      val blockColorManager: BlockColorRegistry = ClientContext.blockColorRegistry
      val customBlockColor: Color? = blockColorManager.getColor(blockState.getBlock())
      if (customBlockColor != null) {
         return customBlockColor
      }
      val mapLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val mapPosition: BlockPos = blockEntity.getBlockPos()
      val stateMapColor: MapColor = blockState.getMapColor(mapLevel, mapPosition)
      var mapArgb = stateMapColor.calculateARGBColor(Brightness.HIGH)
      if (blockEntity is ShulkerBoxBlockEntity) {
         return Color(163, 64, 163)
      }
      if (blockEntity is EnderChestBlockEntity) {
         return Color(64, 0, 64)
      }
      if (blockEntity.blockState.block is BedBlock) {
         val bed = blockEntity.blockState.block as BedBlock
         val dyeMapColor = bed.getColor().getMapColor()
         mapArgb = dyeMapColor.calculateARGBColor(Brightness.HIGH)
      }
      return Color(mapArgb, false).brighter()
   }


}
