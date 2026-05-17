package com.kuvaszuptime.kuvasz.services.check.icmp

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import kotlin.math.roundToInt

data class PingResult(
    val packetsSent: Int,
    val packetsReceived: Int,
    val packetLossPercentage: Int,
    val avgLatencyMs: Int?,
    val rawOutput: String,
    val isOutputRecognized: Boolean,
)

interface PingExecutor {
    val receivedPattern: Regex
    val rttPattern: Regex
    fun prepareCommand(host: String, count: Int, timeoutSeconds: Int): List<String>

    @Suppress("MagicNumber")
    fun parsePingOutput(output: String): PingResult {
        val receivedMatch = receivedPattern.find(output)
        val transmitted = receivedMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val received = receivedMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        val lossPercentage = receivedMatch?.groupValues?.getOrNull(3)?.toIntOrNull() ?: 100

        val avgLatencyMs = rttPattern.find(output)?.groupValues?.get(1)?.toDoubleOrNull()?.roundToInt()

        return PingResult(
            packetsSent = transmitted,
            packetsReceived = received,
            packetLossPercentage = lossPercentage,
            avgLatencyMs = avgLatencyMs.takeIf { received > 0 },
            rawOutput = output,
            isOutputRecognized = receivedMatch != null,
        )
    }

    fun execute(host: String, count: Int, timeoutSeconds: Int): PingResult {
        val command = prepareCommand(host, count, timeoutSeconds)

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        return parsePingOutput(output)
    }
}

@Singleton
@Requires(notEnv = ["macos"])
class BusyboxPingExecutor : PingExecutor {

    override val receivedPattern =
        Regex("""(\d+) packets transmitted, (\d+) packets received, (\d+)% packet loss""")
    override val rttPattern = Regex("""round-trip min/avg/max = [\d.]+/([\d.]+)/""")

    override fun prepareCommand(host: String, count: Int, timeoutSeconds: Int): List<String> =
        listOf("ping", "-c", count.toString(), "-W", timeoutSeconds.toString(), host)
}

/**
 * Intended to be used only in a dev environment.
 * The ping command on MacOS is slightly different to the one under Linux, because it:
 * - has a different output format
 * - uses milliseconds for the -W argument
 */
@Singleton
@Requires(env = ["macos"])
class MacOsPingExecutor : PingExecutor {

    override val receivedPattern =
        Regex("""(\d+) packets transmitted, (\d+) packets received, (\d+)(?:\.\d+)?% packet loss""")
    override val rttPattern = Regex("""round-trip min/avg/max/stddev = [\d.]+/([\d.]+)/""")

    @Suppress("MagicNumber")
    override fun prepareCommand(host: String, count: Int, timeoutSeconds: Int): List<String> =
        listOf("ping", "-c", count.toString(), "-W", (timeoutSeconds * 1000).toString(), host)
}
