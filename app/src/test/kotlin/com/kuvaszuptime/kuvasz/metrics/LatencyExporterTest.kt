package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import io.kotest.inspectors.forNone
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus

class LatencyExporterTest : ExporterTest("enabled-metrics-latency") {

    init {
        given("an enabled latency exporter") {

            `when`("the exporter is initialized") {
                appContext = ApplicationContext.run()

                val enabledMonitorWithLatency = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                    latencyHistoryEnabled = false,
                )
                // Enabled monitor without latency records
                createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled-no-latency",
                    url = "https://test.enabled.no-latency",
                    enabled = true,
                    latencyHistoryEnabled = true,
                )
                val disabledMonitorWithLatency = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = false,
                    latencyHistoryEnabled = true,
                )
                latencyLogRepository().insertLatencyForMonitor(enabledMonitorWithLatency.id, 100)
                latencyLogRepository().insertLatencyForMonitor(enabledMonitorWithLatency.id, 20)
                latencyLogRepository().insertLatencyForMonitor(disabledMonitorWithLatency.id, 50)

                restartAppContextWithMetrics()

                val registeredMeters = meterRegistry().meters

                then("it should register one meter for the enabled monitor with latency") {

                    val expectedMeter = registeredMeters.single()
                    expectedMeter.id.name shouldBe "kuvasz.monitor.latency.latest.milliseconds"
                    expectedMeter shouldHaveNameTag enabledMonitorWithLatency.name
                    expectedMeter shouldHaveUrlTag enabledMonitorWithLatency.url
                    expectedMeter shouldHaveValue 20.0
                }
            }

            `when`("there are new events for existing monitors after initialization") {
                appContext = ApplicationContext.run()

                val enabledMonitorWithLatency = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                    latencyHistoryEnabled = false,
                )
                val enabledMonitorWithoutLatency = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled-no-latency",
                    url = "https://test.enabled.no-latency",
                    enabled = true,
                    latencyHistoryEnabled = true,
                )
                val disabledMonitorWithLatency = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = false,
                    latencyHistoryEnabled = true,
                )
                latencyLogRepository().insertLatencyForMonitor(enabledMonitorWithLatency.id, 100)
                latencyLogRepository().insertLatencyForMonitor(enabledMonitorWithLatency.id, 20)
                latencyLogRepository().insertLatencyForMonitor(disabledMonitorWithLatency.id, 50)

                restartAppContextWithMetrics()

                // Simulating the events
                eventDispatcher().dispatch(MonitorUpEvent(enabledMonitorWithLatency, HttpStatus.OK, 30, null))
                eventDispatcher().dispatch(MonitorUpEvent(enabledMonitorWithoutLatency, HttpStatus.OK, 40, null))

                val registeredMeters = meterRegistry().meters

                then("it should register a new meter and update the existing one") {

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

            `when`("monitors are updated/deleted after initialization") {

                appContext = ApplicationContext.run()

                val enabledMonitorWithLatency = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled",
                    url = "https://test.enabled",
                    enabled = true,
                    latencyHistoryEnabled = false,
                )
                val anotherEnabledMonitorWithLatency = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-enabled-other",
                    url = "https://test.enabled.other",
                    enabled = true,
                    latencyHistoryEnabled = true,
                )
                val yetAnotherEnabledMonitorWithLatency = createMonitor(
                    getMonitorRepository(),
                    monitorName = "yet-another-enabled",
                    url = "https://yet.another.enabled",
                    enabled = true,
                    latencyHistoryEnabled = true,
                )
                val disabledMonitorWithLatency = createMonitor(
                    getMonitorRepository(),
                    monitorName = "test-disabled",
                    url = "https://test.disabled",
                    enabled = false,
                    latencyHistoryEnabled = true,
                )
                latencyLogRepository().insertLatencyForMonitor(enabledMonitorWithLatency.id, 100)
                latencyLogRepository().insertLatencyForMonitor(anotherEnabledMonitorWithLatency.id, 20)
                latencyLogRepository().insertLatencyForMonitor(disabledMonitorWithLatency.id, 50)
                latencyLogRepository().insertLatencyForMonitor(yetAnotherEnabledMonitorWithLatency.id, 10)

                restartAppContextWithMetrics()

                meterRegistry().meters shouldHaveSize 3

                // Simulating the events
                monitorCrudService().updateMonitor(enabledMonitorWithLatency.id, monitorDisableUpdate)
                monitorCrudService().updateMonitor(anotherEnabledMonitorWithLatency.id, monitorNameUpdate)
                monitorCrudService().updateMonitor(disabledMonitorWithLatency.id, monitorEnableUpdate)
                monitorCrudService().deleteMonitorById(yetAnotherEnabledMonitorWithLatency.id)

                val registeredMeters = meterRegistry().meters

                then("it should delete/recreate the meters of them") {

                    registeredMeters shouldHaveSize 2

                    // The meter for the disabled monitor should be removed
                    registeredMeters.forNone { it shouldHaveNameTag enabledMonitorWithLatency.name }
                    // The deleted monitor's meter should not exist
                    registeredMeters.forNone { it shouldHaveNameTag yetAnotherEnabledMonitorWithLatency.name }
                    // The meter for the enabled monitor should be updated with the new name
                    registeredMeters.forOne { updatedMonitor ->
                        updatedMonitor shouldHaveNameTag "new-name"
                        updatedMonitor shouldHaveValue 20.0
                    }
                    // The meter for the newly enabled monitor should be created
                    registeredMeters.forOne { newlyEnabledMonitor ->
                        newlyEnabledMonitor shouldHaveNameTag disabledMonitorWithLatency.name
                        newlyEnabledMonitor shouldHaveValue 50.0
                    }
                }
            }
        }
    }
}
