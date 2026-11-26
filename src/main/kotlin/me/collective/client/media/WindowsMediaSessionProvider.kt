package me.collective.client.media

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.time.Duration
import java.util.Objects
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Supplier

class WindowsMediaSessionProvider : PortableMediaSessionProvider.WindowsBackend {
    data class MediaState(
        @get:JvmName("playbackStatus") val playbackStatus: Int,
        @get:JvmName("artist") val artist: String,
        @get:JvmName("title") val title: String,
        @get:JvmName("positionMillis") val positionMillis: Long,
        @get:JvmName("durationMillis") val durationMillis: Long
    ) {
        init {
            Objects.requireNonNull(artist, "Artist")
            Objects.requireNonNull(title, "Title")
        }

        companion object {
            @JvmStatic
            fun empty(): MediaState {
                return MediaState(0, "", "", 0, 0)
            }
        }
    }

    interface RuntimeApi : StdCallLibrary {
        fun RoInitialize(apartmentType: Int): Int

        fun RoUninitialize()

        fun RoGetActivationFactory(className: Pointer?, iid: Pointer?, factory: PointerByReference): Int

        fun WindowsCreateString(text: Pointer?, utf16Units: Int, result: PointerByReference): Int

        fun WindowsDeleteString(value: Pointer?)

        fun WindowsGetStringRawBuffer(value: Pointer?, utf16Units: IntByReference): Pointer?
    }

    class WinRtException internal constructor(operation: String, @get:JvmName("result") val result: Int) :
        RuntimeException(operation + " failed (HRESULT 0x" + Integer.toHexString(result) + ")")

    internal class SessionReader(private val api: RuntimeApi, private val timeout: Duration) : AutoCloseable {
        private lateinit var manager: ComReference
        private var initialized = false
        private var stopped = false
        private val owner: Thread = Thread.currentThread()

        init {
            Objects.requireNonNull(api, "Windows runtime API")
            Objects.requireNonNull(timeout, "Async timeout")
            if (timeout.isNegative || timeout.isZero) throw IllegalArgumentException("Async timeout must be positive")

            check(api.RoInitialize(1), "RoInitialize(MTA)")
            initialized = true
            try {
                StringHandle.create(api, RUNTIME_CLASS).use { name ->
                    guid(MANAGER_STATICS).use { iid ->
                        val result = PointerByReference()
                        check(api.RoGetActivationFactory(name.pointer, iid, result), "RoGetActivationFactory")
                        ComReference.required(result.value).use { factory ->
                            factory.`object`(6).use { operation ->
                                manager = awaitObject(operation)
                            }
                        }
                    }
                }
            } catch (failure: RuntimeException) {
                close()
                throw failure
            } catch (failure: Error) {
                close()
                throw failure
            } catch (failure: InterruptedException) {
                close()
                throw failure
            }
        }

        @Throws(InterruptedException::class)
        fun read(): MediaState {
            requireOwner()
            if (stopped) throw IllegalStateException("Media reader is closed")
            val session = manager.optionalObject(6) ?: return MediaState.empty()
            return session.use { s ->
                val status = s.`object`(9).use { info -> info.integer(7) }
                val strings = s.`object`(7).use { operation ->
                    awaitObject(operation).use { properties ->
                        Pair(properties.string(api, 6), properties.string(api, 9))
                    }
                }
                val title = strings.first
                val artist = strings.second
                var position = 0L
                var duration = 0L
                s.optionalObject(8)?.use { timeline ->
                    val start = timeline.longInteger(6)
                    val end = timeline.longInteger(7)
                    val current = timeline.longInteger(10)

                    position = (current - start) / 10_000
                    duration = (end - start) / 10_000
                }
                MediaState(status, artist, title, position, duration)
            }
        }

