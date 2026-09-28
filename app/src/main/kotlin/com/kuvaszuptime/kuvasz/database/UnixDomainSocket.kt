package com.kuvaszuptime.kuvasz.database

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketImpl
import java.net.SocketTimeoutException
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.file.Path
import java.util.concurrent.TimeUnit

// The JDK copies a heap array through a temporary native buffer of the same size, which it then caches for the thread,
// so a single large transfer would keep that much native memory around for good. Like the JDK's own socket, reads and
// writes are capped, which bounds that cache.
private const val MAX_TRANSFER_SIZE = 128 * 1024

/**
 * A [Socket] over a unix domain socket. The JDK only exposes those through [SocketChannel], while pgjdbc needs a
 * [Socket], so this overrides what pgjdbc relies on, and every other operation fails (see [UnsupportedSocketImpl]).
 *
 * The channel is non-blocking, and reads wait on a [Selector], which is what makes [setSoTimeout] work: pgjdbc depends
 * on reads timing out, e.g. to poll for pending messages or to validate a connection within a deadline.
 */
internal class UnixDomainSocket(
    private val path: Path,
    private val openSelector: () -> Selector = Selector::open,
) : Socket(UnsupportedSocketImpl()) {

    private val lock = Any()

    @Volatile
    private var connection: UnixDomainSocketConnection? = null

    @Volatile
    private var readTimeoutMs = 0

    private val openConnection: UnixDomainSocketConnection
        get() {
            if (isClosed) throw SocketException("Socket is closed")
            return connection ?: throw SocketException("Socket is not connected")
        }

    // The endpoint is the one pgjdbc resolves from the JDBC URL, which is meaningless here. Connecting needs no
    // timeout either, as it never waits (see UnixDomainSocketConnection.open).
    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        synchronized(lock) {
            if (isClosed) throw SocketException("Socket is closed")
            if (connection != null) throw SocketException("Already connected")
            connection = UnixDomainSocketConnection.open(path, openSelector, owner = this) { readTimeoutMs }
        }
    }

    override fun isConnected(): Boolean = connection != null

    override fun getRemoteSocketAddress(): SocketAddress? =
        if (isConnected) UnixDomainSocketAddress.of(path) else null

    override fun getInputStream(): InputStream = openConnection.input

    override fun getOutputStream(): OutputStream = openConnection.output

    override fun setSoTimeout(timeout: Int) {
        if (isClosed) throw SocketException("Socket is closed")
        require(timeout >= 0) { "timeout < 0" }
        readTimeoutMs = timeout
    }

    override fun getSoTimeout(): Int {
        if (isClosed) throw SocketException("Socket is closed")
        return readTimeoutMs
    }

    // TCP only options, which have no meaning for a unix domain socket
    override fun setTcpNoDelay(on: Boolean) = Unit

    override fun getTcpNoDelay(): Boolean = false

    override fun setKeepAlive(on: Boolean) = Unit

    override fun getKeepAlive(): Boolean = false

    override fun getSendBufferSize(): Int = openConnection.channel.getOption(StandardSocketOptions.SO_SNDBUF)

    override fun setSendBufferSize(size: Int) {
        openConnection.channel.setOption(StandardSocketOptions.SO_SNDBUF, size)
    }

    override fun getReceiveBufferSize(): Int = openConnection.channel.getOption(StandardSocketOptions.SO_RCVBUF)

    override fun setReceiveBufferSize(size: Int) {
        openConnection.channel.setOption(StandardSocketOptions.SO_RCVBUF, size)
    }

    // Serialized with connect(), so a connection being opened concurrently can't be missed. A pending read doesn't
    // hold the lock, so closing still interrupts it.
    override fun close() {
        synchronized(lock) {
            try {
                connection?.close()
            } finally {
                super.close()
            }
        }
    }

    override fun toString(): String = "UnixDomainSocket[path=$path]"
}

/**
 * Stands in for the TCP implementation [Socket] would create otherwise, so that every operation [UnixDomainSocket]
 * doesn't override fails loudly, instead of silently acting on an unconnected TCP socket. [Socket] creates its
 * implementation before using it for anything, so failing that is enough, while the rest fail just in case.
 */
internal class UnsupportedSocketImpl : SocketImpl() {

    private fun unsupported(): Nothing = throw SocketException("Not supported by a unix domain socket")

    override fun create(stream: Boolean) = unsupported()

    override fun connect(host: String?, port: Int) = unsupported()

    override fun connect(address: InetAddress?, port: Int) = unsupported()

    override fun connect(address: SocketAddress?, timeout: Int) = unsupported()

    override fun bind(host: InetAddress?, port: Int) = unsupported()

    override fun listen(backlog: Int) = unsupported()

    override fun accept(s: SocketImpl?) = unsupported()

    override fun getInputStream(): InputStream = unsupported()

    override fun getOutputStream(): OutputStream = unsupported()

    override fun available(): Int = unsupported()

    override fun close() = unsupported()

    override fun sendUrgentData(data: Int) = unsupported()

