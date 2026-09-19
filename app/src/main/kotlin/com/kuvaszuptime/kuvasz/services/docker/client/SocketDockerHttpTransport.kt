package com.kuvaszuptime.kuvasz.services.docker.client

import com.kuvaszuptime.kuvasz.config.DockerHostConfig
import com.kuvaszuptime.kuvasz.services.docker.DockerConnectionFactory
import com.kuvaszuptime.kuvasz.services.docker.DockerDaemonAddress
import com.kuvaszuptime.kuvasz.services.docker.DockerHost
import io.micronaut.context.annotation.Requires
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The hand-rolled [DockerHttpTransport]: a socket per request, no pooling, `Connection: close`.
 *
 * docker-java's zerodep transport would be the obvious alternative, but it reaches unix sockets through JNA, which
 * ships no musl native, and Kuvasz's image is Alpine based. This keeps the runtime pure JDK: a unix domain socket via
 * JEP 380, and a plain (optionally TLS wrapped) socket for TCP.
 */
@Singleton
@Requires(bean = DockerHostConfig::class)
class SocketDockerHttpTransport(
    private val connectionFactory: DockerConnectionFactory,
) : DockerHttpTransport, AutoCloseable {

    private companion object {
        const val UNIX_SOCKET_HOST_HEADER = "localhost"
    }

    // A unix domain socket read cannot be bounded with SO_TIMEOUT, and on TCP it only bounds a single read, so the
    // whole exchange runs on a worker the caller interrupts once the budget is spent. It has to be a virtual thread:
    // interrupting one closes the socket or channel it is blocked on, while a platform thread would only give up on
    // the unix channel and stay stuck on a TCP or TLS socket.
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    override fun get(host: DockerHost, path: String, timeoutMs: Int): DockerHttpResponse {
        val future = executor.submit<DockerHttpResponse> {
            connectionFactory.open(host, timeoutMs).use { DockerHttpFraming.exchange(it, path, host.hostHeader) }
        }
        return try {
            future.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (ex: TimeoutException) {
            throw timedOut(timeoutMs, ex)
        } catch (ex: ExecutionException) {
            throw ex.toIOException(timeoutMs)
        } finally {
            // A no-op once the exchange has finished. Otherwise (a timeout, or the caller itself being interrupted) it
            // stops the worker instead of leaving it blocked on the daemon.
            future.cancel(true)
        }
    }

    private fun ExecutionException.toIOException(timeoutMs: Int): IOException = when (val cause = cause) {
        // The socket's own connect and read timeouts use the same budget and only start once the request is underway,
        // so when one of them beats the deadline above, the request has run out of time all the same
        is SocketTimeoutException -> timedOut(timeoutMs, this)
        // The cause carries the useful message ("Connection refused", the TLS failure, ...)
        else -> IOException(cause?.message ?: message, this)
    }

    private fun timedOut(timeoutMs: Int, cause: Exception): IOException =
        IOException("the request timed out after ${timeoutMs}ms", cause)

    private val DockerHost.hostHeader: String
        get() = when (val address = address) {
            is DockerDaemonAddress.UnixSocket -> UNIX_SOCKET_HOST_HEADER
            is DockerDaemonAddress.Tcp -> "${address.host}:${address.port}"
        }

    @PreDestroy
    override fun close() {
        executor.shutdownNow()
    }
}
