package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.combat.AutoBowReleaseModule

import java.util.function.Predicate
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceKey
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.entity.projectile.FishingHook
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.entity.projectile.ThrowableProjectile
import net.minecraft.world.entity.projectile.ThrownTrident
import net.minecraft.world.entity.projectile.Snowball
import net.minecraft.world.entity.projectile.ThrownEgg
import net.minecraft.world.entity.projectile.ThrownEnderpearl
import net.minecraft.world.entity.projectile.ThrownExperienceBottle
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.EggItem
import net.minecraft.world.item.EnderpearlItem
import net.minecraft.world.item.ExperienceBottleItem
import net.minecraft.world.item.FishingRodItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.SnowballItem
import net.minecraft.world.item.ThrowablePotionItem
import net.minecraft.world.item.TridentItem
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Vector3d

class ProjectileTrajectorySimulator : ClientAccess {
   companion object {
      private var module: TrajectoriesModule? = null
      private var autoBowModule: AutoBowReleaseModule? = null
      private val SIMULATION_LIMIT: HitResult = object : HitResult(
         Vec3.ZERO
      ) {
         override fun getType(): HitResult.Type {
            return HitResult.Type.MISS
         }
      }

      private fun finite(vector: Vec3): Boolean {
         return vector.x.isFinite() && vector.y.isFinite() && vector.z.isFinite()
      }

      @JvmStatic
      fun bindModules(registry: ModuleRegistry) {
         module = registry.getModule(
            TrajectoriesModule::class.java
         ) as TrajectoriesModule?
         autoBowModule = registry.getModule(
            AutoBowReleaseModule::class.java
         ) as AutoBowReleaseModule?
      }
   }

   private val fluidPosition = MutableBlockPos()
   private var segmentEnd: Vec3 = Vec3.ZERO
   private var segmentStart: Vec3 = Vec3.ZERO
   private var sourceEntity: Entity? = null
   private var ignoredCollisionOwner: Entity? = null
   @JvmField
   val position: Vector3d = Vector3d()
   private val velocity: Vector3d = Vector3d()
   private var gravity: Double = 0.0
   private var airDrag: Double = 0.0
   private var waterDrag: Double = 0.0

   fun isEnabledProjectileItem(item: Item): Boolean {
      if (item is BowItem) return module!!.bowSetting.value
      if (item is CrossbowItem) return module!!.crossbowSetting.value
      if (item is TridentItem) return module!!.tridentSetting.value
      if (item is EnderpearlItem) return module!!.pearlsSetting.value
      if (item is ExperienceBottleItem) return module!!.expSetting.value
      return (item is FishingRodItem || item is ThrowablePotionItem || item is SnowballItem || item is EggItem) &&
         module!!.othersSetting.value
   }


   fun initializeFromProjectile(entity: Entity, partialTick: Double): Boolean {
      if (entity !is Projectile) return false
      val movement = entity.deltaMovement
      if (movement.lengthSqr() < 1.0e-10 || !finite(movement)) return false
      val level = ClientAccess.collectiveMinecraft.level!!
      if (level == null || !level.noCollision(entity, entity.boundingBox.deflate(1.0e-4))) return false
      val start = entity.getPosition(partialTick.toFloat())
      this.position.set(start.x, start.y, start.z)
      this.velocity.set(movement.x, movement.y, movement.z)
      this.sourceEntity = entity
      this.ignoredCollisionOwner = entity.owner
      this.airDrag = 0.99
      if (entity is ThrownTrident) {
         this.gravity = 0.05
         this.waterDrag = 0.99
      } else if (entity is AbstractArrow) {
         this.gravity = 0.05
         this.waterDrag = 0.6
      } else if (entity is ThrownExperienceBottle) {
         this.gravity = 0.07
         this.waterDrag = 0.8
      } else if (entity is ThrownEnderpearl || entity is Snowball || entity is ThrownEgg) {
         this.gravity = 0.03
         this.waterDrag = 0.8
      } else if (entity is FireworkRocketEntity) {
         this.gravity = 0.0
         this.airDrag = 1.0
         this.waterDrag = 1.0
      } else if (entity is FishingHook) {
         this.gravity = 0.03
         this.airDrag = 0.92
         this.waterDrag = 0.0
      } else if (entity is ThrowableProjectile) {
         this.gravity = 0.05
         this.waterDrag = 0.8
      } else if (entity is AbstractHurtingProjectile) {
         this.gravity = 0.0
         this.airDrag = 0.95
         this.waterDrag = 0.8
      } else return false
      if (entity.isNoGravity) this.gravity = 0.0
      return true
   }

