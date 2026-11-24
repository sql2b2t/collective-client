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
import net.minecraft.core.component.DataComponents
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
import net.minecraft.world.level.Explosion
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

         val exposure = getExposure(explosionPosition, target, bounds, ignoredPositions, solidPosition, ignoreLowResistance)
         val impact = (1.0 - normalizedDistance) * exposure
         val quadraticImpact = impact * impact + impact
         val scaledImpact = quadraticImpact / 2.0
         val rawDamage = scaledImpact * 7.0 * diameter
         var damage = (rawDamage + 1.0).toFloat()
         damage = scaleForDifficulty(damage)

         damage = applyDamageReduction(target, damage)
         damage = resolveBlockingDamage(target, explosionPosition, damage)
         damage = Math.max(damage, 0.0f)
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
         val samplePosition = Vec3(0.0, 0.0, 0.0)
         val stepX = 1.0 / ((bounds.maxX - bounds.minX) * 2.0 + 1.0)
         val stepY = 1.0 / ((bounds.maxY - bounds.minY) * 2.0 + 1.0)
         val stepZ = 1.0 / ((bounds.maxZ - bounds.minZ) * 2.0 + 1.0)
         val offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0
         val offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0
         if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) {
            return 0.0f
         }
         val builder = BlockRaycastContext.Builder(samplePosition, explosionPosition)

         builder.withEntity(target)
         val classifierArray = arrayOfNulls<BlockRaycastClassifier>(3)
         classifierArray[0] = BlockRaycastClassifier.ignorePositions(ignoredPositions)

         classifierArray[1] = BlockRaycastClassifier.solidPosition(solidPosition)
         classifierArray[2] = if (ignoreLowResistance)
            BlockRaycastClassifier.IGNORE_LOW_RESISTANCE
         else
            BlockRaycastClassifier.NO_OVERRIDE
         builder.withClassifiers(*classifierArray)
         val context = builder.build()
         var visibleSamples = 0
         var totalSamples = 0
         var fractionX = 0.0
         while (fractionX <= 1.0) {
            var fractionY = 0.0
            while (fractionY <= 1.0) {
               var fractionZ = 0.0
               while (fractionZ <= 1.0) {
                  val maximumX = bounds.maxX
                  val minimumX = bounds.minX
                  val sampleX = Mth.lerp(fractionX, minimumX, maximumX)
                  val maximumY = bounds.maxY
                  val minimumY = bounds.minY
                  val sampleY = Mth.lerp(fractionY, minimumY, maximumY)
                  val maximumZ = bounds.maxZ
                  val minimumZ = bounds.minZ
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
         val resistanceEffect = MobEffects.RESISTANCE
         if (target.hasEffect(resistanceEffect) || assumeBestArmor) {
            val resistance = target.getEffect(MobEffects.RESISTANCE)
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
         if (!target.isUsingItem) {
            return damage
         }

         val usedItem = target.useItem
         val blockingType = DataComponents.BLOCKS_ATTACKS

         val blocksAttacks = usedItem.get(blockingType)
         if (blocksAttacks == null) {
            return damage
         }

         val level = ClientAccess.collectiveMinecraft.level ?: return damage
         val damageSources = level.damageSources()
         val damageSource = damageSources.explosion(null as Explosion?)
         val headYaw = target.yHeadRot
         val viewVector = target.calculateViewVector(0.0f, headYaw)

         var attackDirection = explosionPosition.subtract(target.position())
         val horizontalDirection = Vec3(attackDirection.x, 0.0, attackDirection.z)
         attackDirection = horizontalDirection.normalize()
         val dot = attackDirection.dot(viewVector)
         val angle = Math.acos(dot)

         return blocksAttacks.resolveBlockedDamage(damageSource, damage, angle)
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
