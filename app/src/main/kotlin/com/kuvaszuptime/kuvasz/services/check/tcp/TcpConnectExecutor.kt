package com.kuvaszuptime.kuvasz.services.check.tcp

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
class TcpConnectExecutor {

    fun execute(host: String, port: Int, timeoutMs: Int): TcpCheckResult =
        try {
            Socket().use { socket ->
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val latencyMs = ((System.nanoTime() - start) / NANOS_IN_MILLI).toInt()
                TcpCheckResult(isConnected = true, latencyMs = latencyMs, error = null)
            }
        } catch (ex: IOException) {
            TcpCheckResult(
                isConnected = false,
                latencyMs = null,
                error = ex.message ?: ex.javaClass.simpleName,
            )
        }

    companion object {
        private const val NANOS_IN_MILLI = 1_000_000L
    }
}
