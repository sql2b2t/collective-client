package me.collective.client.config

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

object ClientDataDirectory {

    @JvmStatic
    fun migrateLegacy(destination: Path): Path {
        try {
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) &&
         !Files.isDirectory(destination)
            ) {
                throw IOException("Collective data path is not a directory: $destination")
            }
            return destination
        } catch (failure: IOException) {
            throw UncheckedIOException("Unable to validate client data directory $destination", failure)
        }
    }
}
