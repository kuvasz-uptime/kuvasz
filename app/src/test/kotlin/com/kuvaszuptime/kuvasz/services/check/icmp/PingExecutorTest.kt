package com.kuvaszuptime.kuvasz.services.check.icmp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class PingExecutorTest : StringSpec({

    val executor = SystemPingExecutor()

    "parsePingOutput returns correct result for a typical Linux ping output" {
        val output = """
            PING example.com (93.184.216.34) 56(84) bytes of data.
            64 bytes from 93.184.216.34: icmp_seq=1 ttl=56 time=11.2 ms
            64 bytes from 93.184.216.34: icmp_seq=2 ttl=56 time=10.8 ms
            64 bytes from 93.184.216.34: icmp_seq=3 ttl=56 time=11.1 ms

            --- example.com ping statistics ---
            3 packets transmitted, 3 received, 0% packet loss, time 2003ms
            rtt min/avg/max/mdev = 10.800/11.033/11.200/0.172 ms
        """.trimIndent()

        val result = executor.parsePingOutput(output, packetsSent = 3)

        result.packetsSent shouldBe 3
        result.packetsReceived shouldBe 3
        result.avgLatencyMs shouldBe 11
        result.packetLossPercentage shouldBe 0
        result.rawOutput shouldBe output
        result.isOutputRecognized shouldBe true
    }

    "parsePingOutput returns correct result for a Linux ping output with 100% packet loss" {
        val output = """
            PING 192.0.2.1 (192.0.2.1) 56(84) bytes of data.

            --- 192.0.2.1 ping statistics ---
            3 packets transmitted, 0 received, 100% packet loss, time 2002ms
        """.trimIndent()

        val result = executor.parsePingOutput(output, packetsSent = 3)

        result.packetsSent shouldBe 3
        result.packetsReceived shouldBe 0
        result.avgLatencyMs.shouldBeNull()
        result.packetLossPercentage shouldBe 100
        result.rawOutput shouldBe output
        result.isOutputRecognized shouldBe true
    }

    "parsePingOutput returns isOutputRecognized=false for unrecognized output" {
        val output = "ping: unknown host does-not-exist.invalid"

        val result = executor.parsePingOutput(output, packetsSent = 3)

        result.packetsSent shouldBe 3
        result.packetsReceived shouldBe 0
        result.avgLatencyMs.shouldBeNull()
        result.packetLossPercentage shouldBe 100
        result.rawOutput shouldBe output
        result.isOutputRecognized shouldBe false
    }

    "parsePingOutput returns isOutputRecognized=false for empty output" {
        val result = executor.parsePingOutput("", packetsSent = 3)

        result.isOutputRecognized shouldBe false
        result.rawOutput shouldBe ""
        result.packetsReceived shouldBe 0
    }
})
