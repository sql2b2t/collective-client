package me.collective.client.modules.player

import me.collective.client.events.StatusEffectQueryEvent
import me.collective.client.events.EventHandler
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting

import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffects

class NoEffectsModule : Module("NoEffects", "Allows you to cancel certain effects.", ModuleCategory.PLAYER) {
   private val effectsSetting: RegistrySetSetting<MobEffect>

   init {
      this.effectsSetting = this.add(RegistrySetSetting<MobEffect>("Effects", BuiltInRegistries.MOB_EFFECT))
      val defaults: List<MobEffect> = listOf(MobEffects.LEVITATION.value(), MobEffects.SLOW_FALLING.value())
      this.selectedEffects().addAll(defaults)
      this.defaultEffects().addAll(defaults)
   }

   fun blocksEffect(effect: Holder<MobEffect>): Boolean {
      return this.selectedEffects().contains(effect.value())
   }

   @EventHandler
   private fun onEffectQuery(event: StatusEffectQueryEvent) {
      if (this.blocksEffect(event.effect)) {
         event.cancel()
      }
   }

   private fun selectedEffects(): MutableSet<MobEffect> {
      return this.effectsSetting.value as MutableSet<MobEffect>
   }

   private fun defaultEffects(): MutableSet<MobEffect> {
      return this.effectsSetting.getDefaultValue() as MutableSet<MobEffect>
   }
}
