package com.kuvaszuptime.kuvasz.metrics.docker

import com.kuvaszuptime.kuvasz.metrics.DockerExporterTest
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorUpEvent
import com.kuvaszuptime.kuvasz.services.docker.DockerCgroupVersion
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStats
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

private fun statsOf(cpu: Double? = null, memory: Long? = null) = DockerContainerStats(
    cgroupVersion = DockerCgroupVersion.V2,
    cpuUsagePercent = cpu,
    memoryUsageBytes = memory,
    memoryLimitBytes = 8L * 1024 * 1024 * 1024,
)

class DockerCpuUsageExporterTest : DockerExporterTest("enabled-metrics-docker-cpu-usage") {

    init {
        given("an enabled Docker CPU usage exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val sampledMonitor = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-sampled",
                    enabled = true,
                )
                // A monitor without a metrics history is never sampled, so it has nothing to export
                createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-no-history",
                    enabled = true,
                    metricsHistoryEnabled = false,
                )
                createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                dockerMetricsLogRepository().insertLog(
                    monitorId = sampledMonitor.id,
                    latencyMs = 10,
                    stats = statsOf(cpu = 12.50),
                )

                restartAppContextWithMetrics()

                then("it should register one meter, seeded from the last sample") {
                    val expectedMeter = meterRegistry().meters.single()
                    expectedMeter.id.name shouldBe "kuvasz.docker.cpu.usage.latest.percent"
                    expectedMeter shouldHaveNameTag sampledMonitor.name
                    expectedMeter shouldHaveValue 12.5
                }
            }

            // The whole point of the decimal gauge: an idling container must not read as a flat zero
            `when`("the container is using less than a whole percent") {
                appContext = testAppContext()

                val sampledMonitor = createDockerMonitor(dockerMonitorRepository(), monitorName = "test-idle")
                dockerMetricsLogRepository().insertLog(
                    monitorId = sampledMonitor.id,
                    latencyMs = 10,
                    stats = statsOf(cpu = 0.40),
                )

                restartAppContextWithMetrics()

                then("the gauge should keep the fraction") {
                    meterRegistry().meters.single() shouldHaveValue 0.4
                }
            }

            `when`("an up event carries a new sample") {
                appContext = testAppContext()

                val sampledMonitor = createDockerMonitor(dockerMonitorRepository(), monitorName = "test-sampled")
                dockerMetricsLogRepository().insertLog(
                    monitorId = sampledMonitor.id,
                    latencyMs = 10,
                    stats = statsOf(cpu = 12.50),
                )

                restartAppContextWithMetrics()
                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    DockerMonitorUpEvent(
                        sampledMonitor,
                        previousEvent = null,
                        latencyInMs = 10,
                        cpuUsagePercent = BigDecimal("37.25"),
                    )
                )

                then("it should update the meter") {
                    meterRegistry().meters.single() shouldHaveValue 37.25
                }
            }

            `when`("an up event carries no sample") {
                appContext = testAppContext()

                val sampledMonitor = createDockerMonitor(dockerMonitorRepository(), monitorName = "test-sampled")
                dockerMetricsLogRepository().insertLog(
                    monitorId = sampledMonitor.id,
                    latencyMs = 10,
                    stats = statsOf(cpu = 12.50),
                )

                restartAppContextWithMetrics()

                eventDispatcher().dispatch(
                    DockerMonitorUpEvent(sampledMonitor, previousEvent = null, latencyInMs = 10)
                )

                then("it should leave the meter on the last known value") {
                    meterRegistry().meters.single() shouldHaveValue 12.5
                }
            }

            // A stopped container has no live cgroup, so a down event never carries one
            `when`("a down event arrives") {
                appContext = testAppContext()

                val sampledMonitor = createDockerMonitor(dockerMonitorRepository(), monitorName = "test-sampled")
                dockerMetricsLogRepository().insertLog(
                    monitorId = sampledMonitor.id,
                    latencyMs = 10,
                    stats = statsOf(cpu = 12.50),
                )

                restartAppContextWithMetrics()

                eventDispatcher().dispatch(
                    DockerMonitorDownEvent(
                        sampledMonitor,
                        error = "The container exited (137)",
                        previousEvent = null,
                        latencyInMs = 10,
                    )
                )

                then("it should leave the meter untouched") {
                    meterRegistry().meters.single() shouldHaveValue 12.5
                }
            }
        }
    }
}

class DockerMemoryUsageExporterTest : DockerExporterTest("enabled-metrics-docker-memory-usage") {

    init {
        given("an enabled Docker memory usage exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val sampledMonitor = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-sampled",
                    enabled = true,
                )
                createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-no-history",
                    enabled = true,
                    metricsHistoryEnabled = false,
                )
                dockerMetricsLogRepository().insertLog(
                    monitorId = sampledMonitor.id,
                    latencyMs = 10,
                    stats = statsOf(memory = 1_048_576),
                )

                restartAppContextWithMetrics()

                then("it should register one meter, seeded from the last sample") {
                    val expectedMeter = meterRegistry().meters.single()
                    expectedMeter.id.name shouldBe "kuvasz.docker.memory.usage.latest.bytes"
                    expectedMeter shouldHaveNameTag sampledMonitor.name
                    expectedMeter shouldHaveValue 1_048_576.0
                }
            }

            // A multi-gigabyte container overflows an Int, which is why the measurement is a Long
            `when`("the container uses more memory than an Int can hold") {
                appContext = testAppContext()

                val sampledMonitor = createDockerMonitor(dockerMonitorRepository(), monitorName = "test-big")
                val sixGiB = 6L * 1024 * 1024 * 1024
                dockerMetricsLogRepository().insertLog(
                    monitorId = sampledMonitor.id,
                    latencyMs = 10,
                    stats = statsOf(memory = sixGiB),
                )

                restartAppContextWithMetrics()

                then("the gauge should report it without overflowing") {
                    meterRegistry().meters.single() shouldHaveValue sixGiB.toDouble()
                }
            }

            `when`("an up event carries a new sample") {
                appContext = testAppContext()

                val sampledMonitor = createDockerMonitor(dockerMonitorRepository(), monitorName = "test-sampled")
                dockerMetricsLogRepository().insertLog(
                    monitorId = sampledMonitor.id,
                    latencyMs = 10,
                    stats = statsOf(memory = 1_048_576),
                )

                restartAppContextWithMetrics()
                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    DockerMonitorUpEvent(
                        sampledMonitor,
                        previousEvent = null,
                        latencyInMs = 10,
                        memoryUsageBytes = 2_097_152,
                    )
                )

                then("it should update the meter") {
                    meterRegistry().meters.single() shouldHaveValue 2_097_152.0
                }
            }

            `when`("an up event carries no sample") {
                appContext = testAppContext()

                val sampledMonitor = createDockerMonitor(dockerMonitorRepository(), monitorName = "test-sampled")
                dockerMetricsLogRepository().insertLog(
                    monitorId = sampledMonitor.id,
                    latencyMs = 10,
                    stats = statsOf(memory = 1_048_576),
                )

                restartAppContextWithMetrics()

                eventDispatcher().dispatch(
                    DockerMonitorUpEvent(sampledMonitor, previousEvent = null, latencyInMs = 10)
                )

                then("it should leave the meter on the last known value") {
                    meterRegistry().meters.single() shouldHaveValue 1_048_576.0
                }
            }
        }
    }
}
