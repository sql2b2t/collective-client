package me.collective.client.media

import java.io.IOException
import java.util.ArrayList
import java.util.Objects

class MacApplicationMediaSource(private val runtime: RuntimeApi) : MediaSessionSource {
    init {
        Objects.requireNonNull(runtime)
    }

    @Volatile
    private var closed = false

    private var activeReader: Thread? = null

    private var selectedPid: Int? = null

    @Throws(IOException::class)
    override fun read(): MediaSnapshot {
        synchronized(this) {
            if (closed || Thread.currentThread().isInterrupted()) throw IOException("Mac media source closed")
            if (activeReader != null) throw IOException("Concurrent Mac media reads are not supported")
            activeReader = Thread.currentThread()
        }
        try {
            runtime.begin().use { _ ->
                val applications = ArrayList<Application>()
                for (app in runtime.alreadyRunning()) {
                    if (SUPPORTED_APPLICATIONS.contains(app.bundleId) && app.pid > 0) applications.add(app)
                }
                applications.sortWith(compareBy({ it.bundleId }, { it.pid }))
                if (selectedPid != null) {
                    for (n in applications.indices) {
                        if (applications[n].pid == selectedPid) {
                            applications.add(0, applications.removeAt(n))
                            break
                        }
                    }
                }
                var fallback: MediaSnapshot? = null
                var fallbackPid: Int? = null
                var unavailable: IOException? = null
                for (application in applications) {
                    if (closed || Thread.currentThread().isInterrupted()) throw IOException("Mac media source closed")
                    try {
                        val schema = MacScriptingDictionary.parse(runtime.dictionary(application))

                        val permission = runtime.permissionForGetData(application, false)
                        if (permission != 0) throw IOException("Mac media Automation permission unavailable: " + permission)
                        val session = runtime.openPidTarget(application, READ_SEND_MODE, REPLY_TIMEOUT_TICKS)
                        val state = session.enumValue(schema.playerState)
                        val status = if (state == schema.playing) 4
                        else if (schema.paused != null && state == schema.paused) 5
                        else if (schema.stopped != null && state == schema.stopped) 3
                        else 0
                        var title = ""
                        var artist = ""
                        var position = 0L
                        var duration = 0L
                        if (status == 4 || status == 5) {
                            title = session.text(listOf(schema.currentTrack, schema.title))
                            artist = session.text(listOf(schema.currentTrack, schema.artist))
                            if (schema.position != null) {
                                position = MacScriptingDictionary.millis(session.number(listOf(schema.position.code)), schema.position)
                            }
                            if (schema.duration != null) {
                                duration = MacScriptingDictionary.millis(session.number(listOf(schema.currentTrack, schema.duration.code)), schema.duration)
                            }
                        }
                        val snapshot = MediaSnapshot(status, artist, title, position, duration)
                        if (status == 4) {
                            selectedPid = application.pid
                            return snapshot
                        }
                        if (fallback == null) {
                            fallback = snapshot
                            fallbackPid = application.pid
                        }
                    } catch (problem: IOException) {
                        if (unavailable == null) unavailable = problem else unavailable.addSuppressed(problem)
                    }
                }
                selectedPid = fallbackPid
                if (fallback != null) return fallback
                if (unavailable != null) throw unavailable
                return MediaSnapshot.empty()
            }
        } finally {
            synchronized(this) { activeReader = null }
        }
    }

    override fun close() {
        val reader: Thread?
        synchronized(this) {
            closed = true
            reader = activeReader
        }
        reader?.interrupt()
    }

    data class Application(
        @get:JvmName("pid") val pid: Int,
        @get:JvmName("bundleId") val bundleId: String,
        @get:JvmName("resourceDirectory") val resourceDirectory: String,
        @get:JvmName("dictionaryFile") val dictionaryFile: String
    )

    interface Scope : AutoCloseable {
        @Throws(IOException::class)
        override fun close()
    }

    interface Session {
        @Throws(IOException::class)
        fun enumValue(property: Int): Int

        @Throws(IOException::class)
        fun text(propertyPath: List<Int>): String

        @Throws(IOException::class)
        fun number(propertyPath: List<Int>): Double?
    }

    interface RuntimeApi {
        @Throws(IOException::class)
        fun begin(): Scope

        @Throws(IOException::class)
        fun alreadyRunning(): List<Application>

        @Throws(IOException::class)
        fun dictionary(application: Application): ByteArray

        @Throws(IOException::class)
        fun permissionForGetData(application: Application, askUserIfNeeded: Boolean): Int

        @Throws(IOException::class)
        fun openPidTarget(application: Application, sendMode: Int, replyTimeoutTicks: Long): Session
    }

    companion object {
        @JvmField
        val SUPPORTED_APPLICATIONS: Set<String> = setOf("com.apple.Music", "com.spotify.client")

        @JvmField
        val READ_SEND_MODE: Int = 131219

        @JvmField
        val REPLY_TIMEOUT_TICKS: Long = 120L

        @JvmStatic
        @Throws(IOException::class)
        fun connect(): MacApplicationMediaSource {
            return MacApplicationMediaSource(MacJnaRuntime())
        }
    }
}
