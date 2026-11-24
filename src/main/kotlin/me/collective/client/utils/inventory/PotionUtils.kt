package me.collective.client.utils.inventory

import me.collective.mixin.LivingEntityAccessor
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.util.ARGB
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.PotionContents

class PotionUtils private constructor() {

   companion object {
      private val EFFECTS_BY_COLOR: MutableMap<Int, Holder<MobEffect>> = HashMap()

      @JvmField
      val MISSING_POTION: Potion = Potion("empty")

      init {
         BuiltInRegistries.MOB_EFFECT.listElements().forEach { registerEffectColor(it) }
      }

      @JvmStatic
      fun getPotion(stack: ItemStack): Potion {
         val contents: PotionContents? = stack.get(DataComponents.POTION_CONTENTS)
         if (contents == null) {
            return MISSING_POTION
         }

         val potionHolder = contents.potion().orElse(null)
         return if (potionHolder == null) MISSING_POTION else potionHolder.value()
      }

      @JvmStatic
      fun getEffectsByParticleColor(entity: LivingEntity): Set<Holder<MobEffect>> {
         val swirlAccessor = LivingEntityAccessor.`collective$potion_swirls`()
         val effects = HashSet<Holder<MobEffect>>()
         val particles =
            entity.entityData.get(swirlAccessor as EntityDataAccessor<Any>) as List<*>

         for (particle in particles) {
            if (particle is ColorParticleOption) {
               val alpha = 1.0F
               val red = particle.red
               val green = particle.green
               val blue = particle.blue
               val color = ARGB.colorFromFloat(alpha, red, green, blue)
               val effect = EFFECTS_BY_COLOR[color]
               if (effect != null) {
                  effects.add(effect)
               }
            }
         }

         return effects
      }

      private fun registerEffectColor(reference: Holder.Reference<MobEffect>) {
         val effectColor = reference.value().color
         val color = ARGB.color(255, effectColor)
         EFFECTS_BY_COLOR[color] = reference
      }
   }
}
