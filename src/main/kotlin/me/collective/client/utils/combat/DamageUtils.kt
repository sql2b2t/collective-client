package me.collective.client.utils.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.combat.AutoCrystalModule
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.utils.inventory.EquipmentUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.math.Vec3Utils

import java.util.Collections
import net.minecraft.core.BlockPos
import net.minecraft.tags.DamageTypeTags
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.Difficulty
import net.minecraft.world.damagesource.CombatRules
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.ClipContext.Block
import net.minecraft.world.level.ClipContext.Fluid
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult.Type
import net.minecraft.world.phys.Vec3

class DamageUtils private constructor() : ClientAccess {
   companion object {
      @JvmStatic
      fun getCrystalDamage(explosionPosition: Vec3, target: LivingEntity): Float {
         val bounds = target.boundingBox
         return getCrystalDamage(explosionPosition, target, bounds)
      }

      @JvmStatic
      fun getCrystalDamage(
         explosionPosition: Vec3, target: LivingEntity, bounds: AABB
      ): Float {
         val ignoredPositions: List<Any> = Collections.emptyList()

         return getExplosionDamage(explosionPosition, target, bounds, 6.0, true, ignoredPositions, null)
      }

      @JvmStatic
      fun getBedDamage(explosionPosition: Vec3, target: LivingEntity): Float {
         val bounds = target.boundingBox
         return getBedDamage(explosionPosition, target, bounds)
      }

      @JvmStatic
      fun getBedDamage(
         explosionPosition: Vec3, target: LivingEntity, bounds: AABB
      ): Float {
         val ignoredPositions: List<Any> = Collections.emptyList()

         return getExplosionDamage(explosionPosition, target, bounds, 5.0, true, ignoredPositions, null)
      }

      @JvmStatic
      fun getExplosionDamage(
         explosionPosition: Vec3, target: LivingEntity, bounds: AABB, power: Double, ignoreLowResistance: Boolean, ignoredPosition: BlockPos?, solidPosition: BlockPos?
      ): Float {
         val ignoredPositions = Collections.singletonList<BlockPos?>(ignoredPosition)

         return getExplosionDamage(explosionPosition, target, bounds, power, ignoreLowResistance, ignoredPositions, solidPosition)
      }

      @JvmStatic
      fun getExplosionDamage(
         explosionPosition: Vec3, target: LivingEntity, bounds: AABB, power: Double, ignoreLowResistance: Boolean, ignoredPositions: Collection<*>, solidPosition: BlockPos?
      ): Float {
         val player = ClientAccess.collectiveMinecraft.player
         if (player !== null && player === target) {
            if (player.isCreative) {
               return 0.0f
            }
         }
         val bottomCenter = Vec3(
            Mth.lerp(MathConstants.ONE_HALF, bounds.minX, bounds.maxX),
            bounds.minY,
            Mth.lerp(MathConstants.ONE_HALF, bounds.minZ, bounds.maxZ)
         )
         val diameter = power * 2.0
         val normalizedDistance = bottomCenter.distanceTo(explosionPosition) / diameter
         if (normalizedDistance > 1.0) {
            return 0.0f
         }

         // Кэш: значение — детерминированная функция не-блочных входов (точный
         // ключ) и чтений блоков (проверяются на каждом чтении). Epoch фиксируется
         // ДО расчёта; запись отбрасывается, если epoch изменился.
         val cacheKey = ExplosionDamageCache.keyOf(
            explosionPosition, target, bounds, power, ignoreLowResistance, ignoredPositions, solidPosition
         )
         val cachedDamage = ExplosionDamageCache.get(cacheKey)
         if (cachedDamage != null) {
            return cachedDamage
         }
         val cacheEpoch = ExplosionDamageCache.epoch()
         val blockReads = ExplosionDamageCache.recorder()
         blockReads.reset()

         val exposure = getExposure(explosionPosition, target, bounds, ignoredPositions, solidPosition, ignoreLowResistance, blockReads)
         val impact = (1.0 - normalizedDistance) * exposure
         val quadraticImpact = impact * impact + impact
         val scaledImpact = quadraticImpact / 2.0
         val rawDamage = scaledImpact * 7.0 * diameter
         var damage = (rawDamage + 1.0).toFloat()
         damage = scaleForDifficulty(damage)

         damage = applyDamageReduction(target, damage)
         damage = resolveBlockingDamage(target, explosionPosition, damage)
         damage = Math.max(damage, 0.0f)
         ExplosionDamageCache.put(cacheKey, cacheEpoch, damage, blockReads, target)
         return damage
      }

      @JvmStatic
      fun getExposure(
         explosionPosition: Vec3, target: Entity, ignoredPositions: Collection<*>, ignoreLowResistance: Boolean
      ): Float {
         val bounds = target.boundingBox

         return getExposure(explosionPosition, target, bounds, ignoredPositions, null, ignoreLowResistance)
      }

      @JvmStatic
      fun getExposure(
         explosionPosition: Vec3, target: Entity, bounds: AABB, ignoredPositions: Collection<*>, solidPosition: BlockPos?, ignoreLowResistance: Boolean
      ): Float {
         return getExposure(explosionPosition, target, bounds, ignoredPositions, solidPosition, ignoreLowResistance, null)
      }

      private fun getExposure(
         explosionPosition: Vec3, target: Entity, bounds: AABB, ignoredPositions: Collection<*>, solidPosition: BlockPos?, ignoreLowResistance: Boolean, blockReads: BlockReadRecorder?
      ): Float {
         val samplePosition = Vec3(0.0, 0.0, 0.0)
         val stepX = 1.0 / ((bounds.maxX - bounds.minX) * 2.0 + 1.0)
         val stepY = 1.0 / ((bounds.maxY - bounds.minY) * 2.0 + 1.0)
         val stepZ = 1.0 / ((bounds.maxZ - bounds.minZ) * 2.0 + 1.0)
         val offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0
         val offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0
         if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) {
            return 0.0f
         }
         // Один составной классификатор вместо трёх анонимных + firstNonNone:
         // порядок проверок и результаты test() идентичны (NO_OVERRIDE всегда
         // возвращает NONE, фильтр firstNonNone его только пропускал).
         val classifier = ExposureClassifier(ignoredPositions, solidPosition, ignoreLowResistance)
         val context = BlockRaycastContext(samplePosition, explosionPosition, Block.COLLIDER, Fluid.NONE, target, classifier, blockReads)
         // Границы и lerp-константы инвариантны циклам: sampleX зависит только
         // от fractionX, sampleY — от fractionY, sampleZ — от fractionZ.
         // Значения те же (одинаковый вход lerp → одинаковый выход), счётчики
         // и порядок обхода не меняются.
         val minimumX = bounds.minX
         val maximumX = bounds.maxX
         val minimumY = bounds.minY
         val maximumY = bounds.maxY
         val minimumZ = bounds.minZ
         val maximumZ = bounds.maxZ
         var visibleSamples = 0
         var totalSamples = 0
         var fractionX = 0.0
         while (fractionX <= 1.0) {
            val sampleX = Mth.lerp(fractionX, minimumX, maximumX)
            var fractionY = 0.0
            while (fractionY <= 1.0) {
               val sampleY = Mth.lerp(fractionY, minimumY, maximumY)
               var fractionZ = 0.0
               while (fractionZ <= 1.0) {
                  val sampleZ = Mth.lerp(fractionZ, minimumZ, maximumZ)
                  val offsetSampleX = sampleX + offsetX
                  val shiftedZ = sampleZ + offsetZ
                  Vec3Utils.setComponents(samplePosition, offsetSampleX, sampleY, shiftedZ)

                  if (RaycastUtils.raycast(context).type == Type.MISS) {
                     visibleSamples++
                  }
                  totalSamples++
                  fractionZ += stepZ
               }
               fractionY += stepY
            }
            fractionX += stepX
         }
         return visibleSamples.toFloat() / totalSamples
      }

