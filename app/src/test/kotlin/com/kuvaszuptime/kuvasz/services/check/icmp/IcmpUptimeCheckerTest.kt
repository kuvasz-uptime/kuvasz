package com.kuvaszuptime.kuvasz.services.check.icmp

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.IcmpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.subscribers.TestSubscriber

@MicronautTest(startApplication = false)
class IcmpUptimeCheckerTest(
    private val uptimeChecker: IcmpUptimeChecker,
    private val monitorRepository: IcmpMonitorRepository,
    private val uptimeEventRepository: IcmpUptimeEventRepository,
    private val latencyLogRepository: IcmpMetricsLogRepository,
    private val eventDispatcher: EventDispatcher,
    private val pingExecutor: PingExecutor,
) : DatabaseBehaviorSpec() {

    @MockBean(PingExecutor::class)
    fun pingExecutorMock(): PingExecutor = mockk()

    init {
        given("IcmpUptimeChecker") {

            `when`("all packets are received") {
                val monitor = createIcmpMonitor(
                    monitorRepository,
                    packetCount = 3,
                    packetLossThreshold = 100,
                )
                val pingExecutorMock = getMock(pingExecutor)
                every {
                    pingExecutorMock.execute(monitor.host, monitor.packetCount, monitor.timeoutSeconds)
                } returns PingResult(
                    packetsSent = 3,
                    packetsReceived = 3,
                    packetLossPercentage = 0,
                    avgLatencyMs = 12,
                    rawOutput = "",
                    isOutputRecognized = true
                )

                val upSubscriber = TestSubscriber<IcmpMonitorUpEvent>()
                val downSubscriber = TestSubscriber<IcmpMonitorDownEvent>()
                eventDispatcher.subscribeToIcmpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
                eventDispatcher.subscribeToIcmpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("an UP event should be dispatched") {
                    upSubscriber.awaitCount(1)
                    upSubscriber.values().shouldHaveSize(1)
                    val upEvent = upSubscriber.values().first()
                    upEvent.monitor.id shouldBe monitor.id
                    upEvent.uptimeStatus shouldBe UptimeStatus.UP
                    upEvent.latencyInMs shouldBe 12
                    upEvent.packetLossPercentage shouldBe 0
                }

                then("no DOWN event should be dispatched") {
                    downSubscriber.values().shouldHaveSize(0)
                }

                then("the event should be persisted") {
                    val events = uptimeEventRepository.fetchByMonitorId(monitor.id)
                    events.shouldHaveSize(1)
                    events.first().status shouldBe UptimeStatus.UP
                }
            }

            `when`("no packets are received and threshold is 100") {
                val monitor = createIcmpMonitor(
                    monitorRepository,
                    packetCount = 3,
                    packetLossThreshold = 100,
                )
                val pingExecutorMock = getMock(pingExecutor)
                every {
                    pingExecutorMock.execute(monitor.host, monitor.packetCount, monitor.timeoutSeconds)
                } returns PingResult(
                    packetsSent = 3,
                    packetsReceived = 0,
                    packetLossPercentage = 100,
                    avgLatencyMs = null,
                    rawOutput = "",
                    isOutputRecognized = true
                )

                val upSubscriber = TestSubscriber<IcmpMonitorUpEvent>()
                val downSubscriber = TestSubscriber<IcmpMonitorDownEvent>()
                eventDispatcher.subscribeToIcmpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
                eventDispatcher.subscribeToIcmpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should be dispatched") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1)
                    val downEvent = downSubscriber.values().first()
                    downEvent.monitor.id shouldBe monitor.id
                    downEvent.uptimeStatus shouldBe UptimeStatus.DOWN
                    downEvent.packetLossPercentage shouldBe 100
                }

                then("no UP event should be dispatched") {
                    upSubscriber.values().shouldHaveSize(0)
                }

                then("a DOWN event should be persisted") {
                    val events = uptimeEventRepository.fetchByMonitorId(monitor.id)
                    events.shouldHaveSize(1)
                    events.first().status shouldBe UptimeStatus.DOWN
                }
            }

            `when`("50% packets are lost but threshold is 100") {
                val monitor = createIcmpMonitor(
                    monitorRepository,
                    packetCount = 4,
                    packetLossThreshold = 100,
                )
                val pingExecutorMock = getMock(pingExecutor)
                every {
                    pingExecutorMock.execute(monitor.host, monitor.packetCount, monitor.timeoutSeconds)
                } returns PingResult(
                    packetsSent = 4,
                    packetsReceived = 2,
                    packetLossPercentage = 50,
                    avgLatencyMs = 20,
                    rawOutput = "",
                    isOutputRecognized = true
                )

                val upSubscriber = TestSubscriber<IcmpMonitorUpEvent>()
                eventDispatcher.subscribeToIcmpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }

                uptimeChecker.check(monitor)

                then("an UP event should be dispatched because loss is below threshold") {
                    upSubscriber.awaitCount(1)
                    upSubscriber.values().shouldHaveSize(1)
                    upSubscriber.values().first().packetLossPercentage shouldBe 50
                }
            }

            `when`("50% packets are lost and threshold is 50") {
                val monitor = createIcmpMonitor(
                    monitorRepository,
                    packetCount = 4,
                    packetLossThreshold = 50,
                )
                val pingExecutorMock = getMock(pingExecutor)
                every {
                    pingExecutorMock.execute(monitor.host, monitor.packetCount, monitor.timeoutSeconds)
                } returns PingResult(
                    packetsSent = 4,
                    packetsReceived = 2,
                    packetLossPercentage = 50,
                    avgLatencyMs = null,
                    rawOutput = "",
                    isOutputRecognized = true
                )

                val downSubscriber = TestSubscriber<IcmpMonitorDownEvent>()
                eventDispatcher.subscribeToIcmpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should be dispatched because loss is at threshold") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1)
                    downSubscriber.values().first().packetLossPercentage shouldBe 50
                }
            }

            `when`("a DOWN check occurs but failureCountThreshold is 2 (first occurrence)") {
                val monitor = createIcmpMonitor(
                    monitorRepository,
                    packetCount = 3,
                    packetLossThreshold = 100,
                    failureCountThreshold = 2L,
                )
                val pingExecutorMock = getMock(pingExecutor)
                every {
                    pingExecutorMock.execute(monitor.host, monitor.packetCount, monitor.timeoutSeconds)
                } returns PingResult(
                    packetsSent = 3,
                    packetsReceived = 0,
                    packetLossPercentage = 100,
                    avgLatencyMs = null,
                    rawOutput = "",
                    isOutputRecognized = true
                )

                val downSubscriber = TestSubscriber<IcmpMonitorDownEvent>()
                eventDispatcher.subscribeToIcmpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("no DOWN event should be dispatched on first occurrence") {
                    downSubscriber.values().shouldHaveSize(0)
                }
            }

            `when`("the ping output could not be parsed and packetLossThreshold is met") {
                val monitor = createIcmpMonitor(
                    monitorRepository,
                    packetCount = 3,
                    packetLossThreshold = 100,
                )
                val pingExecutorMock = getMock(pingExecutor)
                val rawOutput = "ping: unknown host does-not-exist.invalid"
                every {
                    pingExecutorMock.execute(monitor.host, monitor.packetCount, monitor.timeoutSeconds)
                } returns PingResult(
                    packetsSent = 3,
                    packetsReceived = 0,
                    packetLossPercentage = 100,
                    avgLatencyMs = null,
                    rawOutput = rawOutput,
                    isOutputRecognized = false,
                )

                val downSubscriber = TestSubscriber<IcmpMonitorDownEvent>()
                eventDispatcher.subscribeToIcmpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should be dispatched with rawOutput as the error") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1)
                    val downEvent = downSubscriber.values().first()
                    downEvent.error shouldBe rawOutput
                    downEvent.packetLossPercentage shouldBe 100
                }
            }

            `when`("execute() throws a RuntimeException") {
                val monitor = createIcmpMonitor(
                    monitorRepository,
                    packetCount = 3,
                    packetLossThreshold = 100,
                )
                val pingExecutorMock = getMock(pingExecutor)
                every {
                    pingExecutorMock.execute(monitor.host, monitor.packetCount, monitor.timeoutSeconds)
                } throws RuntimeException("Unexpected ping failure")

                val upSubscriber = TestSubscriber<IcmpMonitorUpEvent>()
                val downSubscriber = TestSubscriber<IcmpMonitorDownEvent>()
                eventDispatcher.subscribeToIcmpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
                eventDispatcher.subscribeToIcmpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should be dispatched with the exception message as the error") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1)
                    val downEvent = downSubscriber.values().first()
                    downEvent.monitor.id shouldBe monitor.id
                    downEvent.error shouldBe "Unexpected ping failure"
                    downEvent.packetLossPercentage shouldBe 100
                }

                then("no UP event should be dispatched") {
                    upSubscriber.values().shouldHaveSize(0)
                }
            }

            `when`("the monitor has metricsHistoryEnabled=false and a check runs") {
                val monitor = createIcmpMonitor(
                    monitorRepository,
                    packetCount = 3,
                    packetLossThreshold = 100,
                    metricsHistoryEnabled = false,
                )
                val pingExecutorMock = getMock(pingExecutor)
                every {
                    pingExecutorMock.execute(monitor.host, monitor.packetCount, monitor.timeoutSeconds)
                } returns PingResult(
                    packetsSent = 3,
                    packetsReceived = 3,
                    packetLossPercentage = 0,
                    avgLatencyMs = 10,
                    rawOutput = "",
                    isOutputRecognized = true,
                )

                uptimeChecker.check(monitor)

                then("no metrics log should be inserted") {
                    latencyLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()
                }
            }
        }
    }
}
