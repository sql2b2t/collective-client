package me.collective.client.media

import java.io.IOException
import java.util.Locale
import java.util.Objects
import java.util.function.Consumer

class PortableMediaSessionProvider : AutoCloseable {
    enum class Backend { WINDOWS_GSMTC, LINUX_MPRIS, MACOS_APPLE_EVENTS, UNSUPPORTED_OS }

    enum class Availability { NOT_STARTED, AVAILABLE, UNAVAILABLE, UNSUPPORTED, CLOSED }

    internal fun interface SourceFactory {
        @Throws(IOException::class)
        fun open(): MediaSessionSource
    }

    internal interface WindowsBackend : AutoCloseable {
        fun start()

        fun lastFailure(): String?

        override fun close()
    }

    internal fun interface WindowsFactory {
        fun create(update: Consumer<MediaSnapshot>): WindowsBackend
    }

    private val update: Consumer<MediaSnapshot>
    private val sourceFactory: SourceFactory
    private val macSourceFactory: SourceFactory
    private val windowsFactory: WindowsFactory
    private val selectedBackend: Backend

    @Volatile
    private var latestState: MediaSnapshot = MediaSnapshot.empty()

    @Volatile
    private var availabilityState: Availability = Availability.NOT_STARTED

    @Volatile
    private var lastFailureMessage: String? = null

    @Volatile
    private var source: MediaSessionSource? = null

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var windows: WindowsBackend? = null

    @Volatile
    private var closed = false

    private var started = false

    internal constructor(
        update: Consumer<MediaSnapshot>,
        platform: String,
        sourceFactory: SourceFactory,
        macSourceFactory: SourceFactory,
        windowsFactory: WindowsFactory
    ) {
        this.update = Objects.requireNonNull(update)
        this.sourceFactory = Objects.requireNonNull(sourceFactory)
        this.macSourceFactory = Objects.requireNonNull(macSourceFactory)
        this.windowsFactory = Objects.requireNonNull(windowsFactory)
        val os = Objects.requireNonNull(platform).lowercase(Locale.ROOT)
        selectedBackend = when {
            os.startsWith("windows") -> Backend.WINDOWS_GSMTC
            os.contains("linux") -> Backend.LINUX_MPRIS
            os.contains("mac") || os.contains("darwin") -> Backend.MACOS_APPLE_EVENTS
            else -> Backend.UNSUPPORTED_OS
        }
    }

    internal constructor(
        update: Consumer<MediaSnapshot>,
        platform: String,
        sourceFactory: SourceFactory,
        windowsFactory: WindowsFactory
    ) : this(update, platform, sourceFactory, SourceFactory { MacApplicationMediaSource.connect() }, windowsFactory)

    constructor(update: Consumer<MediaSnapshot>) : this(
        update,
        System.getProperty("os.name", ""),
        SourceFactory { MprisSessionSource.connect() },
        SourceFactory { MacApplicationMediaSource.connect() },
        WindowsFactory { sink ->
            WindowsMediaSessionProvider { state ->
                sink.accept(
                    MediaSnapshot(
                        state.playbackStatus, state.artist, state.title,
                        state.positionMillis, state.durationMillis
                    )
                )
            }
        }
    )

    fun latest(): MediaSnapshot {
        return latestState
    }

    fun backend(): Backend {
        return selectedBackend
    }

    fun availability(): Availability {
        val w = windows
        return if (!closed && w != null && w.lastFailure() != null) Availability.UNAVAILABLE else availabilityState
    }

    fun lastFailure(): String? {
        val w = windows
        return if (w != null) w.lastFailure() else lastFailureMessage
    }

    @Synchronized
    fun start() {
        if (closed || started) return
        started = true
        if (selectedBackend == Backend.WINDOWS_GSMTC) {
            val w = windowsFactory.create { state -> publish(state) }
            windows = w
            w.start()
        } else if (selectedBackend == Backend.LINUX_MPRIS || selectedBackend == Backend.MACOS_APPLE_EVENTS) {
            val thread = Thread(
                { poll() },
                if (selectedBackend == Backend.LINUX_MPRIS) "collective-mpris-session" else "collective-mac-media"
            )
            worker = thread
            thread.isDaemon = true
            thread.start()
        } else {
            availabilityState = Availability.UNSUPPORTED
            lastFailureMessage = "No local-media adapter for this operating system"
        }
    }

    @Synchronized
    private fun publish(state: MediaSnapshot) {
        if (closed) return
        latestState = Objects.requireNonNull(state)
        availabilityState = Availability.AVAILABLE
        update.accept(state)
    }

    @Synchronized
    private fun publishFailure(failure: Throwable) {
        if (closed) return
        lastFailureMessage = failure.javaClass.simpleName + ": " + failure.message
        latestState = MediaSnapshot.empty()
        availabilityState = Availability.UNAVAILABLE
        update.accept(latestState)
    }

    private fun poll() {
        while (!closed && !Thread.currentThread().isInterrupted()) {
            var opened: MediaSessionSource? = null
            try {
                val openedNow = (if (selectedBackend == Backend.MACOS_APPLE_EVENTS) macSourceFactory else sourceFactory).open()
                opened = openedNow
                synchronized(this) {
                    if (closed) return
                    source = openedNow
                }
                while (!closed && !Thread.currentThread().isInterrupted()) {
                    publish(openedNow.read())
                    lastFailureMessage = null
                    Thread.sleep(POLL_MILLIS)
                }
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (unavailable: Throwable) {
                if (unavailable is IOException || unavailable is RuntimeException || unavailable is LinkageError) {
                    publishFailure(unavailable)
                } else {
                    throw unavailable
                }
            } finally {
                synchronized(this) { if (source === opened) source = null }
                val toClose = opened
                if (toClose != null) {
                    try {
                        toClose.close()
                    } catch (cleanup: IOException) {
                        if (!closed) lastFailureMessage = "Media close failed: " + cleanup.message
                    }
                }
            }
            if (!closed && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(RETRY_MILLIS)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    override fun close() {
        val (current, w, thread) = synchronized(this) {
            if (closed) return
            closed = true
            availabilityState = Availability.CLOSED
            Triple(source, windows, worker)
        }
        thread?.interrupt()
        if (current != null) {
            try {
                current.close()
            } catch (cleanup: IOException) {
                lastFailureMessage = "Media close failed: " + cleanup.message
            }
        }
        w?.close()
    }

    companion object {
        private const val POLL_MILLIS = 500L
        private const val RETRY_MILLIS = 5_000L
    }
}
