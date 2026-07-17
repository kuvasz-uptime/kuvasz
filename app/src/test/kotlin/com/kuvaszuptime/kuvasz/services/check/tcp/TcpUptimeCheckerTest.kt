package com.kuvaszuptime.kuvasz.services.check.tcp

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.TcpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpUptimeEventRepository
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
class TcpUptimeCheckerTest(
    private val uptimeChecker: TcpUptimeChecker,
    private val monitorRepository: TcpMonitorRepository,
    private val uptimeEventRepository: TcpUptimeEventRepository,
    private val metricsLogRepository: TcpMetricsLogRepository,
    private val eventDispatcher: EventDispatcher,
    private val connectExecutor: TcpConnectExecutor,
    private val checkScheduler: TcpCheckScheduler,
) : DatabaseBehaviorSpec() {

    @MockBean(TcpConnectExecutor::class)
    fun connectExecutorMock(): TcpConnectExecutor = mockk()

    init {
        afterContainer { checkScheduler.removeAllChecks() }

        given("TcpUptimeChecker") {

            `when`("the connection succeeds") {
                val monitor = createTcpMonitor(monitorRepository)
                val mock = getMock(connectExecutor)
                every {
                    mock.execute(monitor.host, monitor.port, monitor.timeoutMs)
                } returns TcpCheckResult(isConnected = true, latencyMs = 12, error = null)

                val upSubscriber = TestSubscriber<TcpMonitorUpEvent>()
                val downSubscriber = TestSubscriber<TcpMonitorDownEvent>()
                eventDispatcher.subscribeToTcpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
                eventDispatcher.subscribeToTcpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("an UP event should be dispatched with the latency") {
                    upSubscriber.awaitCount(1)
                    upSubscriber.values().shouldHaveSize(1)
                    val upEvent = upSubscriber.values().first()
                    upEvent.monitor.id shouldBe monitor.id
                    upEvent.uptimeStatus shouldBe UptimeStatus.UP
                    upEvent.latencyInMs shouldBe 12
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

            `when`("the connection fails") {
                val monitor = createTcpMonitor(monitorRepository)
                val mock = getMock(connectExecutor)
                every {
                    mock.execute(monitor.host, monitor.port, monitor.timeoutMs)
                } returns TcpCheckResult(isConnected = false, latencyMs = null, error = "Connection refused")

                val upSubscriber = TestSubscriber<TcpMonitorUpEvent>()
                val downSubscriber = TestSubscriber<TcpMonitorDownEvent>()
                eventDispatcher.subscribeToTcpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
                eventDispatcher.subscribeToTcpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should be dispatched with the error") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1)
                    val downEvent = downSubscriber.values().first()
                    downEvent.monitor.id shouldBe monitor.id
                    downEvent.uptimeStatus shouldBe UptimeStatus.DOWN
                    downEvent.error shouldBe "Connection refused"
                }

                then("no UP event should be dispatched") {
                    upSubscriber.values().shouldHaveSize(0)
                }
            }

            `when`("the connection succeeds but latency exceeds the threshold") {
                val monitor = createTcpMonitor(monitorRepository, latencyThresholdMs = 50)
                val mock = getMock(connectExecutor)
                every {
                    mock.execute(monitor.host, monitor.port, monitor.timeoutMs)
                } returns TcpCheckResult(isConnected = true, latencyMs = 120, error = null)

                val downSubscriber = TestSubscriber<TcpMonitorDownEvent>()
                eventDispatcher.subscribeToTcpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should be dispatched") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1)
                    downSubscriber.values().first().uptimeStatus shouldBe UptimeStatus.DOWN
                }
            }

            `when`("the connection succeeds and latency is below the threshold") {
                val monitor = createTcpMonitor(monitorRepository, latencyThresholdMs = 500)
                val mock = getMock(connectExecutor)
                every {
                    mock.execute(monitor.host, monitor.port, monitor.timeoutMs)
                } returns TcpCheckResult(isConnected = true, latencyMs = 30, error = null)

                val upSubscriber = TestSubscriber<TcpMonitorUpEvent>()
                eventDispatcher.subscribeToTcpMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }

                uptimeChecker.check(monitor)

                then("an UP event should be dispatched") {
                    upSubscriber.awaitCount(1)
                    upSubscriber.values().shouldHaveSize(1)
                }
            }

            `when`("a DOWN check occurs but failureCountThreshold is 2 (first occurrence)") {
                val monitor = createTcpMonitor(monitorRepository, failureCountThreshold = 2L)
                val mock = getMock(connectExecutor)
                every {
                    mock.execute(monitor.host, monitor.port, monitor.timeoutMs)
                } returns TcpCheckResult(isConnected = false, latencyMs = null, error = "Connection refused")

                val downSubscriber = TestSubscriber<TcpMonitorDownEvent>()
                eventDispatcher.subscribeToTcpMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("no DOWN event should be dispatched on first occurrence") {
                    downSubscriber.values().shouldHaveSize(0)
                }
            }

            `when`("the monitor has metricsHistoryEnabled=false and a check runs") {
                val monitor = createTcpMonitor(monitorRepository, metricsHistoryEnabled = false)
                val mock = getMock(connectExecutor)
                every {
                    mock.execute(monitor.host, monitor.port, monitor.timeoutMs)
                } returns TcpCheckResult(isConnected = true, latencyMs = 10, error = null)

                uptimeChecker.check(monitor)

                then("no metrics log should be inserted") {
                    metricsLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()
                }
            }
        }
    }
}