   fun initializeLaunch(
      entity: Entity, pitchOffset: Double, speed: Double, spreadAngle: Double, gravityPerTick: Double, dragInWater: Double, addEntityVelocity: Boolean, partialTick: Double
   ) {
      this.sourceEntity = entity
      this.ignoredCollisionOwner = entity
      this.position.set(Mth.lerp(partialTick, entity.xo, entity.x),
         Mth.lerp(partialTick, entity.yo, entity.y) + entity.getEyeHeight(entity.pose).toDouble(),
         Mth.lerp(partialTick, entity.zo, entity.z))
      val yaw = entity.getViewYRot(partialTick.toFloat()).toDouble()
      val pitch = entity.getViewXRot(partialTick.toFloat()).toDouble()
      val directionX: Double
      val directionY: Double
      val directionZ: Double
      if (spreadAngle == 0.0) {
         val yawRadians = yaw * MathConstants.RADIANS_PER_DEGREE.toDouble()
         val pitchRadians = pitch * MathConstants.RADIANS_PER_DEGREE.toDouble()
         directionX = -Math.sin(yawRadians) * Math.cos(pitchRadians)
         directionY = -Math.sin((pitch + pitchOffset) * MathConstants.RADIANS_PER_DEGREE.toDouble())
         directionZ = Math.cos(yawRadians) * Math.cos(pitchRadians)
      } else {
         val upVector = entity.getUpVector(1.0F)
         val spreadRotation = Quaterniond().setAngleAxis(spreadAngle, upVector.x, upVector.y, upVector.z)
         val viewVector = entity.getViewVector(1.0F)
         val rotatedDirection = Vector3d(viewVector.x, viewVector.y, viewVector.z)
         rotatedDirection.rotate(spreadRotation)
         directionX = rotatedDirection.x
         directionY = rotatedDirection.y
         directionZ = rotatedDirection.z
      }
      this.velocity.set(directionX, directionY, directionZ).normalize().mul(speed)
      if (addEntityVelocity) {
         var movement = entity.deltaMovement
         if (entity === ClientAccess.collectiveMinecraft.player!!) {
            val previous = module!!.previousPlayerVelocity
            movement = Vec3(Mth.lerp(partialTick, previous.x, movement.x),
               Mth.lerp(partialTick, previous.y, movement.y), Mth.lerp(partialTick, previous.z, movement.z))
         }
         this.velocity.add(movement.x, if (entity.onGround()) 0.0 else movement.y, movement.z)
      }
      this.gravity = gravityPerTick
      this.airDrag = 0.99
      this.waterDrag = dragInWater
   }

   fun initializeFromItem(
      entity: Entity, stack: ItemStack, spreadAngle: Double, addEntityVelocity: Boolean, partialTick: Double
   ): Boolean {
      val item = stack.item
      if (item is TridentItem) {
         val riptideKey: ResourceKey<net.minecraft.world.item.enchantment.Enchantment> = Enchantments.RIPTIDE
         if (EnchantmentUtils.hasEnchantment(riptideKey, stack)) {
            return false
         }
      }
      if (item is BowItem) {
         val configuredBow = module!!.bowSetting.value
         if (configuredBow) {
            if (entity !is LivingEntity) return false
            val currentPower = BowItem.getPowerForTime(entity.ticksUsingItem).toDouble()
            val previousPower = if (entity === ClientAccess.collectiveMinecraft.player!!) module!!.getPreviousBowPower().toDouble() else currentPower
            var bowPower = Mth.lerp(partialTick, previousPower, currentPower)
            if (bowPower <= 0.0) {
               bowPower = if (autoBowModule!!.isToggled()) BowItem.getPowerForTime(autoBowModule!!.releaseTicks).toDouble() else 1.0
            }
            val bowSpeed = bowPower * 3.0
            this.initializeLaunch(
               entity, 0.0, bowSpeed, spreadAngle, 0.05000000074505806, 0.6, addEntityVelocity, partialTick
            )
            return true
         }
      }
      if (item is CrossbowItem) {
         val configuredCrossbow = module!!.crossbowSetting.value
         if (configuredCrossbow) {
            if (!CrossbowItem.isCharged(stack)) {
               return false
            }
            val chargedProjectiles = stack.get(DataComponents.CHARGED_PROJECTILES)
            if (chargedProjectiles == null) {
               return false
            }
            val fireworkItem = Items.FIREWORK_ROCKET
            if (chargedProjectiles.contains(fireworkItem)) {
               val fireworkSpeed = CrossbowItem.getShootingPower(chargedProjectiles).toDouble()
               this.initializeLaunch(
                  entity, 0.0, fireworkSpeed, spreadAngle, 0.0, 0.6, addEntityVelocity, partialTick
               )
            } else {
               val crossbowSpeed = CrossbowItem.getShootingPower(chargedProjectiles).toDouble()
               this.initializeLaunch(
                  entity, 0.0, crossbowSpeed, spreadAngle, 0.05000000074505806, 0.6, addEntityVelocity, partialTick
               )
            }
            return true
         }
      }
      if (item is FishingRodItem) {
         val configuredOthers = module!!.othersSetting.value
         if (configuredOthers) {
            this.initializeFishingRod(entity, partialTick)
            return true
         }
      }
      if (item is TridentItem) {
         val configuredTrident = module!!.tridentSetting.value
         if (configuredTrident) {
            this.initializeLaunch(
               entity, 0.0, 2.5, spreadAngle, 0.05000000074505806, 0.99, addEntityVelocity, partialTick
            )
            return true
         }
      }
      if (((item is SnowballItem || item is EggItem) && module!!.othersSetting.value) ||
         (item is EnderpearlItem && module!!.pearlsSetting.value)) {
         this.initializeLaunch(
            entity, 0.0, 1.5, spreadAngle, 0.03, 0.8, addEntityVelocity, partialTick
         )
         return true
      }
      if (item is ExperienceBottleItem) {
         val configuredExp = module!!.expSetting.value
         if (configuredExp) {
            this.initializeLaunch(
               entity, -20.0, 0.7, spreadAngle, 0.07, 0.8, addEntityVelocity, partialTick
            )
            return true
         }
      }
      if (item !is ThrowablePotionItem) {
         return false
      }
      val configuredOthers3 = module!!.othersSetting.value
      if (!configuredOthers3) {
         return false
      }
      val potionSpeed = MathConstants.HALF
      this.initializeLaunch(
         entity, -20.0, potionSpeed, spreadAngle, 0.05, 0.8, addEntityVelocity, partialTick
      )
      return true
   }

