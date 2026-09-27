package com.kuvaszuptime.kuvasz.metrics.docker

import com.kuvaszuptime.kuvasz.metrics.DockerExporterTest
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorUpEvent
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class DockerLatencyExporterTest : DockerExporterTest("enabled-metrics-docker-latency") {

    init {
        given("an enabled Docker latency exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                // Enabled monitor without latency records
                createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled-no-latency",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                dockerMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 100, stats = null)
                dockerMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 20, stats = null)
                dockerMetricsLogRepository().insertLog(disabledMonitorWithLatency.id, latencyMs = 50, stats = null)

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with latency") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.docker.api.latency.latest.milliseconds"
                    expectedMeter shouldHaveNameTag enabledMonitorWithLatency.name
                    expectedMeter shouldHaveValue 20.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val enabledMonitorWithoutLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled-no-latency",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                dockerMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 90, stats = null)
                dockerMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 20, stats = null)
                dockerMetricsLogRepository().insertLog(disabledMonitorWithLatency.id, latencyMs = 50, stats = null)

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    DockerMonitorUpEvent(enabledMonitorWithLatency, null, latencyInMs = 30)
                )
                eventDispatcher().dispatch(
                    DockerMonitorUpEvent(enabledMonitorWithoutLatency, null, latencyInMs = 40)
                )

                val registeredMeters = meterRegistry().meters

                then("it should register a new meter and update the existing one") {

                    registeredMeters shouldHaveSize 2

                    registeredMeters.forOne { withPreviousLatency ->
                        withPreviousLatency shouldHaveNameTag enabledMonitorWithLatency.name
                        withPreviousLatency shouldHaveValue 30.0
                    }
                    registeredMeters.forOne { withoutPreviousLatency ->
                        withoutPreviousLatency shouldHaveNameTag enabledMonitorWithoutLatency.name
                        withoutPreviousLatency shouldHaveValue 40.0
                    }
                }
            }

            `when`("there is an up event with null latency") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                dockerMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 50, stats = null)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    DockerMonitorUpEvent(enabledMonitorWithLatency, null, latencyInMs = null)
                )

                then("it should not update the existing meter") {
                    meterRegistry().meters.single() shouldHaveValue 50.0
                }
            }

            `when`("there is a down event carrying a latency (the daemon answered, the container is stopped)") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                dockerMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 50, stats = null)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    DockerMonitorDownEvent(
                        enabledMonitorWithLatency,
                        error = "The container exited (137)",
                        previousEvent = null,
                        latencyInMs = 120,
                    )
                )

                then("it should update the existing meter with the measured latency") {
                    meterRegistry().meters.single() shouldHaveValue 120.0
                }
            }

            `when`("there is a down event without a latency (connection failed)") {
                appContext = testAppContext()

                val enabledMonitorWithLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                dockerMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 50, stats = null)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 1

                eventDispatcher().dispatch(
                    DockerMonitorDownEvent(enabledMonitorWithLatency, error = "refused", previousEvent = null)
                )

                then("it should not update the existing meter") {
                    meterRegistry().meters.single() shouldHaveValue 50.0
                }
            }

            `when`("monitors are updated/deleted after initialization") {

                appContext = testAppContext()

                val enabledMonitorWithLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val anotherEnabledMonitorWithLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled-other",
                    enabled = true,
                )
                val yetAnotherEnabledMonitorWithLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    enabled = true,
                )
                val disabledMonitorWithLatency = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                dockerMetricsLogRepository().insertLog(enabledMonitorWithLatency.id, latencyMs = 100, stats = null)
                dockerMetricsLogRepository()
                    .insertLog(anotherEnabledMonitorWithLatency.id, latencyMs = 20, stats = null)
                dockerMetricsLogRepository().insertLog(disabledMonitorWithLatency.id, latencyMs = 50, stats = null)
                dockerMetricsLogRepository()
                    .insertLog(yetAnotherEnabledMonitorWithLatency.id, latencyMs = 10, stats = null)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                dockerMonitorActions().updateMonitor(enabledMonitorWithLatency.id, monitorDisableUpdate)
                dockerMonitorActions().updateMonitor(anotherEnabledMonitorWithLatency.id, monitorNameUpdate)
                dockerMonitorActions().updateMonitor(disabledMonitorWithLatency.id, monitorEnableUpdate)
                dockerMonitorActions().deleteMonitorById(yetAnotherEnabledMonitorWithLatency.id)

                val registeredMeters = meterRegistry().meters

                then("it should delete/recreate the meters of them") {

                    registeredMeters shouldHaveSize 2

                    registeredMeters.forNone { it shouldHaveNameTag enabledMonitorWithLatency.name }
                    registeredMeters.forNone { it shouldHaveNameTag yetAnotherEnabledMonitorWithLatency.name }
                    registeredMeters.forOne { updatedMonitor ->
                        updatedMonitor shouldHaveNameTag "new-name"
                        updatedMonitor shouldHaveValue 20.0
                    }
                    registeredMeters.forOne { newlyEnabledMonitor ->
                        newlyEnabledMonitor shouldHaveNameTag disabledMonitorWithLatency.name
                        newlyEnabledMonitor shouldHaveValue 50.0
                    }
                }
            }
        }
    }
}
