package me.collective.client.modules.misc

import me.collective.client.ClientAccess
import me.collective.client.bots.gui.BotManagerScreen
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory

class BotsModule : Module("Bots", "Opens the bot manager screen.", ModuleCategory.MISC) {

    override fun onEnable() {
        toggle()
        val mc = ClientAccess.collectiveMinecraft
        mc.gui.setScreen(BotManagerScreen(mc.gui.screen()))
    }
}
