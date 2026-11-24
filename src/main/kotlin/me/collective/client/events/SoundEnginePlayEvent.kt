package me.collective.client.events

import net.minecraft.client.resources.sounds.SoundInstance

class SoundEnginePlayEvent(val soundInstance: SoundInstance) : CancellableEvent()
