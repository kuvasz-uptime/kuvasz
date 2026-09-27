package com.kuvaszuptime.kuvasz.services.docker

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

private fun stats(cpu: Double?) = DockerContainerStats(
    cgroupVersion = DockerCgroupVersion.V2,
    cpuUsagePercent = cpu,
    memoryUsageBytes = 1_000,
    memoryLimitBytes = 8_000,
)

class DockerContainerStatsTest : StringSpec({

    "the CPU usage is rounded half up to the scale the metrics log keeps" {
        forAll(
            row(37.125, BigDecimal("37.13")),
            row(37.25, BigDecimal("37.25")),
            row(0.004, BigDecimal("0.00")),
        ) { cpu, expected ->
            stats(cpu).cpuUsagePercentDecimal shouldBe expected
        }
    }

    "a sample without a CPU reading has no CPU usage either" {
        stats(null).cpuUsagePercentDecimal.shouldBeNull()
    }
})
