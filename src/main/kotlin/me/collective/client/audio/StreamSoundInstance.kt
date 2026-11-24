package me.collective.client.audio

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.AudioStream
import net.minecraft.client.sounds.JOrbisAudioStream
import net.minecraft.client.sounds.SoundBufferLibrary
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

class StreamSoundInstance : AbstractSoundInstance {
    private val inputStream: InputStream

    constructor(position: Vec3, inputStream: InputStream) : super(
        Identifier.fromNamespaceAndPath(
            "collective", "sound"
        ),
        SoundSource.MASTER,
        SoundInstance.createUnseededRandom()
    ) {
        this.x = position.x
        this.y = position.y
        this.z = position.z
        this.inputStream = inputStream
    }

    constructor(inputStream: InputStream) : super(
        Identifier.fromNamespaceAndPath(
            "collective", "sound"
        ),
        SoundSource.MASTER,
        SoundInstance.createUnseededRandom()
    ) {
        this.inputStream = inputStream
        this.relative = true
        this.x = 0.0
        this.y = 0.0

        this.z = -1.0
    }

    override fun getAudioStream(
        soundBuffers: SoundBufferLibrary,
        identifier: Identifier,
        repeatInstantly: Boolean
    ): CompletableFuture<AudioStream> {
        return try {
            val audioStream = JOrbisAudioStream(this.inputStream)
            CompletableFuture.completedFuture(audioStream)
        } catch (exception: Exception) {
            try {
                this.inputStream.close()
            } catch (cleanup: IOException) {
                exception.addSuppressed(cleanup)
            }
            CompletableFuture.failedFuture(exception)
        }
    }
}
