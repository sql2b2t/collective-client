package me.collective.client.managers

import me.collective.client.ClientContext
import me.collective.client.settings.JsonConfigurable

class ConfigurableRegistry : Iterable<JsonConfigurable> {
    private val configurables = ArrayList<JsonConfigurable>()

    init {
        register(ClientContext.webhookManager)
        register(ClientContext.moduleRegistry)
        register(ClientContext.socialRegistry)
        register(ClientContext.waypointRegistry)
        register(ClientContext.macroRegistry)
        register(ClientContext.stashRegistry)
        register(ClientContext.hudModuleRegistry)
        register(ClientContext.kitRegistry)
        register(ClientContext.autoPresetRegistry)
        register(ClientContext.aliasRegistry)
        register(ClientContext.chatFilterRegistry)
        register(ClientContext.glintRegistry)
        register(ClientContext.commandRegistry)
        register(ClientContext.tradeRegistry)
        register(ClientContext.blockColorRegistry)
    }

    override fun iterator(): MutableIterator<JsonConfigurable> = configurables.iterator()

    private fun register(configurable: JsonConfigurable) {
        configurables.add(configurable)
    }
}
