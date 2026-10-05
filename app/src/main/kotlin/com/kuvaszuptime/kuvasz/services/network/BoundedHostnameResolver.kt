package com.kuvaszuptime.kuvasz.services.network

import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

fun interface HostnameResolver {
    fun resolve(host: String): InetAddress
}

@Singleton
class SystemHostnameResolver : HostnameResolver {
    override fun resolve(host: String): InetAddress = InetAddress.getByName(host)
}

/**
 * Resolves host names within a timeout, which the JDK can't do on its own: `InetSocketAddress(host, port)` resolves
 * eagerly on the calling thread with no timeout, and `Socket.connect()`'s timeout only covers the TCP handshake.
 */
@Singleton
class BoundedHostnameResolver(private val hostnameResolver: HostnameResolver) : AutoCloseable {

    private val resolverExecutor: ExecutorService = Executors.newCachedThreadPool(DaemonThreadFactory)

    // Tracks the currently outstanding name resolution per host, so a slow or black-holed DNS lookup
    // can't create a new resolver thread on every check: concurrent and subsequent checks for the
    // same host share the single in-flight lookup instead.
    private val inFlightResolutions = ConcurrentHashMap<String, Future<InetAddress>>()

    /**
     * @throws IOException if [host] can't be resolved, or not within [timeoutMs].
     */
    fun resolve(host: String, timeoutMs: Int): InetAddress {
        val future = inFlightResolutions.computeIfAbsent(host) { hostToResolve ->
            resolverExecutor.submit<InetAddress> { hostnameResolver.resolve(hostToResolve) }
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

    @PreDestroy
    override fun close() {
        resolverExecutor.shutdownNow()
    }

    private object DaemonThreadFactory : ThreadFactory {
        private val counter = AtomicInteger(0)

        override fun newThread(runnable: Runnable): Thread =
            Thread(runnable, "hostname-resolver-${counter.incrementAndGet()}").apply { isDaemon = true }
    }
}
