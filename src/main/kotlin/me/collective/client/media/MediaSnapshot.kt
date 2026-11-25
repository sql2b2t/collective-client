package me.collective.client.media

import java.util.Objects

data class MediaSnapshot(
    @get:JvmName("playbackStatus") val playbackStatus: Int,
    @get:JvmName("artist") val artist: String,
    @get:JvmName("title") val title: String,
    @get:JvmName("positionMillis") val positionMillis: Long,
    @get:JvmName("durationMillis") val durationMillis: Long
) {
    init {
        Objects.requireNonNull(artist, "artist")
        Objects.requireNonNull(title, "title")
    }

    companion object {
        const val PLAYING: Int = 4

        @JvmStatic
        fun empty(): MediaSnapshot {
            return MediaSnapshot(0, "", "", 0, 0)
        }
    }
}
