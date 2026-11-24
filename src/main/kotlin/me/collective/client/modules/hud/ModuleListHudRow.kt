package me.collective.client.modules.hud

import me.collective.client.modules.Module

import net.minecraft.network.chat.Component

class ModuleListHudRow(owner: ModuleListModule, module: Module) : HudTextRow(
   { ->
      val infoText: String = module.infoString
      Component.literal(infoText)
   },
   { ->
      module.isToggled() &&
         module.isDrawn() &&
         (!module.getKeybind().isUnbound() ||
         !owner.onlyBoundSetting.value)
   }
) {
   val module: Module = module
}