    override fun setOption(optID: Int, value: Any?) = unsupported()

    override fun getOption(optID: Int): Any = unsupported()
}

/**
 * The channel and the selectors of a connected [UnixDomainSocket]. Its streams close the [owner] socket, just like the
 * streams of a regular socket.
 */
private class UnixDomainSocketConnection private constructor(
    val channel: SocketChannel,
    private val readSelector: Selector,
    private val writeSelector: Selector,
    readTimeoutMs: () -> Int,
    owner: Closeable,
) : Closeable {

    val input: InputStream = ChannelInputStream(channel, readSelector, readTimeoutMs, owner)
    val output: OutputStream = ChannelOutputStream(channel, writeSelector, owner)

    // Closing the selectors first wakes up a thread waiting on them, which then fails instead of waiting again
    override fun close() = closeAll(listOf(readSelector, writeSelector, channel))

    companion object {

        /** Opens the channel along with its selectors, closing whatever was opened already if anything fails. */
        fun open(
            path: Path,
            openSelector: () -> Selector,
            owner: Closeable,
            readTimeoutMs: () -> Int,
        ): UnixDomainSocketConnection {
            val opened = mutableListOf<Closeable>()
            var connected = false
            try {
                val channel = SocketChannel.open(StandardProtocolFamily.UNIX).also(opened::add)
                channel.configureBlocking(false)
                // Connected in non-blocking mode, because a blocking connect waits indefinitely on Linux while the
                // server's backlog is full, e.g. when PostgreSQL is overloaded. This fails at once instead, as a unix
                // domain socket, unlike a TCP one, is never left pending.
                if (!channel.connect(UnixDomainSocketAddress.of(path))) throw ConnectException("Connection pending")
                val readSelector = openSelector().also(opened::add)
                channel.register(readSelector, SelectionKey.OP_READ)
                val writeSelector = openSelector().also(opened::add)
                channel.register(writeSelector, SelectionKey.OP_WRITE)
                return UnixDomainSocketConnection(channel, readSelector, writeSelector, readTimeoutMs, owner)
                    .also { connected = true }
            } finally {
                // On any failure, unchecked ones included, as the pool would leak them again with every retry
                if (!connected) opened.forEach { resource -> runCatching { resource.close() } }
            }
        }
    }
}

private class ChannelInputStream(
    private val channel: SocketChannel,
    private val selector: Selector,
    private val timeoutMs: () -> Int,
    private val owner: Closeable,
) : InputStream() {

    override fun read(): Int {
        val byte = ByteArray(1)
        return if (read(byte, 0, 1) == -1) -1 else byte[0].toUByte().toInt()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val buffer = ByteBuffer.wrap(b, off, len).limit(off + minOf(len, MAX_TRANSFER_SIZE))
        if (len == 0) return 0
        val timeout = timeoutMs()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout.toLong())
        var read = channel.read(buffer)
        while (read == 0) {
            selector.await(remainingMs(timeout, deadline))
            read = channel.read(buffer)
        }
        return read
    }

    // Like with a regular socket, a timed out read leaves the socket usable
    private fun remainingMs(timeout: Int, deadline: Long): Long {
        if (timeout == 0) return 0
        val remainingNanos = deadline - System.nanoTime()
        if (remainingNanos <= 0) throw SocketTimeoutException("Read timed out")
        // Rounded up, as zero would mean waiting forever
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos) + 1
    }

    override fun close() = owner.close()
}

private class ChannelOutputStream(
    private val channel: SocketChannel,
    private val selector: Selector,
    private val owner: Closeable,
) : OutputStream() {

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        val buffer = ByteBuffer.wrap(b, off, len)
        val end = off + len
        while (buffer.position() < end) {
            buffer.limit(buffer.position() + minOf(end - buffer.position(), MAX_TRANSFER_SIZE))
            if (channel.write(buffer) == 0) selector.await(0)
        }
    }

    override fun close() = owner.close()
}

/**
 * Waits until the channel is ready or the timeout elapses (0 meaning no timeout). A selector closed along with the
 * socket, even while waiting on it, surfaces as an IOException, since that's what the callers of a socket expect.
 *
 * Like the I/O of a regular socket, it ignores interrupts. A selector returns immediately for an interrupted thread,
 * which would make the callers spin, so the interrupt flag is cleared while waiting, then restored.
 */
private fun Selector.await(timeoutMs: Long) {
    val interrupted = Thread.interrupted()
    try {
        select(timeoutMs)
        selectedKeys().clear()
    } catch (e: ClosedSelectorException) {
        throw SocketException("Socket is closed", e)
    } finally {
        if (interrupted) Thread.currentThread().interrupt()
    }
}

/** Closes all the resources, even if some of them fail, rethrowing the first failure with the rest suppressed. */
internal fun closeAll(resources: List<Closeable>) {
    var failure: IOException? = null
    for (resource in resources) {
        try {
            resource.close()
        } catch (e: IOException) {
            failure?.addSuppressed(e) ?: run { failure = e }
        }
    }
    failure?.let { throw it }
}
