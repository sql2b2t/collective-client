package me.collective.client.modules.movement

import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory

class NoJumpDelayModule : Module("NoJumpDelay", "Removes the jumping delay when sprint-jumping.", ModuleCategory.MOVEMENT) {
   init {
      this.setDrawn(false)
   }
}
