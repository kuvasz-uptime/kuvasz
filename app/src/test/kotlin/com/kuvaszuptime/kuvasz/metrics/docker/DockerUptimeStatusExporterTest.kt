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

class DockerUptimeStatusExporterTest : DockerExporterTest("enabled-metrics-docker-uptime-status") {

    init {
        given("an enabled Docker uptime status exporter") {

            `when`("the exporter is initialized") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                // Enabled monitor without status
                createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )
                dockerUptimeEventRepository().insertFromMonitorEvent(
                    DockerMonitorUpEvent(enabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )
                dockerUptimeEventRepository().insertFromMonitorEvent(
                    DockerMonitorUpEvent(disabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with status") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.docker.uptime.status"
                    expectedMeter shouldHaveNameTag enabledMonitorWithStatus.name
                    expectedMeter shouldHaveValue 1.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = testAppContext()

                val enabledMonitorWithStatus = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val enabledMonitorWithoutStatus = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled-no-status",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )

                val firstMonitorPreviousEvent = dockerUptimeEventRepository().insertFromMonitorEvent(
                    DockerMonitorUpEvent(enabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )
                dockerUptimeEventRepository().insertFromMonitorEvent(
                    DockerMonitorUpEvent(disabledMonitorWithStatus, previousEvent = null, latencyInMs = 10)
                )

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(
                    DockerMonitorDownEvent(
                        enabledMonitorWithStatus,
                        previousEvent = firstMonitorPreviousEvent,
                        error = "timeout",
                    )
                )
                eventDispatcher().dispatch(
                    DockerMonitorUpEvent(enabledMonitorWithoutStatus, previousEvent = null, latencyInMs = 5)
                )

                val registeredMeters = meterRegistry().meters

                then("it should register a new meter and update the existing one") {

                    registeredMeters shouldHaveSize 2

                    registeredMeters.forOne { withPreviousStatus ->
                        withPreviousStatus shouldHaveNameTag enabledMonitorWithStatus.name
                        withPreviousStatus shouldHaveValue 0.0
                    }
                    registeredMeters.forOne { withoutPreviousStatus ->
                        withoutPreviousStatus shouldHaveNameTag enabledMonitorWithoutStatus.name
                        withoutPreviousStatus shouldHaveValue 1.0
                    }
                }
            }

            `when`("monitors are updated/deleted after initialization") {

                appContext = testAppContext()

                val enabledMonitorWithStatus = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled",
                    enabled = true,
                )
                val anotherEnabledMonitorWithStatus = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-enabled-other",
                    enabled = true,
                )
                val yetAnotherEnabledMonitorWithStatus = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    enabled = true,
                )
                val disabledMonitorWithStatus = createDockerMonitor(
                    dockerMonitorRepository(),
                    monitorName = "test-disabled",
                    enabled = false,
                )

                dockerUptimeEventRepository().insertFromMonitorEvent(
                    DockerMonitorUpEvent(enabledMonitorWithStatus, null, latencyInMs = 10)
                )
                dockerUptimeEventRepository().insertFromMonitorEvent(
                    DockerMonitorUpEvent(anotherEnabledMonitorWithStatus, null, latencyInMs = 10)
                )
                dockerUptimeEventRepository().insertFromMonitorEvent(
                    DockerMonitorUpEvent(yetAnotherEnabledMonitorWithStatus, null, latencyInMs = 10)
                )
                dockerUptimeEventRepository().insertFromMonitorEvent(
                    DockerMonitorUpEvent(disabledMonitorWithStatus, null, latencyInMs = 10)
                )

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                dockerMonitorActions().updateMonitor(enabledMonitorWithStatus.id, monitorDisableUpdate)
                dockerMonitorActions().updateMonitor(anotherEnabledMonitorWithStatus.id, monitorNameUpdate)
                dockerMonitorActions().updateMonitor(disabledMonitorWithStatus.id, monitorEnableUpdate)
                dockerMonitorActions().deleteMonitorById(yetAnotherEnabledMonitorWithStatus.id)

                val registeredMeters = meterRegistry().meters

                then("it should delete/recreate the meters of them") {

                    registeredMeters shouldHaveSize 2

                    registeredMeters.forNone { it shouldHaveNameTag enabledMonitorWithStatus.name }
                    registeredMeters.forNone { it shouldHaveNameTag yetAnotherEnabledMonitorWithStatus.name }
                    registeredMeters.forOne { updatedMonitor ->
                        updatedMonitor shouldHaveNameTag "new-name"
                        updatedMonitor shouldHaveValue 1.0
                    }
                    registeredMeters.forOne { newlyEnabledMonitor ->
                        newlyEnabledMonitor shouldHaveNameTag disabledMonitorWithStatus.name
                        newlyEnabledMonitor shouldHaveValue 1.0
                    }
                }
            }
        }
    }
}
