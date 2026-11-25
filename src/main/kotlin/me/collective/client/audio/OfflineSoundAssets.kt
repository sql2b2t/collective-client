package me.collective.client.audio

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

object OfflineSoundAssets {
    private const val PREFIX = "/assets/collective/offline/killstreaks/"

    @JvmStatic
    fun installKillstreaks(path: Path) {
        val directory = path.toAbsolutePath().normalize()
        if (Files.isDirectory(directory)) {
            return
        }
        try {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw IOException("Sound-pack path is occupied by a file: $directory")
            }
            val manifest = OfflineSoundAssets::class.java.getResourceAsStream(PREFIX + "manifest.txt")
                ?.use { inputStream -> String(inputStream.readAllBytes(), StandardCharsets.UTF_8) }
                ?: throw IOException("Preserved sound-pack manifest is missing")
            for (relativePath in manifest.lines().toList()) {
                if (relativePath.isBlank()) {
                    continue
                }
                val destination = directory.resolve(relativePath).normalize()
                if (!destination.startsWith(directory) || !relativePath.endsWith(".ogg")) {
                    throw IOException("Invalid preserved sound path")
                }
                val inputStream = OfflineSoundAssets::class.java.getResourceAsStream(PREFIX + relativePath)
                    ?: continue
                try {
                    Files.createDirectories(destination.parent)
                    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                        continue
                    }
                    Files.copy(inputStream, destination)
                } finally {
                    inputStream.close()
                }
            }
        } catch (exception: IOException) {
            logUnavailable(exception)
        } catch (exception: SecurityException) {
            logUnavailable(exception)
        }
    }

    private fun logUnavailable(exception: Exception) {
        System.getLogger("Collective Offline Sounds")
            .log(System.Logger.Level.WARNING, "Optional killstreak sounds are unavailable: " + exception.message)
    }
}
