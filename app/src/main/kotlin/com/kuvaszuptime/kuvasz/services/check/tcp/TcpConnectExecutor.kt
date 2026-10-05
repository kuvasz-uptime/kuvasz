package com.kuvaszuptime.kuvasz.services.check.tcp

import com.kuvaszuptime.kuvasz.services.network.BoundedHostnameResolver
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
class TcpConnectExecutor(private val hostnameResolver: BoundedHostnameResolver) {

    fun execute(host: String, port: Int, timeoutMs: Int): TcpCheckResult {
        val start = System.nanoTime()

        val address = try {
            hostnameResolver.resolve(host, timeoutMs)
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

    companion object {
        private const val MIN_CONNECT_TIMEOUT_MS = 1
    }
}