        @Throws(InterruptedException::class)
        private fun awaitObject(operation: ComReference): ComReference {
            requireOwner()
            operation.query(ASYNC_INFO).use { info ->
                var terminal = false
                try {
                    val startedAt = System.nanoTime()
                    val limit = timeout.toNanos()
                    while (true) {
                        if (Thread.currentThread().isInterrupted()) throw InterruptedException("Media query interrupted")
                        val status = info.integer(7)
                        if (status == 1) {
                            terminal = true
                            return operation.`object`(8)
                        }
                        if (status == 2) {
                            terminal = true
                            throw WinRtException("Media query canceled", 0x800704c7.toInt())
                        }
                        if (status == 3) {
                            terminal = true
                            throw WinRtException("Media query failed", info.integer(8))
                        }
                        if (status != 0) throw IllegalStateException("Unknown async media status $status")
                        if (System.nanoTime() - startedAt >= limit) throw WinRtException("Media query timed out", 0x800705b4.toInt())
                        Thread.sleep(10)
                    }
                } finally {
                    if (!terminal) info.bestEffort(9)
                    info.bestEffort(10)
                }
            }
        }

        private fun requireOwner() {
            if (Thread.currentThread() !== owner) throw IllegalStateException("COM media reader used from another thread")
        }

        override fun close() {
            requireOwner()
            if (stopped) return
            stopped = true
            try {
                if (this::manager.isInitialized) manager.close()
            } finally {
                if (initialized) {
                    initialized = false
                    api.RoUninitialize()
                }
            }
        }

        companion object {
            private const val RUNTIME_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager"
            private const val MANAGER_STATICS = "2050c4ee-11a0-57de-aed7-c97c70338245"
            private const val ASYNC_INFO = "00000036-0000-0000-c000-000000000046"
        }
    }

    internal class ComReference private constructor(private var pointer: Pointer?) : AutoCloseable {
        private val owner: Thread = Thread.currentThread()

        internal fun query(interfaceId: String): ComReference {
            guid(interfaceId).use { iid ->
                val result = PointerByReference()
                check(invoke(0, iid, result), "QueryInterface")
                return required(result.value)
            }
        }

        internal fun optionalObject(slot: Int): ComReference? {
            val result = PointerByReference()
            check(invoke(slot, result), "Media interface method $slot")
            val value = result.value
            return if (value == null || Pointer.nativeValue(value) == 0L) null else required(value)
        }

        internal fun `object`(slot: Int): ComReference {
            val result = optionalObject(slot)
            if (result == null) throw IllegalStateException("Windows media method $slot returned no required object")
            return result
        }

        internal fun integer(slot: Int): Int {
            val value = IntByReference()
            check(invoke(slot, value), "Media integer method $slot")
            return value.value
        }

        internal fun longInteger(slot: Int): Long {
            val value = LongByReference()
            check(invoke(slot, value), "Media TimeSpan method $slot")
            return value.value
        }

        internal fun string(api: RuntimeApi, slot: Int): String {
            val value = PointerByReference()
            check(invoke(slot, value), "Media string method $slot")
            StringHandle(api, value.value).use { string -> return string.text() }
        }

        internal fun bestEffort(slot: Int) {
            try {
                invoke(slot)
            } catch (cleanupFailure: RuntimeException) {
            }
        }

        override fun close() {
            if (Thread.currentThread() !== owner) throw IllegalStateException("COM reference released from another thread")
            if (pointer == null) return
            try {
                invoke(2)
            } finally {
                pointer = null
            }
        }

        private fun invoke(slot: Int, vararg arguments: Any): Int {
            if (Thread.currentThread() !== owner) throw IllegalStateException("COM reference used from another thread")
            val target = pointer ?: throw IllegalStateException("COM reference is released")
            val table = target.getPointer(0)
            val address = table.getPointer(slot.toLong() * Native.POINTER_SIZE)
            if (address == null) throw IllegalStateException("Null Windows media vtable slot $slot")
            val call = arrayOfNulls<Any>(arguments.size + 1)
            call[0] = target
            System.arraycopy(arguments, 0, call, 1, arguments.size)
            return Function.getFunction(address, Function.ALT_CONVENTION).invokeInt(call)
        }

        companion object {
            internal fun required(pointer: Pointer?): ComReference {
                if (pointer == null || Pointer.nativeValue(pointer) == 0L) throw IllegalStateException("Windows media API returned a null interface")
                return ComReference(pointer)
            }
        }
    }

