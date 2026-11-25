package me.collective.client.utils.entity

import me.collective.client.modules.client.AntiCheatModule

enum class SwingAction {
   INTERACT,
   MINING,
   HIT;

   fun isEnabled(antiCheat: AntiCheatModule): Boolean {
      val swingEnabled = antiCheat.swingSetting.value
      if (!swingEnabled) {
         return false
      } else if (this == MINING) {
         return antiCheat.miningSwingSetting.value
      } else if (this == HIT) {
         return antiCheat.hitSetting.value
      } else {
         return antiCheat.placeSetting.value && this == INTERACT
      }
   }
}
