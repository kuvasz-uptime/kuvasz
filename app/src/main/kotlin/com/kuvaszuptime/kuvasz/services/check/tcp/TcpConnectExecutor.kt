package com.kuvaszuptime.kuvasz.services.check.tcp

import jakarta.annotation.PreDestroy
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

data class TcpCheckResult(
    val isConnected: Boolean,
    val latencyMs: Int?,
    val error: String?,
)

@Singleton
class TcpConnectExecutor internal constructor(
    private val resolver: (String) -> InetAddress,
    private val resolverExecutor: ExecutorService,
) : AutoCloseable {

    @Inject
    constructor() : this(
        resolver = InetAddress::getByName,
        resolverExecutor = Executors.newCachedThreadPool(DaemonThreadFactory),
    )

    // Tracks the currently outstanding name resolution per host, so a slow or black-holed DNS lookup
    // can't create a new resolver thread on every check: concurrent and subsequent checks for the
    // same host share the single in-flight lookup instead.
    private val inFlightResolutions = ConcurrentHashMap<String, Future<InetAddress>>()

    fun execute(host: String, port: Int, timeoutMs: Int): TcpCheckResult {
        val start = System.nanoTime()

        // Name resolution is bounded explicitly, because InetSocketAddress(host, port) would otherwise
        // resolve eagerly on this thread with no timeout - Socket.connect()'s timeout only covers the
        // TCP handshake, not the lookup.
        val address = try {
            resolveWithTimeout(host, timeoutMs)
        } catch (ex: IOException) {
            return TcpCheckResult(isConnected = false, latencyMs = null, error = ex.message ?: ex.javaClass.simpleName)
        }

        return try {
            Socket().use { socket ->
                // The resolution has already consumed part of the budget, so the handshake gets the rest.
                val remainingMs = (timeoutMs - elapsedMsSince(start)).coerceAtLeast(MIN_CONNECT_TIMEOUT_MS)
                val connectStart = System.nanoTime()
                socket.connect(InetSocketAddress(address, port), remainingMs)
                TcpCheckResult(isConnected = true, latencyMs = elapsedMsSince(connectStart), error = null)
            }
        } catch (ex: IOException) {
            TcpCheckResult(isConnected = false, latencyMs = null, error = ex.message ?: ex.javaClass.simpleName)
        }
    }

    private fun resolveWithTimeout(host: String, timeoutMs: Int): InetAddress {
        val future = inFlightResolutions.computeIfAbsent(host) { hostToResolve ->
            resolverExecutor.submit<InetAddress> { resolver(hostToResolve) }
        }
        return try {
            future.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        } catch (ex: ExecutionException) {
            val cause = ex.cause
            throw IOException(cause?.message ?: ex.message, ex)
        } catch (ex: TimeoutException) {
            throw IOException("DNS resolution timed out after ${timeoutMs}ms", ex)
        } finally {
            // Only evict finished lookups, so a later check re-resolves (honoring the JVM's own DNS cache)
            if (future.isDone) {
                inFlightResolutions.remove(host, future)
            }
        }
    }

    private fun elapsedMsSince(startNanos: Long): Int = ((System.nanoTime() - startNanos) / NANOS_IN_MILLI).toInt()

    @PreDestroy
    override fun close() {
        resolverExecutor.shutdownNow()
    }

    private object DaemonThreadFactory : ThreadFactory {
        private val counter = AtomicInteger(0)

        override fun newThread(runnable: Runnable): Thread =
            Thread(runnable, "tcp-dns-resolver-${counter.incrementAndGet()}").apply { isDaemon = true }
    }

    companion object {
        private const val NANOS_IN_MILLI = 1_000_000L
        private const val MIN_CONNECT_TIMEOUT_MS = 1
    }
}
