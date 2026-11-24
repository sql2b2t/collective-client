package me.collective.client.audio

import me.collective.client.ClientAccess
import me.collective.mixin.AbstractSoundInstanceAccessor
import java.io.ByteArrayInputStream
import net.minecraft.world.phys.Vec3

class SoundClip(private val soundBytes: ByteArray?) : ClientAccess {
    fun play() {
        play(1.0F)
    }

    fun playAt(position: Vec3?) {
        playAt(position, 1.0F)
    }

    fun play(volume: Float) {
        playAt(null, volume)
    }

    fun playAt(position: Vec3?, volume: Float) {
        if (this.soundBytes == null) return
        val minecraft = ClientAccess.collectiveMinecraft
        minecraft.execute {
            if (minecraft.soundManager == null || position != null && minecraft.level == null) return@execute
            try {
                val stream = ByteArrayInputStream(this.soundBytes)
                val sound = if (position == null) {
                    StreamSoundInstance(stream)
                } else {
                    StreamSoundInstance(position, stream)
                }
                (sound as AbstractSoundInstanceAccessor).setVolume(volume)
                minecraft.soundManager.play(sound)
            } catch (failure: Exception) {
                ClientAccess.logger.warn("Could not play sound", failure)
            }
        }
    }

    companion object {
        @JvmField
        val NOOP: SoundClip = SoundClip(null)
    }
}
