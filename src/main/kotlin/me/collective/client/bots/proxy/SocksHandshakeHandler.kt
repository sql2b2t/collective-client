package me.collective.client.bots.proxy

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.PendingWriteQueue
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.Promise
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class SocksHandshakeHandler(
    private val proxy: ProxyConfig,
    private val proxyAddress: InetSocketAddress,
    private val handshakePromise: Promise<Void>
) : ChannelDuplexHandler() {

    private enum class State {
        SOCKS5_METHOD,
        SOCKS5_AUTH,
        SOCKS5_CONNECT,
        SOCKS4_CONNECT,
        CONNECTED,
        FAILED
    }

    private var destinationAddress: InetSocketAddress? = null
    private var state: State = State.SOCKS5_METHOD
    private var pendingWrites: PendingWriteQueue? = null
    private var writePending: Boolean = false
    private var buffer: ByteBuf? = null
    private var timeoutFuture: Future<*>? = null
    private var connectPromise: ChannelPromise? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        scheduleTimeout(ctx)
        if (proxy.type == ProxyType.SOCKS5) {
            sendSocks5MethodRequest(ctx)
            state = State.SOCKS5_METHOD
        } else {
            sendSocks4ConnectRequest(ctx)
            state = State.SOCKS4_CONNECT
        }
        super.channelActive(ctx)
    }

    override fun connect(ctx: ChannelHandlerContext, remoteAddress: SocketAddress, localAddress: SocketAddress?, promise: ChannelPromise) {
        if (remoteAddress is InetSocketAddress) {
            destinationAddress = remoteAddress
            connectPromise = promise
            super.connect(ctx, proxyAddress, localAddress, ctx.newPromise())
        } else {
            super.connect(ctx, remoteAddress, localAddress, promise)
        }
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (state != State.CONNECTED) {
            if (pendingWrites == null) {
                pendingWrites = PendingWriteQueue(ctx)
            }
            pendingWrites!!.add(msg, promise)
        } else {
            super.write(ctx, msg, promise)
        }
    }

    override fun flush(ctx: ChannelHandlerContext) {
        if (state != State.CONNECTED) {
            writePending = true
        } else {
            super.flush(ctx)
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (state == State.CONNECTED) {
            ctx.fireChannelRead(msg)
            return
        }

        if (msg is ByteBuf) {
            appendData(ctx, msg)
            try {
                processHandshake(ctx)
            } finally {
                msg.release()
            }
        } else {
            ctx.fireChannelRead(msg)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        fail(ctx, IOException("Disconnected during SOCKS handshake"))
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        fail(ctx, cause)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        releaseBuffer()
        cancelTimeout()
        super.handlerRemoved(ctx)
    }

    private fun appendData(ctx: ChannelHandlerContext, data: ByteBuf) {
        if (buffer == null) {
            buffer = ctx.alloc().buffer(data.readableBytes())
        }
        buffer!!.writeBytes(data)
    }

    private fun releaseBuffer() {
        buffer?.release()
        buffer = null
    }

    private fun scheduleTimeout(ctx: ChannelHandlerContext) {
        timeoutFuture = ctx.executor().schedule({
            fail(ctx, IOException("SOCKS handshake timeout"))
        }, 12000L, TimeUnit.MILLISECONDS)
    }

    private fun cancelTimeout() {
        timeoutFuture?.cancel(false)
        timeoutFuture = null
    }

    private fun processHandshake(ctx: ChannelHandlerContext) {
        while (buffer != null && buffer!!.isReadable) {
            val progress = when (state) {
                State.SOCKS5_METHOD -> processSocks5MethodResponse(ctx)
                State.SOCKS5_AUTH -> processSocks5AuthResponse(ctx)
                State.SOCKS5_CONNECT -> processSocks5ConnectResponse(ctx)
                State.SOCKS4_CONNECT -> processSocks4ConnectResponse(ctx)
                else -> false
            }
            if (!progress) break
        }
    }

    private fun sendSocks5MethodRequest(ctx: ChannelHandlerContext) {
        val buf = ctx.alloc().buffer()
        buf.writeByte(5) // SOCKS5
        if (proxy.hasAuth()) {
            buf.writeByte(2)
            buf.writeByte(0) // No auth
            buf.writeByte(2) // Username/Password
        } else {
            buf.writeByte(1)
            buf.writeByte(0) // No auth
        }
        ctx.writeAndFlush(buf)
    }

    private fun processSocks5MethodResponse(ctx: ChannelHandlerContext): Boolean {
        if (buffer!!.readableBytes() < 2) return false
        val version = buffer!!.readUnsignedByte().toInt()
        val method = buffer!!.readUnsignedByte().toInt()

        if (version != 5) {
            fail(ctx, IOException("Invalid SOCKS version: $version"))
            return false
        }

        when (method) {
            0 -> {
                sendSocks5ConnectRequest(ctx)
                state = State.SOCKS5_CONNECT
                return true
            }
            2 -> {
                if (!proxy.hasAuth()) {
                    fail(ctx, IOException("Server requested auth but none configured"))
                    return false
                }
                sendSocks5AuthRequest(ctx)
                state = State.SOCKS5_AUTH
                return true
            }
            else -> {
                fail(ctx, IOException("Unsupported SOCKS5 auth method: $method"))
                return false
            }
        }
    }

    private fun sendSocks5AuthRequest(ctx: ChannelHandlerContext) {
        val userBytes = proxy.username.toByteArray(StandardCharsets.UTF_8)
        val passBytes = proxy.password.toByteArray(StandardCharsets.UTF_8)
        val buf = ctx.alloc().buffer(3 + userBytes.size + passBytes.size)
        buf.writeByte(1) // Subnegotiation version
        buf.writeByte(userBytes.size)
        buf.writeBytes(userBytes)
        buf.writeByte(passBytes.size)
        buf.writeBytes(passBytes)
        ctx.writeAndFlush(buf)
    }

    private fun processSocks5AuthResponse(ctx: ChannelHandlerContext): Boolean {
        if (buffer!!.readableBytes() < 2) return false
        val ver = buffer!!.readUnsignedByte().toInt()
        val status = buffer!!.readUnsignedByte().toInt()

        if (ver == 1 && status == 0) {
            sendSocks5ConnectRequest(ctx)
            state = State.SOCKS5_CONNECT
            return true
        } else {
            fail(ctx, IOException("SOCKS5 authentication failed with status: $status"))
            return false
        }
    }

    private fun sendSocks5ConnectRequest(ctx: ChannelHandlerContext) {
        val dest = destinationAddress ?: run {
            fail(ctx, IOException("Missing destination address"))
            return
        }
        val port = dest.port
        val buf = ctx.alloc().buffer()
        buf.writeByte(5) // SOCKS5
        buf.writeByte(1) // CONNECT
        buf.writeByte(0) // Reserved

        val host = dest.hostString
        val addr = dest.address
        when (addr) {
            is Inet4Address -> {
                buf.writeByte(1)
                buf.writeBytes(addr.address)
            }
            is Inet6Address -> {
                buf.writeByte(4)
                buf.writeBytes(addr.address)
            }
            else -> {
                val hostBytes = host.toByteArray(StandardCharsets.US_ASCII)
                buf.writeByte(3)
                buf.writeByte(hostBytes.size)
                buf.writeBytes(hostBytes)
            }
        }
        buf.writeShort(port)
        ctx.writeAndFlush(buf)
    }

    private fun processSocks5ConnectResponse(ctx: ChannelHandlerContext): Boolean {
        if (buffer!!.readableBytes() < 5) return false
        val readerIndex = buffer!!.readerIndex()
        val ver = buffer!!.getUnsignedByte(readerIndex).toInt()
        val reply = buffer!!.getUnsignedByte(readerIndex + 1).toInt()
        val addrType = buffer!!.getUnsignedByte(readerIndex + 3).toInt()

        val expectedLen = when (addrType) {
            1 -> 10 // IPv4
            4 -> 22 // IPv6
            3 -> {
                val domainLen = buffer!!.getUnsignedByte(readerIndex + 4).toInt()
                7 + domainLen
            }
            else -> {
                fail(ctx, IOException("Unsupported SOCKS5 address type: $addrType"))
                return false
            }
        }

        if (buffer!!.readableBytes() < expectedLen) return false
        buffer!!.skipBytes(expectedLen)

        if (ver == 5 && reply == 0) {
            complete(ctx)
            return false
        } else {
            fail(ctx, IOException("SOCKS5 connect failed with reply: $reply"))
            return false
        }
    }

    private fun sendSocks4ConnectRequest(ctx: ChannelHandlerContext) {
        val dest = destinationAddress ?: run {
            fail(ctx, IOException("Missing destination address"))
            return
        }
        val addr = dest.address
        val userBytes = proxy.username.toByteArray(StandardCharsets.US_ASCII)
        val buf = ctx.alloc().buffer(9 + userBytes.size)
        buf.writeByte(4) // SOCKS4
        buf.writeByte(1) // CONNECT
        buf.writeShort(dest.port)

        if (addr is Inet4Address) {
            buf.writeBytes(addr.address)
            buf.writeBytes(userBytes)
            buf.writeByte(0)
        } else {
            // SOCKS4a domain resolution
            buf.writeByte(0)
            buf.writeByte(0)
            buf.writeByte(0)
            buf.writeByte(1)
            buf.writeBytes(userBytes)
            buf.writeByte(0)
            buf.writeBytes(dest.hostString.toByteArray(StandardCharsets.US_ASCII))
            buf.writeByte(0)
        }
        ctx.writeAndFlush(buf)
    }

    private fun processSocks4ConnectResponse(ctx: ChannelHandlerContext): Boolean {
        if (buffer!!.readableBytes() < 8) return false
        val vn = buffer!!.readUnsignedByte().toInt()
        val cd = buffer!!.readUnsignedByte().toInt()
        buffer!!.skipBytes(6)

        if (vn == 0 && cd == 90) {
            complete(ctx)
            return false
        } else {
            fail(ctx, IOException("SOCKS4 connect failed with code: $cd"))
            return false
        }
    }

    private fun complete(ctx: ChannelHandlerContext) {
        state = State.CONNECTED
        cancelTimeout()
        handshakePromise.trySuccess(null)
        connectPromise?.trySuccess()

        val pending = pendingWrites
        pendingWrites = null
        pending?.removeAndWriteAll()

        if (writePending) {
            writePending = false
            ctx.flush()
        }

        val unread = buffer
        buffer = null
        if (unread != null) {
            if (unread.isReadable) {
                ctx.fireChannelRead(unread)
            } else {
                unread.release()
            }
        }
        ctx.pipeline().remove(this)
    }

    private fun fail(ctx: ChannelHandlerContext, cause: Throwable) {
        if (state != State.FAILED) {
            state = State.FAILED
            cancelTimeout()
            handshakePromise.tryFailure(cause)
            connectPromise?.tryFailure(cause)
            pendingWrites?.removeAndFailAll(cause)
            pendingWrites = null
            releaseBuffer()
            ctx.close()
        }
    }
}
