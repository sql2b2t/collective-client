package me.collective.client.modules.misc

import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting

class AntiQuitModule : Module("AntiQuit", "Prevents you from quitting the game/server accidentally.", ModuleCategory.MISC, *arrayOf("antidisconnect")) {
    @JvmField
    val disconnectSetting: Setting<Boolean> = this.add(BooleanSetting("Disconnect", false))

    @JvmField
    val gameCloseSetting: Setting<Boolean> = this.add(BooleanSetting("GameClose", false))
}
