package com.kuvaszuptime.kuvasz.metrics.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.DockerExporterTest
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorUpEvent
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime

private val CREATED_AT = OffsetDateTime.parse("2026-10-07T06:59:12.914442Z")

class DockerRestartCountExporterTest : DockerExporterTest("enabled-metrics-docker-restart-count") {

    private fun recordRestartCount(monitor: DockerMonitorRecord, restartCount: Int) {
        dockerUptimeEventRepository().insertFromMonitorEvent(
            DockerMonitorUpEvent(
                monitor = monitor,
                previousEvent = null,
                latencyInMs = 10,
                restartCount = restartCount,
                containerCreatedAt = CREATED_AT,
            )
        )
    }

    init {
        given("an enabled Docker restart count exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                // Not gated on the metrics history, since the count is recorded on every check
                val monitor = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled",
                    metricsHistoryEnabled = false,
                )
                createDockerMonitor(dockerMonitorRepository(), monitorName = "test-enabled-never-inspected")
                val disabledMonitor = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                recordRestartCount(monitor, restartCount = 3)
                recordRestartCount(disabledMonitor, restartCount = 7)

                restartAppContextWithMetrics()

                then("it should register one meter, seeded from the latest uptime event") {
                    val expectedMeter = meterRegistry().meters.single()
                    expectedMeter.id.name shouldBe "kuvasz.docker.restart.count"
                    expectedMeter shouldHaveNameTag monitor.name
                    expectedMeter shouldHaveTargetTag "local/my-app"
                    expectedMeter shouldHaveValue 3.0
                }
            }

            `when`("an up event carries a restart count") {
                appContext = testAppContext()

                val monitor = createDockerMonitor(dockerMonitorRepository(), monitorName = "test-enabled")
                recordRestartCount(monitor, restartCount = 3)

                restartAppContextWithMetrics()

                eventDispatcher().dispatch(DockerMonitorUpEvent(monitor, null, latencyInMs = 10, restartCount = 4))

                then("it should update the meter") {
                    meterRegistry().meters.single() shouldHaveValue 4.0
                }
            }

            `when`("a down event carries a restart count") {
                appContext = testAppContext()

                val monitor = createDockerMonitor(dockerMonitorRepository(), monitorName = "test-enabled")
                recordRestartCount(monitor, restartCount = 3)

                restartAppContextWithMetrics()

                eventDispatcher().dispatch(
                    DockerMonitorDownEvent(monitor, "The container is restarting", null, restartCount = 5)
                )

                then("it should update the meter too, since the container was inspected") {
                    meterRegistry().meters.single() shouldHaveValue 5.0
                }
            }

            `when`("a check could not inspect the container") {
                appContext = testAppContext()

                val monitor = createDockerMonitor(dockerMonitorRepository(), monitorName = "test-enabled")
                recordRestartCount(monitor, restartCount = 3)

                restartAppContextWithMetrics()

                eventDispatcher().dispatch(
                    DockerMonitorDownEvent(monitor, """The Docker host "local" cannot be reached""", null)
                )

                then("it should keep the last known count instead of removing the meter") {
                    meterRegistry().meters.single() shouldHaveValue 3.0
                }
            }

            `when`("a monitor without any known restart count gets one") {
                appContext = testAppContext()

                val monitor = createDockerMonitor(dockerMonitorRepository(), monitorName = "test-enabled")

                restartAppContextWithMetrics()
                meterRegistry().meters.shouldBeEmpty()

                eventDispatcher().dispatch(DockerMonitorUpEvent(monitor, null, latencyInMs = 10, restartCount = 0))

                then("it should register the meter") {
                    val expectedMeter = meterRegistry().meters.single()
                    expectedMeter shouldHaveNameTag monitor.name
                    expectedMeter shouldHaveValue 0.0
                }
            }
        }
    }
}
