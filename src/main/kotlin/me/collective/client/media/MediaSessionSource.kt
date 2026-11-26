package me.collective.client.media

import java.io.IOException

interface MediaSessionSource : AutoCloseable {
    @Throws(IOException::class)
    fun read(): MediaSnapshot

    @Throws(IOException::class)
    override fun close()
}
