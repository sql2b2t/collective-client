package me.collective.client.media

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration

internal class DbusSessionConnection private constructor(private val transport: Transport) : MprisSessionSource.Connection {
    internal interface Transport : AutoCloseable {
        @Throws(IOException::class)
        fun readFully(target: ByteArray, deadline: Long)

        @Throws(IOException::class)
        fun writeFully(bytes: ByteArray, deadline: Long)

        @Throws(IOException::class)
        override fun close()
    }

    class MethodFailure internal constructor(name: String) : IOException("D-Bus method unavailable: " + name)

    private var nextSerial = 1

    @Throws(IOException::class)
    private fun authenticate() {
        val deadline = System.nanoTime() + CALL_TIMEOUT_NANOS
        transport.writeFully("\u0000AUTH EXTERNAL\r\n".toByteArray(StandardCharsets.US_ASCII), deadline)
        var response = line(deadline)
        if (response == "DATA" || response == "DATA ") {
            transport.writeFully("DATA\r\n".toByteArray(StandardCharsets.US_ASCII), deadline)
            response = line(deadline)
        }
        if (!response.matches(Regex("OK [0-9a-fA-F]{32}"))) throw IOException("Session bus did not accept EXTERNAL peer credentials")
        transport.writeFully("BEGIN\r\n".toByteArray(StandardCharsets.US_ASCII), deadline)
    }

    @Throws(IOException::class)
    private fun line(deadline: Long): String {
        val bytes = ByteArrayOutputStream()
        val one = ByteArray(1)
        while (bytes.size() < 4096) {
            transport.readFully(one, deadline)
            val value = one[0].toInt() and 255
            if (value == '\r'.code) {
                transport.readFully(one, deadline)
                if (one[0].toInt() != '\n'.code) throw IOException("Invalid auth line terminator")
                return bytes.toString(StandardCharsets.US_ASCII)
            }
            if (value < 32 || value > 126) throw IOException("Invalid auth line byte")
            bytes.write(value)
        }
        throw IOException("Auth line exceeds protocol limit")
    }

    @Throws(IOException::class)
    override fun call(destination: String, path: String, iface: String, member: String, arguments: List<String>): Any? {
        val serial = nextSerial++
        if (nextSerial == 0) nextSerial = 1
        val deadline = System.nanoTime() + CALL_TIMEOUT_NANOS
        transport.writeFully(DbusMessageCodec.methodCall(serial, destination, path, iface, member, arguments), deadline)
        for (messages in 0 until 256) {
            val fixed = ByteArray(16)
            transport.readFully(fixed, deadline)
            val layout = DbusMessageCodec.layout(fixed)
            val fields = ByteArray(layout.paddedFieldsBytes())
            val body = ByteArray(layout.bodyBytes)
            transport.readFully(fields, deadline)
            transport.readFully(body, deadline)
            val reply = DbusMessageCodec.decode(layout, fields, body)
            if (reply.replySerial != (serial.toLong() and 0xFFFFFFFFL)) continue
            if (reply.type == 3) throw MethodFailure(reply.headers[4]!!.value() as String)
            if (reply.type != 2 || reply.arguments.size != 1) throw IOException("Unexpected read-only D-Bus reply")
            return reply.arguments[0]
        }
        throw IOException("Session bus flooded unrelated messages")
    }

    @Throws(IOException::class)
    override fun close() {
        transport.close()
    }

    companion object {
        private val CALL_TIMEOUT_NANOS = Duration.ofSeconds(2).toNanos()

        @JvmStatic
        @Throws(IOException::class)
        fun connect(address: String?): DbusSessionConnection {
            var failure: IOException? = null
            for (endpoint in endpoints(address)) {
                var connection: DbusSessionConnection? = null
                try {
                    val created = DbusSessionConnection(SocketTransport.connect(endpoint))
                    connection = created
                    created.authenticate()
                    val hello = created.call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "Hello", listOf())
                    if (hello !is String || !hello.startsWith(":")) throw IOException("Invalid session-bus Hello reply")
                    return created
                } catch (unavailable: IOException) {
                    val opened = connection
                    if (opened != null) {
                        try {
                            opened.close()
                        } catch (cleanup: IOException) {
                            unavailable.addSuppressed(cleanup)
                        }
                    }
                    if (failure == null) failure = unavailable else failure.addSuppressed(unavailable)
                }
            }
            throw failure ?: IOException("No filesystem Unix session-bus endpoint is available")
        }

