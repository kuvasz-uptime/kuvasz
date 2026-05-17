package com.kuvaszuptime.kuvasz.services.check.icmp

import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data class PingResult(
    val packetsSent: Int,
    val packetsReceived: Int,
    val avgLatencyMs: Int?,
    val rawOutput: String,
    val isOutputRecognized: Boolean,
) {
    @Suppress("MagicNumber")
    val packetLossPercentage: Int
        get() = if (packetsSent == 0) 100 else (packetsSent - packetsReceived) * 100 / packetsSent
}

interface PingExecutor {
    val receivedPattern: Regex
    val rttPattern: Regex
    fun prepareCommand(host: String, count: Int, timeoutSeconds: Int): List<String>
    fun execute(host: String, count: Int, timeoutSeconds: Int): PingResult
}

@Singleton
class SystemPingExecutor : PingExecutor {

    private val logger: Logger = LoggerFactory.getLogger(SystemPingExecutor::class.java)

    override val receivedPattern = Regex("""(\d+) received""")
    override val rttPattern = Regex("""rtt .* = [\d.]+/([\d.]+)/""")

    override fun prepareCommand(host: String, count: Int, timeoutSeconds: Int): List<String> =
        listOf("ping", "-c", count.toString(), "-W", timeoutSeconds.toString(), host)

    override fun execute(host: String, count: Int, timeoutSeconds: Int): PingResult {
        val command = prepareCommand(host, count, timeoutSeconds)
        logger.debug("Running ping: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        return parsePingOutput(output, count)
    }

    fun parsePingOutput(output: String, packetsSent: Int): PingResult {
        logger.debug("Ping output:\n$output")

        val receivedMatch = receivedPattern.find(output)
        val packetsReceived = receivedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val avgLatencyMs = rttPattern.find(output)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()

        return PingResult(
            packetsSent = packetsSent,
            packetsReceived = packetsReceived,
            avgLatencyMs = avgLatencyMs.takeIf { packetsReceived > 0 },
            rawOutput = output,
            isOutputRecognized = receivedMatch != null,
        )
    }
}

/**
 * Intended to be used only in a dev environment.
 * The ping command on MacOS is slightly different to the one under Linux, because it:
 * - has a different output format
 * - uses milliseconds for the -W argument
 */
@Singleton
@Requires(env = ["macos"])
@Primary
class LocalMacOsPingExecutor : SystemPingExecutor() {
    override val receivedPattern = Regex("""(\d+) packets received""")
    override val rttPattern = Regex("""round-trip .* = [\d.]+/([\d.]+)/""")

    @Suppress("MagicNumber")
    override fun prepareCommand(host: String, count: Int, timeoutSeconds: Int): List<String> =
        listOf("ping", "-c", count.toString(), "-W", (timeoutSeconds * 1000).toString(), host)
}
