package me.collective.client.modules.misc

import me.collective.client.events.EventHandler
import me.collective.client.events.SoundEnginePlayEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent

class SoundBlockerModule : Module("SoundBlocker", "Blocks certain sounds from being played.", ModuleCategory.MISC) {
    private val soundsSetting: Setting<Set<SoundEvent>> = this.add(
        RegistrySetSetting<SoundEvent>("Sounds", BuiltInRegistries.SOUND_EVENT, *arrayOf<SoundEvent>())
    )

    @EventHandler
    private fun onSoundEnginePlayEvent(event: SoundEnginePlayEvent) {
        for (sound in this.soundsSetting.getValue()) {
            val blockedIdentifier: ResourceLocation = sound.location()
            val playingSound: SoundInstance = event.soundInstance
            if (blockedIdentifier == playingSound.location) {
                event.cancel()
                break
            }
        }
    }
}