        @Throws(IOException::class)
        internal fun endpoints(address: String?): List<Path> {
            if (address == null || address.isBlank()) throw IOException("DBUS_SESSION_BUS_ADDRESS is unavailable")
            val result = ArrayList<Path>()
            for (entry in address.split(";")) {
                if (!entry.startsWith("unix:")) continue
                var path: String? = null
                for (option in entry.substring(5).split(",")) {
                    val equals = option.indexOf('=')
                    if (equals < 1) throw IOException("Invalid D-Bus Unix address option")
                    if (option.substring(0, equals) == "path") {
                        if (path != null) throw IOException("Duplicate D-Bus path option")
                        path = unescape(option.substring(equals + 1))
                    }
                }

                if (path != null) {
                    if (!path.startsWith("/")) throw IOException("Session-bus path must be absolute")
                    result.add(Path.of(path))
                }
            }
            return java.util.List.copyOf(result)
        }

        @Throws(IOException::class)
        private fun unescape(value: String): String {
            val bytes = ByteArrayOutputStream()
            var at = 0
            while (at < value.length) {
                val c = value[at]
                if (c == '%') {
                    if (at + 2 >= value.length) throw IOException("Invalid D-Bus address escape")
                    at++
                    val high = Character.digit(value[at], 16)
                    at++
                    val low = Character.digit(value[at], 16)
                    if (high < 0 || low < 0) throw IOException("Invalid D-Bus address escape")
                    bytes.write((high shl 4) or low)
                } else {
                    if (!(c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z' || "-_/.*".indexOf(c) >= 0)) throw IOException("Invalid unescaped D-Bus address byte")
                    bytes.write(c.code)
                }
                at++
            }
            val decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
            if (decoded.indexOf(0.toChar()) >= 0) throw IOException("Nul session-bus path")
            return decoded
        }

        private class SocketTransport private constructor(
            private val channel: SocketChannel,
            private val selector: Selector,
            private val key: SelectionKey
        ) : Transport {
            companion object {
                @Throws(IOException::class)
                fun connect(endpoint: Path): SocketTransport {
                    val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
                    var selector: Selector? = null
                    try {
                        channel.configureBlocking(false)
                        selector = Selector.open()
                        val key = channel.register(selector, SelectionKey.OP_CONNECT)
                        val transport = SocketTransport(channel, selector, key)
                        if (!channel.connect(UnixDomainSocketAddress.of(endpoint))) {
                            val deadline = System.nanoTime() + CALL_TIMEOUT_NANOS
                            while (!channel.finishConnect()) transport.await(SelectionKey.OP_CONNECT, deadline)
                        }
                        return transport
                    } catch (failure: Throwable) {
                        if (failure is IOException || failure is RuntimeException) {
                            try {
                                channel.close()
                            } catch (cleanup: IOException) {
                                failure.addSuppressed(cleanup)
                            }
                            val opened = selector
                            if (opened != null) {
                                try {
                                    opened.close()
                                } catch (cleanup: IOException) {
                                    failure.addSuppressed(cleanup)
                                }
                            }
                            throw failure
                        } else {
                            throw failure
                        }
                    }
                }
            }

            @Throws(IOException::class)
            private fun await(interest: Int, deadline: Long) {
                if (Thread.currentThread().isInterrupted()) throw InterruptedIOException("Media read interrupted")
                val left = deadline - System.nanoTime()
                if (left <= 0) throw IOException("Local media session-bus timeout")
                key.interestOps(interest)
                selector.select(Math.max(1L, (left + 999_999) / 1_000_000))
                selector.selectedKeys().clear()
            }

            @Throws(IOException::class)
            override fun readFully(target: ByteArray, deadline: Long) {
                val bytes = ByteBuffer.wrap(target)
                while (bytes.hasRemaining()) {
                    checkDeadline(deadline)
                    val count = channel.read(bytes)
                    if (count < 0) throw EOFException("Session bus closed")
                    if (count == 0) await(SelectionKey.OP_READ, deadline)
                }
            }

            @Throws(IOException::class)
            override fun writeFully(bytes: ByteArray, deadline: Long) {
                val packet = ByteBuffer.wrap(bytes)
                while (packet.hasRemaining()) {
                    checkDeadline(deadline)
                    if (channel.write(packet) == 0) await(SelectionKey.OP_WRITE, deadline)
                }
            }

            @Throws(IOException::class)
            private fun checkDeadline(deadline: Long) {
                if (Thread.currentThread().isInterrupted()) throw InterruptedIOException("Media operation interrupted")
                if (deadline - System.nanoTime() <= 0) throw IOException("Local media session-bus timeout")
            }

            @Throws(IOException::class)
            override fun close() {
                var failure: IOException? = null
                try {
                    channel.close()
                } catch (error: IOException) {
                    failure = error
                }
                try {
                    selector.close()
                } catch (error: IOException) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                }
                if (failure != null) throw failure
            }
        }
    }
}
