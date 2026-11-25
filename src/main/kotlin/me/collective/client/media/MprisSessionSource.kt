package me.collective.client.media

import java.io.IOException
import java.util.Objects
import kotlin.math.max

class MprisSessionSource(private val connection: Connection) : MediaSessionSource {
    init {
        Objects.requireNonNull(connection)
    }

    interface Connection : AutoCloseable {
        @Throws(IOException::class)
        fun call(destination: String, path: String, iface: String, member: String, arguments: List<String>): Any?

        @Throws(IOException::class)
        override fun close()
    }

    private var selectedPlayer: String? = null

    @Throws(IOException::class)
    override fun read(): MediaSnapshot {
        val result = connection.call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "ListNames", listOf())
        val names = result as? List<*> ?: throw IOException("Invalid D-Bus player list")
        if (names.any { it !is String }) throw IOException("Invalid D-Bus player list")
        val players = names.map { it as String }.filter { it.startsWith("org.mpris.MediaPlayer2.") }.sorted()
        val ordered = ArrayList(players)
        val selected = selectedPlayer
        if (selected != null && ordered.remove(selected)) ordered.add(0, selected)
        var fallback = MediaSnapshot.empty()
        var fallbackPlayer: String? = null
        for (player in ordered) {
            val response: Any? = try {
                connection.call(player, "/org/mpris/MediaPlayer2", "org.freedesktop.DBus.Properties", "GetAll", listOf("org.mpris.MediaPlayer2.Player"))
            } catch (gone: DbusSessionConnection.MethodFailure) {
                continue
            }
            val state = fromProperties(response)
            if (state.playbackStatus == MediaSnapshot.PLAYING) {
                selectedPlayer = player
                return state
            }
            if (fallbackPlayer == null) {
                fallback = state
                fallbackPlayer = player
            }
        }
        selectedPlayer = fallbackPlayer
        return fallback
    }

    @Throws(IOException::class)
    override fun close() {
        connection.close()
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun connect(): MprisSessionSource {
            // DBUS_SESSION_BUS_ADDRESS экспортирует не каждый способ запуска (TTY/ssh,
            // часть лаунчеров); современный сессионный шин доступен и по
            // $XDG_RUNTIME_DIR/bus, пробуем его как fallback.
            var address = System.getenv("DBUS_SESSION_BUS_ADDRESS")
            if (address == null || address.isBlank()) {
                val runtimeDir = System.getenv("XDG_RUNTIME_DIR")
                if (!runtimeDir.isNullOrBlank()) {
                    address = "unix:path=$runtimeDir/bus"
                }
            }
            return MprisSessionSource(DbusSessionConnection.connect(address))
        }

        @JvmStatic
        @Throws(IOException::class)
        fun fromProperties(response: Any?): MediaSnapshot {
            if (response !is Map<*, *>) throw IOException("Invalid MPRIS properties")
            val properties = response
            val playback = string(property(properties, "PlaybackStatus", "s"))
            val status = when (playback) {
                "Playing" -> 4
                "Paused" -> 5
                "Stopped" -> 3
                else -> 0
            }
            var metadata: Any? = property(properties, "Metadata", "a{sv}")
            if (metadata == null) metadata = emptyMap<Any?, Any?>()
            if (metadata !is Map<*, *>) throw IOException("Invalid MPRIS track metadata")
            val track = metadata
            val title = string(property(track, "xesam:title", "s"))
            val artists = property(track, "xesam:artist", "as")
            var artist = ""
            if (artists != null) {
                if (artists !is List<*> || artists.any { it !is String }) throw IOException("Invalid MPRIS artist list")
                artist = (artists as List<*>).map { it as String }.joinToString(", ")
            }
            return MediaSnapshot(
                status, artist, title,
                millis(property(properties, "Position", "x")),
                millis(property(track, "mpris:length", "x"))
            )
        }

        @Throws(IOException::class)
        private fun property(values: Map<*, *>, key: String, signature: String): Any? {
            val value = values[key]
            if (value is DbusMessageCodec.Variant) {
                if (value.signature() != signature) throw IOException("Unexpected MPRIS type for " + key)
                return value.value()
            }
            return value
        }

        @Throws(IOException::class)
        private fun string(value: Any?): String {
            if (value == null) return ""
            if (value is String) return value
            throw IOException("Invalid MPRIS string property")
        }

        @Throws(IOException::class)
        private fun millis(value: Any?): Long {
            if (value == null) return 0
            if (value is Long) return max(0L, value / 1000)
            throw IOException("Invalid MPRIS microsecond property")
        }
    }
}