      /**
       * Эквивалент BlockRaycastClassifier.firstNonNone(
       *   ignorePositions(ignoredPositions), solidPosition(solidPosition),
       *   if (ignoreLowResistance) IGNORE_LOW_RESISTANCE else NO_OVERRIDE):
       * первая не-NONE победа в том же порядке, без аллокаций на каждый вызов.
       */
      private class ExposureClassifier(
         private val ignoredPositions: Collection<*>,
         private val solidPosition: BlockPos?,
         private val ignoreLowResistance: Boolean
      ) : BlockRaycastClassifier {
         override fun test(blockPos: BlockPos, state: BlockState): BlockRaycastOverride {
            if (ignoredPositions.isNotEmpty()) {
               for (value in ignoredPositions) {
                  if (blockPos == (value as BlockPos?)) {
                     return BlockRaycastOverride.IGNORE
                  }
               }
            }
            if (solidPosition != null && blockPos == solidPosition) {
               return BlockRaycastOverride.SOLID
            }
            if (ignoreLowResistance && state.block.explosionResistance < 600.0f) {
               return BlockRaycastOverride.IGNORE
            }
            return BlockRaycastOverride.NONE
         }
      }

      @JvmStatic
      fun applyDamageReduction(target: LivingEntity, damage: Float): Float {
         var damage = damage
         val crystalModule = ClientContext.getModule(AutoCrystalModule::class.java) as AutoCrystalModule?
         val level = ClientAccess.collectiveMinecraft.level ?: return damage

         val assumeBestArmor: Boolean
         if (crystalModule != null && crystalModule.isToggled()) {
            assumeBestArmor = crystalModule.assumeBestArmorSetting.value
         } else {
            assumeBestArmor = false
         }

         val damageSources = level.damageSources()
         val damageSource = damageSources.explosion(null as Explosion?)

         val armor = target.armorValue
         val toughnessAttribute = Attributes.ARMOR_TOUGHNESS
         val toughness = target.getAttributeValue(toughnessAttribute).toFloat()
         damage = CombatRules.getDamageAfterAbsorb(target, damage, damageSource, armor.toFloat(), toughness)
         val resistanceEffect = MobEffects.DAMAGE_RESISTANCE
         if (target.hasEffect(resistanceEffect) || assumeBestArmor) {
            val resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE)
            var resistanceAmplifier = 1.0f
            if (resistance != null) {
               resistanceAmplifier = resistance.amplifier.toFloat()
            }
            val resistanceLevel = resistanceAmplifier + 1.0f
            val resistanceNumerator = damage * (25.0f - resistanceLevel * 5.0f)
            damage = resistanceNumerator / 25.0f
         }
         var protectionScore = 0
         if (target is Player) {
            var assumedArmorUnits = 1
            val armorStacks = EquipmentUtils.getArmorStacks(target)
            val armorItems = armorStacks.iterator()
            while (armorItems.hasNext()) {
               val armorItem = armorItems.next()

               if (!armorItem.isEmpty) {
                  val headArmorTag = ItemTags.HEAD_ARMOR
                  if (armorItem.`is`(headArmorTag)) {
                     assumedArmorUnits--
                  }
                  val enchantments = EnchantmentUtils.getLevels(armorItem)
                  val blastProtectionKey = Enchantments.BLAST_PROTECTION

                  var blastContributionWord = enchantments.getOrDefault(blastProtectionKey, 0) as Int
                  blastContributionWord *= 2
                  val protectionKey = Enchantments.PROTECTION
                  val protectionLevel = enchantments.getOrDefault(protectionKey, 0) as Int
                  protectionScore += blastContributionWord + protectionLevel
                  assumedArmorUnits++
               }
            }
            if (assumeBestArmor) {
               val armorUnitsAsFloat = assumedArmorUnits.toFloat()
               val assumedArmorFraction = armorUnitsAsFloat / 4.0f
               protectionScore = (assumedArmorFraction * 20.0f).toInt()
            }
         }
         if (protectionScore > 20) {
            protectionScore = 20
         }
         val protectionAsFloat = protectionScore.toFloat()
         damage *= 1.0f - protectionAsFloat / 25.0f
         return if (damage < 0.0f) 0.0f else damage
      }

      private fun resolveBlockingDamage(
         target: LivingEntity, explosionPosition: Vec3, damage: Float
      ): Float {
         if (!target.isBlocking) {
            return damage
         }

         val level = ClientAccess.collectiveMinecraft.level ?: return damage
         val damageSources = level.damageSources()
         val damageSource = damageSources.explosion(null as Explosion?)
         if (damageSource.`is`(DamageTypeTags.BYPASSES_SHIELD)) {
            return damage
         }

         val headYaw = target.yHeadRot
         val viewVector = target.calculateViewVector(0.0f, headYaw)

         var attackDirection = explosionPosition.subtract(target.position())
         val horizontalDirection = Vec3(attackDirection.x, 0.0, attackDirection.z)
         attackDirection = horizontalDirection.normalize()
         val dot = attackDirection.dot(viewVector)
         return if (dot > 0.0) 0.0f else damage
      }

      @JvmStatic
      fun scaleForDifficulty(damage: Float): Float {
         val level = ClientAccess.collectiveMinecraft.level ?: return damage
         return when (level.difficulty) {
            Difficulty.PEACEFUL -> 0.0f
            Difficulty.EASY -> Math.min(damage / 2.0f + 1.0f, damage)
            Difficulty.HARD -> damage * 1.5f
            else -> damage
         }
      }

      @JvmStatic
      fun getDurabilityPercent(stack: ItemStack): Int {
         val remainingFraction = (stack.maxDamage.toFloat() - stack.damageValue.toFloat()) / stack.maxDamage.toFloat()
         val usedFraction = 1.0f - remainingFraction
         return Mth.clamp(100 - Math.ceil((usedFraction * 100.0f).toDouble()).toInt(), 1, 100)
      }
   }
}
