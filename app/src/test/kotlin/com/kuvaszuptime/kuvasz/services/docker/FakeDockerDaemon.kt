package com.kuvaszuptime.kuvasz.services.docker

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

/**
 * A minimal stand-in for a Docker daemon. It writes a canned raw HTTP response and closes, which is all the client
 * needs to see, and it is driven over the same four transports the production code supports: unix socket, plaintext
 * TCP, TLS and mTLS.
 *
 * Purpose-built rather than MockServer, because the specs need to control the exact framing on the wire (chunked vs
 * content-length vs truncated) and because MockServer's hardcoded ports are shared across this suite.
 */
internal sealed class FakeDockerDaemon : AutoCloseable {

    val receivedRequests = CopyOnWriteArrayList<String>()

    /** The number of connections the client hung up on while the daemon was holding them open. */
    val hangUps = AtomicInteger(0)

    protected val executor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "fake-docker-daemon").apply { isDaemon = true }
    }

    // Closed along with the daemon, since a held-open connection would otherwise block its serving thread for good
    protected val connections = CopyOnWriteArrayList<Closeable>()

    protected fun serve(input: InputStream, output: OutputStream, rawResponse: String?, holdOpen: Boolean) {
        val head = StringBuilder()
        val reader = input.bufferedReader(StandardCharsets.US_ASCII)
        var line = reader.readLine()
        while (!line.isNullOrEmpty()) {
            head.appendLine(line)
            line = reader.readLine()
        }
        receivedRequests.add(head.toString())

        if (holdOpen) {
            // Never answer, so the caller has to time the exchange out, and wait for it to hang up. An abrupt close
            // can surface as a reset rather than as the end of the stream, which counts all the same.
            runCatching { reader.read() }
            hangUps.incrementAndGet()
            return
        }
        output.write(rawResponse.orEmpty().toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    protected fun closeConnections() {
        connections.forEach { runCatching { it.close() } }
    }

    class Tcp(
        private val rawResponse: String? = null,
        sslContext: SSLContext? = null,
        needClientAuth: Boolean = false,
        private val holdOpen: Boolean = false,
    ) : FakeDockerDaemon() {

        private val server: ServerSocket = if (sslContext != null) {
            (sslContext.serverSocketFactory.createServerSocket(0) as SSLServerSocket)
                .apply { this.needClientAuth = needClientAuth }
        } else {
            ServerSocket(0)
        }

        val port: Int get() = server.localPort

        init {
            executor.submit {
                while (!server.isClosed) {
                    // accept() throws as soon as the server is closed, which is the loop's exit condition. A TLS
                    // handshake only starts with the first read, so a failed one ends that connection in serve().
                    try {
                        val socket = server.accept().also(connections::add)
                        executor.submit {
                            socket.use { serve(it.getInputStream(), it.getOutputStream(), rawResponse, holdOpen) }
                        }
                    } catch (_: IOException) {
                        return@submit
                    }
                }
            }
        }

        override fun close() {
            server.close()
            closeConnections()
            executor.shutdownNow()
        }
    }

    class UnixSocket(
        private val rawResponse: String? = null,
        private val holdOpen: Boolean = false,
    ) : FakeDockerDaemon() {

        private val directory: Path = Files.createTempDirectory("kuvasz-docker")
        val path: Path = directory.resolve("docker.sock")

        private val server: ServerSocketChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            .apply { bind(UnixDomainSocketAddress.of(path)) }

        init {
            executor.submit {
                while (server.isOpen) {
                    try {
                        val channel = server.accept().also(connections::add)
                        executor.submit {
                            channel.use {
                                serve(Channels.newInputStream(it), Channels.newOutputStream(it), rawResponse, holdOpen)
                            }
                        }
                    } catch (_: IOException) {
                        return@submit
                    }
                }
            }
        }

        override fun close() {
            server.close()
            closeConnections()
            executor.shutdownNow()
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }
}
