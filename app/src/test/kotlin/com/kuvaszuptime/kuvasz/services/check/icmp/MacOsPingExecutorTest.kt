package com.kuvaszuptime.kuvasz.services.check.icmp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class MacOsPingExecutorTest : StringSpec({

    val executor = MacOsPingExecutor()

    "parsePingOutput returns correct result for a typical Linux ping output" {
        val output = """
            PING kuvasz-uptime.dev (185.199.109.153): 56 data bytes
            64 bytes from 185.199.109.153: icmp_seq=0 ttl=63 time=14.714 ms
            64 bytes from 185.199.109.153: icmp_seq=1 ttl=63 time=28.656 ms
            
            --- kuvasz-uptime.dev ping statistics ---
            2 packets transmitted, 2 packets received, 0.0% packet loss
            round-trip min/avg/max/stddev = 14.714/21.685/28.656/0.816 ms
        """.trimIndent()

        val result = executor.parsePingOutput(output)

        result.packetsSent shouldBe 2
        result.packetsReceived shouldBe 2
        result.avgLatencyMs shouldBe 22
        result.packetLossPercentage shouldBe 0
        result.rawOutput shouldBe output
        result.isOutputRecognized shouldBe true
    }

    "parsePingOutput returns correct result for a Linux ping output with 100% packet loss" {
        val output = """
            PING kuvasz-uptime.dev (185.199.109.153): 56 data bytes
            
            --- kuvasz-uptime.dev ping statistics ---
            2 packets transmitted, 0 packets received, 100.0% packet loss
            round-trip min/avg/max/stddev = 14.714/21.685/28.656 ms
        """.trimIndent()

        val result = executor.parsePingOutput(output)

        result.packetsSent shouldBe 2
        result.packetsReceived shouldBe 0
        result.avgLatencyMs.shouldBeNull()
        result.packetLossPercentage shouldBe 100
        result.rawOutput shouldBe output
        result.isOutputRecognized shouldBe true
    }

    "parsePingOutput returns isOutputRecognized=false for unrecognized output" {
        val output = "ping: unknown host does-not-exist.invalid"

        val result = executor.parsePingOutput(output)

        result.packetsSent shouldBe 0
        result.packetsReceived shouldBe 0
        result.avgLatencyMs.shouldBeNull()
        result.packetLossPercentage shouldBe 100
        result.rawOutput shouldBe output
        result.isOutputRecognized shouldBe false
    }

    "parsePingOutput returns isOutputRecognized=false for empty output" {
        val result = executor.parsePingOutput("")

        result.isOutputRecognized shouldBe false
        result.rawOutput shouldBe ""
        result.packetsReceived shouldBe 0
    }
})