   fun initializeFishingRod(entity: Entity, partialTick: Double) {
      this.sourceEntity = entity
      this.ignoredCollisionOwner = entity
      val yaw = Mth.lerp(partialTick, entity.yRotO.toDouble(), entity.yRot.toDouble())
      val pitch = Mth.lerp(partialTick, entity.xRotO.toDouble(), entity.xRot.toDouble())
      val yawRadians = -yaw * MathConstants.RADIANS_PER_DEGREE.toDouble() - MathConstants.PI.toFloat().toDouble()
      val yawCosine = Math.cos(yawRadians)
      val yawSine = Math.sin(yawRadians)
      val pitchRadians = -pitch * MathConstants.RADIANS_PER_DEGREE.toDouble()
      val pitchCosine = -Math.cos(pitchRadians)
      val pitchSine = Math.sin(pitchRadians)
      this.position.set(Mth.lerp(partialTick, entity.xo, entity.x),
         Mth.lerp(partialTick, entity.yo, entity.y) + entity.getEyeHeight(entity.pose).toDouble(),
         Mth.lerp(partialTick, entity.zo, entity.z))
      this.position.sub(yawSine * 0.3, 0.0, yawCosine * 0.3)
      this.velocity.set(-yawSine, Mth.clamp(-(pitchSine / pitchCosine), -5.0, 5.0), -yawCosine)
      val velocityScale = 0.6 / this.velocity.length() + MathConstants.ONE_HALF
      this.velocity.mul(velocityScale)
      this.gravity = 0.03
      this.airDrag = 0.92
      this.waterDrag = 0.0
   }


   fun step(): HitResult? {
      this.segmentStart = Vec3(this.position.x, this.position.y, this.position.z)
      this.position.add(this.velocity)
      if (!this.position.x.isFinite() || !this.position.y.isFinite() || !this.position.z.isFinite()) {
         return SIMULATION_LIMIT
      }
      val level = ClientAccess.collectiveMinecraft.level!!
      if (level == null || this.position.y < level.minY) return SIMULATION_LIMIT
      val chunkX = Mth.floor(this.position.x) shr 4
      val chunkZ = Mth.floor(this.position.z) shr 4
      if (!level.chunkSource.hasChunk(chunkX, chunkZ)) return SIMULATION_LIMIT
      this.segmentEnd = Vec3(this.position.x, this.position.y, this.position.z)
      val hit = this.findCollision()
      this.velocity.mul(if (this.isInWater()) this.waterDrag else this.airDrag)
      this.velocity.y -= this.gravity
      return if (hit.type == HitResult.Type.MISS) null else hit
   }

   private fun findCollision(): HitResult {
      val level = ClientAccess.collectiveMinecraft.level!!
      val blockHit: BlockHitResult = level!!.clip(ClipContext(this.segmentStart, this.segmentEnd, ClipContext.Block.COLLIDER,
         if (this.waterDrag == 0.0) ClipContext.Fluid.ANY else ClipContext.Fluid.NONE, this.sourceEntity!!))
      val end = if (blockHit.type == HitResult.Type.MISS) this.segmentEnd else blockHit.location
      val searchBounds = AABB(this.segmentStart, end).inflate(1.0)
      val targets = Predicate<Entity> { entity: Entity ->
         entity !== this.sourceEntity && entity !== this.ignoredCollisionOwner &&
         !entity.isSpectator && entity.isAlive && entity.isPickable
      }
      val entityHit: EntityHitResult? = ProjectileUtil.getEntityHitResult(level, this.sourceEntity!!,
         this.segmentStart, end, searchBounds, targets, 0.3F)
      return if (entityHit == null) blockHit else entityHit
   }

   private fun isInWater(): Boolean {
      this.fluidPosition.set(this.position.x, this.position.y, this.position.z)
      val level = ClientAccess.collectiveMinecraft.level!!
      val fluid: FluidState = level!!.getFluidState(this.fluidPosition)
      if (!fluid.`is`(FluidTags.WATER)) return false
      val fractionalY = this.position.y - Math.floor(this.position.y)
      return fractionalY <= fluid.getHeight(level, this.fluidPosition)
   }
}
