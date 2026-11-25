package me.collective.client.modules.hud

import me.collective.client.ClientAccess

import java.util.function.Supplier
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.world.TickRateManager
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffectUtil

class EffectHudRow(effectHolder: Holder<MobEffect>, visibilitySupplier: Supplier<*>) : HudTextRow(
   {
      val player: LocalPlayer? = ClientAccess.collectiveMinecraft.player!!
      val effectInstance: MobEffectInstance? = player?.activeEffectsMap?.get(effectHolder)
      if (effectInstance == null) {
         Component.empty()
      } else {
         val durationScale: Float = 1.0f
         val level: ClientLevel? = ClientAccess.collectiveMinecraft.level!!
         if (level == null) {
            Component.empty()
         } else {
            val tickRateManager: TickRateManager = level.tickRateManager()
            val tickRate: Float = tickRateManager.tickrate()
            val durationComponent: Component =
               MobEffectUtil.formatDuration(effectInstance, durationScale, tickRate)
            var durationText: String = durationComponent.string
            if (durationText.startsWith("0")) {
               durationText = durationText.substring(1)
            }
            val amplifierTextResult: String
            if (effectInstance.amplifier == 0) {
               amplifierTextResult = ""
            } else {
               val displayedLevel: Int = effectInstance.amplifier + 1
               amplifierTextResult = "$displayedLevel "
            }
            val effect: MobEffect = effectHolder.value()
            val effectDisplayName: Component = effect.displayName
            val formattedText: String = "%s %s%s%s".format(
               effectDisplayName.string,
               amplifierTextResult,
               ChatFormatting.WHITE,
               durationText
            )
            Component.literal(formattedText)
         }
      }
   },
   visibilitySupplier
) {
   val effectHolder: Holder<MobEffect> = effectHolder
}
