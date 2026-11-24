package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.modules.HudWidgetModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.HudAnchor
import me.collective.client.settings.options.ListFilterMode

import java.awt.Color
import java.util.ArrayList
import java.util.Comparator
import java.util.function.Predicate
import java.util.function.Supplier
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffect

class EffectsModule : HudWidgetModule("Effects", "potions") {
   private val selectionSetting: Setting<ListFilterMode>
   private val effectsSetting: Setting<Set<MobEffect>>
   private val vanillaSetting: Setting<Boolean>
   private val effectRows: MutableList<EffectHudRow>

   override fun onEnable() {
      this.effectRows.clear()
      BuiltInRegistries.MOB_EFFECT.listElements().forEach { effectReference ->
         this.effectRows.add(
            EffectHudRow(effectReference, Supplier<Any> {
               val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return@Supplier false
               if (!player.getActiveEffectsMap().containsKey(effectReference)) {
                  return@Supplier false
               }
               val selection: ListFilterMode = this@EffectsModule.selectionSetting.value!!
               val effect: MobEffect = effectReference.value()
               return@Supplier ListFilterMode.matches(selection, effect, this@EffectsModule.effectsSetting)
            })
         )
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(ignoredEvent: LocalPlayerTickEvent) {
      this.effectRows.sortWith(
         Comparator.comparing<EffectHudRow, String> { row ->
            val effect: MobEffect = row.effectHolder.value()
            effect.getDisplayName().getString()
         }
      )
   }

   init {
      this.selectionSetting = this.add(EnumSetting("Selection", ListFilterMode.ANY))
      this.effectsSetting = this.add(RegistrySetSetting("Effects", BuiltInRegistries.MOB_EFFECT))
      val vanillaOption = BooleanSetting("Vanilla", false)
      this.vanillaSetting = this.add(vanillaOption)
      this.effectRows = ArrayList()
      val rowRenderer: HudTextListRenderer = object : HudTextListRenderer(this@EffectsModule, this@EffectsModule.effectRows) {
         override fun getRowColor(rowPosition: Float, row: HudTextRow): Color {
            val effectRow = row as EffectHudRow
            val effect: MobEffect = effectRow.effectHolder.value()
            val useVanillaColor: Boolean = this@EffectsModule.vanillaSetting.value!!
            return if (useVanillaColor) {
               Color(this.getVanillaEffectColor(effect), false)
            } else {
               super.getRowColor(rowPosition, row)
            }
         }

         private fun getVanillaEffectColor(effect: MobEffect): Int {
            return effect.getColor()
         }
      }
      this.setRenderer(rowRenderer)
      this.getRenderer()
         .setAnchor(HudAnchor.BOTTOM_RIGHT)
      val effectsForVisibility = this.effectsSetting
      val visibilityPredicate = Predicate<Any> {
         return@Predicate this@EffectsModule.selectionSetting.value !== ListFilterMode.ANY
      }
      effectsForVisibility.addVisibilityPredicate(visibilityPredicate)
   }
}
