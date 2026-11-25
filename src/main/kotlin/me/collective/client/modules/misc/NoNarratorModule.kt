package me.collective.client.modules.misc

import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory

class NoNarratorModule : Module("NoNarrator", "Disables the narrator feature.", ModuleCategory.MISC) {
    init {
        this.setDrawn(false)
    }
}
