package me.collective.client.events

import me.collective.client.modules.misc.Macro

class MacroTriggerEvent(val macro: Macro) : CancellableEvent()