    internal class StringHandle internal constructor(private val api: RuntimeApi, internal var pointer: Pointer?) : AutoCloseable {
        private var released = false

        internal fun text(): String {
            if (released) throw IllegalStateException("Windows string is released")
            val length = IntByReference()
            val raw = api.WindowsGetStringRawBuffer(pointer, length)
            val count = length.value
            if (count < 0 || count > 1_048_576) throw IllegalStateException("Invalid media string length $count")
            if (count == 0) return ""
            if (raw == null) throw IllegalStateException("Windows string has no data")
            val units = raw.getShortArray(0L, count)
            val text = CharArray(count)
            for (i in 0 until count) text[i] = (units[i].toInt() and 0xffff).toChar()
            return String(text)
        }

        override fun close() {
            if (released) return
            released = true
            api.WindowsDeleteString(pointer)
            pointer = null
        }

        companion object {
            internal fun create(api: RuntimeApi, text: String): StringHandle {
                Memory(Math.max(2L, text.length * 2L)).use { utf16 ->
                    for (i in text.indices) utf16.setShort(i * 2L, text[i].code.toShort())
                    val result = PointerByReference()
                    check(api.WindowsCreateString(utf16, text.length, result), "WindowsCreateString")
                    return StringHandle(api, result.value)
                }
            }
        }
    }

    private val update: Consumer<MediaState>
    private val runtime: Supplier<RuntimeApi>
    private val started = AtomicBoolean()
    private val closed = AtomicBoolean()

    @Volatile
    private var latestState: MediaState = MediaState.empty()

    @Volatile
    private var lastFailureMessage: String? = null

    @Volatile
    private var worker: Thread? = null

    constructor(update: Consumer<MediaState>) : this(update, Supplier { Native.load("combase", RuntimeApi::class.java) })

    internal constructor(update: Consumer<MediaState>, runtime: Supplier<RuntimeApi>) {
        this.update = Objects.requireNonNull(update, "Media update consumer")
        this.runtime = Objects.requireNonNull(runtime, "Windows runtime API supplier")
    }

    fun latest(): MediaState {
        return latestState
    }

    override fun lastFailure(): String? {
        return lastFailureMessage
    }

    @Synchronized
    override fun start() {
        if (closed.get() || !started.compareAndSet(false, true)) return
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            publish(MediaState.empty())
            return
        }
        val thread = Thread({ poll() }, "collective-media-session")
        thread.isDaemon = true
        worker = thread
        thread.start()
    }

    private fun publish(state: MediaState) {
        if (closed.get()) return
        latestState = state
        update.accept(state)
    }

    private fun poll() {
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            try {
                val api = runtime.get()
                SessionReader(api, ASYNC_TIMEOUT).use { reader ->
                    while (!closed.get() && !Thread.currentThread().isInterrupted()) {
                        publish(reader.read())
                        lastFailureMessage = null
                        Thread.sleep(POLL_MILLIS)
                    }
                }
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (unavailable: Throwable) {
                if (unavailable is RuntimeException || unavailable is LinkageError) {
                    lastFailureMessage = unavailable.javaClass.simpleName + ": " + unavailable.message
                    publish(MediaState.empty())
                    try {
                        Thread.sleep(RETRY_MILLIS)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                } else {
                    throw unavailable
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val thread = worker
        if (thread != null) thread.interrupt()
    }

    companion object {
        @JvmField
        val PLAYING: Int = 4

        private val ASYNC_TIMEOUT: Duration = Duration.ofSeconds(3)

        private const val POLL_MILLIS = 500L
        private const val RETRY_MILLIS = 5_000L

        internal fun guid(text: String): Memory {
            val id = UUID.fromString(text)
            val bytes = Memory(16)
            val high = id.mostSignificantBits
            val low = id.leastSignificantBits
            bytes.setInt(0, (high ushr 32).toInt())
            bytes.setShort(4, (high ushr 16).toShort())
            bytes.setShort(6, high.toShort())
            for (i in 0 until 8) bytes.setByte(8L + i, (low ushr (56 - 8 * i)).toByte())
            return bytes
        }

        internal fun check(result: Int, operation: String) {
            if (result < 0) throw WinRtException(operation, result)
        }
    }
}
