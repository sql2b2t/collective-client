package me.collective.client.utils.entity

import me.collective.client.ClientAccess
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.combat.ZombifiedPiglinAttackState
import me.collective.mixin.FireworkRocketEntityAccessor
import java.util.HashSet
import java.util.function.Predicate
import net.minecraft.client.CameraType
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.ClientAsset.ResourceTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.dolphin.Dolphin
import net.minecraft.world.entity.animal.fish.AbstractFish
import net.minecraft.world.entity.animal.polarbear.PolarBear
import net.minecraft.world.entity.animal.squid.Squid
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.entity.animal.wolf.WolfVariant
import net.minecraft.world.entity.animal.wolf.WolfVariant.AssetInfo
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.piglin.AbstractPiglin
import net.minecraft.world.entity.monster.piglin.PiglinArmPose
import net.minecraft.world.entity.monster.piglin.PiglinBrute
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class EntityUtils private constructor() : ClientAccess {
   companion object {

      @JvmStatic
      fun getFireworkPositions(): Set<BlockPos> {
         val positions = HashSet<BlockPos>()
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return positions
         val iterator = level.entitiesForRendering().iterator()

         while (iterator.hasNext()) {
            val entity = iterator.next()
            if (entity is FireworkRocketEntity) {
               positions.add(entity.blockPosition())
            }
         }

         return positions
      }

      @JvmStatic
      fun isHostile(entity: Entity): Boolean {
         if (entity is EnderMan) {
            return entity.isCreepy()
         }
         if (entity is ZombifiedPiglinAttackState) {
            return entity.`collective$isAttacking`()
         }
         if (entity is Wolf) {
            val currentTexture: Identifier = entity.getTexture()
            val variantHolder: Holder<WolfVariant> = entity.getVariant()
            val variant = variantHolder.value()
            val assets: AssetInfo = variant.assetInfo()
            val angryTexture: ResourceTexture = assets.angry()
            return currentTexture === angryTexture.texturePath()
         }
         if (entity is NeutralMob) {
            return entity.isAngry()
         }
         if (entity !is AbstractPiglin) {
            return entity is PolarBear || entity is Enemy
         }
         if (entity is PiglinBrute) {
            return true
         }

         if (entity.isBaby()) {
            return false
         }

         val armPose = entity.getArmPose()
         return armPose === PiglinArmPose.CROSSBOW_CHARGE || armPose === PiglinArmPose.CROSSBOW_HOLD || armPose === PiglinArmPose.ATTACKING_WITH_MELEE_WEAPON
      }

      @JvmStatic
      fun isNeutral(entity: Entity): Boolean {
         return entity is NeutralMob || entity is AbstractPiglin && entity !is PiglinBrute
      }

      @JvmStatic
      fun isLookingAtPlayer(entity: Entity): Boolean {
         if (entity !is LivingEntity) {
            return false
         }
         val livingEntity = entity

         val eyePosition: Vec3 = livingEntity.getEyePosition()
         val pitch: Float = livingEntity.getXRot()
         val headYaw: Float = livingEntity.getYHeadRot()
         val viewDirection: Vec3 = livingEntity.calculateViewVector(pitch, headYaw)
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         val playerAlignment = getViewAlignment(eyePosition, viewDirection, player)
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return false
         val bounds: AABB = livingEntity.getBoundingBox()
         val searchBounds: AABB = bounds.inflate(16.0)
         val otherLivingEntities = Predicate<Entity> { candidate ->
            candidate is LivingEntity &&
         candidate !== ClientAccess.collectiveMinecraft.player!! && candidate.isAlive()
         }
         for (nearby in level.getEntities(livingEntity, searchBounds, otherLivingEntities)) {
            if (getViewAlignment(eyePosition, viewDirection, nearby) > playerAlignment) {
               return false
            }
         }

         return playerAlignment > MathConstants.HALF
      }

      private fun getViewAlignment(eyePosition: Vec3, viewDirection: Vec3, entity: Entity): Double {
         val toEntity = Vec3(entity.getX(), entity.getEyeY(), entity.getZ()).subtract(eyePosition)
         if (toEntity.lengthSqr() < 1.0E-4) {
            return 1.0
         }

         return viewDirection.dot(toEntity.normalize())
      }

      @JvmStatic
      fun getPlayerHealthWithAbsorption(): Float {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return 0.0f
         return getHealthWithAbsorption(player)
      }

      @JvmStatic
      fun getHealthFraction(entity: LivingEntity): Float {
         var healthFraction = getHealthWithAbsorption(entity)
         healthFraction /= entity.getMaxHealth()
         val maximum = 1.0F
         return Mth.clamp(healthFraction, 0.0F, maximum)
      }

      @JvmStatic
      fun isPassive(entity: Entity): Boolean {
         return entity is AbstractFish || entity is AgeableMob || entity is Squid || entity is Dolphin || entity is Villager
      }

      @JvmStatic
      fun hasCollisionBelow(entity: LivingEntity): Boolean {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return false
         val boundsSnapshot: AABB = entity.getBoundingBox()
         val expandedBounds: AABB = boundsSnapshot.expandTowards(0.0, -0.1, 0.0)
         return !level.noBlockCollision(entity, expandedBounds)
      }

      @JvmStatic
      fun setStepHeight(entity: LivingEntity, height: Float) {
         val stepHeightAttribute: Holder<net.minecraft.world.entity.ai.attributes.Attribute> = Attributes.STEP_HEIGHT
         val attributeSnapshot: AttributeInstance = entity.getAttribute(stepHeightAttribute) ?: return
         attributeSnapshot.setBaseValue(height.toDouble())
      }

      @JvmStatic
      fun isFirstPersonCameraEntity(entity: Entity): Boolean {
         val options = ClientAccess.collectiveMinecraft.options
         if (options.getCameraType() !== CameraType.FIRST_PERSON) {
            return false
         }

         if (entity === ClientAccess.collectiveMinecraft.player!!) {
            return false
         }

         return ClientAccess.collectiveMinecraft.getCameraEntity() === entity
      }

      @JvmStatic
      fun hasFirework(shooter: Entity): Boolean {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return false
         val iterator = level.entitiesForRendering().iterator()

         while (iterator.hasNext()) {
            val entity = iterator.next()
            if (entity is FireworkRocketEntityAccessor && entity.`collective$getShooter`() === shooter) {
               return true
            }
         }

         return false
      }

      @JvmStatic
      fun getHealthWithAbsorption(entity: Entity): Float {
         if (entity is LivingEntity) {
            val health: Float = entity.getHealth()
            return health + entity.getAbsorptionAmount()
         }
         return 2.0F
      }

      @JvmStatic
      fun getStepHeight(entity: LivingEntity): Float {
         val stepHeightAttribute: Holder<net.minecraft.world.entity.ai.attributes.Attribute> = Attributes.STEP_HEIGHT
         val attribute: AttributeInstance = entity.getAttribute(stepHeightAttribute) ?: return 0.0f
         return attribute.getValue().toFloat()
      }
   }
}
