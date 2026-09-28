package com.kuvaszuptime.kuvasz.database

import java.io.Closeable
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * A unix domain socket server listening on a PostgreSQL-style socket file (`.s.PGSQL.<port>`) in a fresh temporary
 * directory, handing every accepted connection to [handler] on its own thread.
 */
internal class TestUnixSocketServer(
    port: Int = 5432,
    private val handler: (SocketChannel) -> Unit,
) : AutoCloseable {

    val directory: Path = Files.createTempDirectory("kuvasz-pg")
    val path: Path = directory.resolve(".s.PGSQL.$port")
    val acceptedConnections = AtomicInteger(0)

    private val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "test-unix-socket-server").apply { isDaemon = true }
    }

    // Closed along with the server, since a held-open connection would otherwise block its handler thread for good
    private val connections = CopyOnWriteArrayList<Closeable>()

    private val server: ServerSocketChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        .apply { bind(UnixDomainSocketAddress.of(path)) }

    init {
        executor.submit {
            while (server.isOpen) {
                try {
                    val channel = server.accept().also(connections::add)
                    acceptedConnections.incrementAndGet()
                    executor.submit { channel.use { runCatching { handler(it) } } }
                } catch (_: IOException) {
                    return@submit
                }
            }
        }
    }

    override fun close() {
        server.close()
        connections.forEach { runCatching { it.close() } }
        executor.shutdownNow()
        Files.deleteIfExists(path)
        Files.deleteIfExists(directory)
    }
}
