package com.kuvaszuptime.kuvasz.services.check.tcp

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.services.network.BoundedHostnameResolver
import com.kuvaszuptime.kuvasz.services.proxy.ConfiguredProxy
import com.kuvaszuptime.kuvasz.services.proxy.ProxyTunnel
import com.kuvaszuptime.kuvasz.util.elapsedMsSince
import jakarta.inject.Singleton
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

data class TcpCheckResult(
    val isConnected: Boolean,
    val latencyMs: Int?,
    val error: String?,
)

@Singleton
class TcpConnectExecutor(
    private val hostnameResolver: BoundedHostnameResolver,
    private val proxyTunnel: ProxyTunnel,
) {

    /**
     * Connects to [host]:[port], either directly or through [proxy].
     *
     * Through a proxy, [host] is resolved by the proxy, and the latency covers reaching the proxy and the
     * establishment of the tunnel, as there is no way to tell apart the proxy's own connection to the target.
     */
    fun execute(host: String, port: Int, timeoutMs: Int, proxy: ConfiguredProxy? = null): TcpCheckResult =
        if (proxy == null) connectDirectly(host, port, timeoutMs) else connectThrough(proxy, host, port, timeoutMs)

    private fun connectDirectly(host: String, port: Int, timeoutMs: Int): TcpCheckResult {
        val start = System.nanoTime()

        val address = try {
            hostnameResolver.resolve(host, timeoutMs)
        } catch (ex: IOException) {
            return failure(ex.errorMessage)
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
            failure(ex.errorMessage)
        }
    }

    private fun connectThrough(proxy: ConfiguredProxy, host: String, port: Int, timeoutMs: Int): TcpCheckResult {
        val start = System.nanoTime()
        return try {
            proxyTunnel.open(proxy, host, port, timeoutMs).use {
                TcpCheckResult(isConnected = true, latencyMs = elapsedMsSince(start), error = null)
            }
        } catch (ex: IOException) {
            failure(Messages.proxyCheckFailed(proxy.name, ex.errorMessage))
        }
    }

    private fun failure(error: String) = TcpCheckResult(isConnected = false, latencyMs = null, error = error)

    private val IOException.errorMessage: String
        get() = message ?: javaClass.simpleName

    companion object {
        private const val MIN_CONNECT_TIMEOUT_MS = 1
    }
}
