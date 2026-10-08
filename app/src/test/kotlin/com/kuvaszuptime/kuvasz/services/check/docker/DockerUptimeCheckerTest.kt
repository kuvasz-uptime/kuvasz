package com.kuvaszuptime.kuvasz.services.check.docker

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.models.events.DockerContainerRestartedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.DockerMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DockerUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.docker.DockerCgroupVersion
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerState
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStats
import com.kuvaszuptime.kuvasz.services.docker.DockerContainerStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerHealthStatus
import com.kuvaszuptime.kuvasz.services.docker.DockerInspectResult
import com.kuvaszuptime.kuvasz.services.docker.DockerStatsResult
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import com.kuvaszuptime.kuvasz.testutils.DOCKER_HOSTS
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.subscribers.TestSubscriber
import java.math.BigDecimal
import java.time.OffsetDateTime

private const val LATENCY_MS = 12
private const val IMAGE = "nginx:1.27"
private const val RESTART_COUNT = 2
private val CREATED_AT = OffsetDateTime.parse("2026-10-07T06:59:12.914442Z")

private fun inspected(
    status: DockerContainerStatus,
    health: DockerHealthStatus = DockerHealthStatus.NONE,
    exitCode: Int? = null,
    image: String = IMAGE,
    restartCount: Int = RESTART_COUNT,
    createdAt: OffsetDateTime = CREATED_AT,
) = DockerInspectResult.Inspected(
    state = DockerContainerState(
        status = status,
        health = health,
        exitCode = exitCode,
        oomKilled = false,
        failingStreak = null,
        image = image,
        restartCount = restartCount,
        createdAt = createdAt,
    ),
    latencyMs = LATENCY_MS,
)

private val SAMPLE = DockerContainerStats(
    cgroupVersion = DockerCgroupVersion.V2,
    cpuUsagePercent = 37.25,
    memoryUsageBytes = 1_048_576,
    memoryLimitBytes = 8_388_608,
)

@MicronautTest(startApplication = false, environments = [DOCKER_HOSTS])
class DockerUptimeCheckerTest(
    private val uptimeChecker: DockerUptimeChecker,
    private val monitorRepository: DockerMonitorRepository,
    private val uptimeEventRepository: DockerUptimeEventRepository,
    private val metricsLogRepository: DockerMetricsLogRepository,
    private val eventDispatcher: EventDispatcher,
    private val apiClient: DockerApiClient,
    private val checkScheduler: DockerCheckScheduler,
) : DatabaseBehaviorSpec() {

    @MockBean(DockerApiClient::class)
    fun apiClientMock(): DockerApiClient = mockk()

    init {
        afterContainer { checkScheduler.removeAllChecks() }

        given("DockerUptimeChecker") {

            `when`("the container is running") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")
                val mock = getMock(apiClient)
                every {
                    mock.inspectContainer(any(), monitor.container, monitor.timeoutMs)
                } returns inspected(DockerContainerStatus.RUNNING)
                every { mock.containerStats(any(), any(), any()) } returns DockerStatsResult.Measured(SAMPLE)

                val upSubscriber = TestSubscriber<DockerMonitorUpEvent>()
                val downSubscriber = TestSubscriber<DockerMonitorDownEvent>()
                eventDispatcher.subscribeToDockerMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }
                eventDispatcher.subscribeToDockerMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("an UP event should be dispatched with the API latency") {
                    upSubscriber.awaitCount(1)
                    val upEvent = upSubscriber.values().shouldHaveSize(1).first()
                    upEvent.monitor.id shouldBe monitor.id
                    upEvent.uptimeStatus shouldBe UptimeStatus.UP
                    upEvent.latencyInMs shouldBe LATENCY_MS
                }

                then("the event should carry the resource sample for the exporters") {
                    upSubscriber.awaitCount(1)
                    val upEvent = upSubscriber.values().first()
                    upEvent.cpuUsagePercent shouldBe BigDecimal("37.25")
                    upEvent.memoryUsageBytes shouldBe 1_048_576
                }

                then("no DOWN event should be dispatched") {
                    downSubscriber.values().shouldHaveSize(0)
                }

                then("the event should be persisted with the image of the container") {
                    upSubscriber.awaitCount(1)
                    upSubscriber.values().first().image shouldBe IMAGE
                    with(uptimeEventRepository.fetchByMonitorId(monitor.id).shouldHaveSize(1).first()) {
                        status shouldBe UptimeStatus.UP
                        image shouldBe IMAGE
                    }
                }

                then("the event should be persisted with the restart count and the creation time of the container") {
                    upSubscriber.awaitCount(1)
                    with(upSubscriber.values().first()) {
                        restartCount shouldBe RESTART_COUNT
                        containerCreatedAt shouldBe CREATED_AT
                    }
                    with(uptimeEventRepository.fetchByMonitorId(monitor.id).shouldHaveSize(1).first()) {
                        restartCount shouldBe RESTART_COUNT
                        containerCreatedAt shouldBe CREATED_AT
                    }
                }

                then("the sample should be written to the metrics log") {
                    val log = metricsLogRepository.fetchLastByMonitorId(monitor.id).shouldNotBeNull()
                    log.cpuUsagePercent shouldBe BigDecimal("37.25")
                    log.memoryUsageBytes shouldBe 1_048_576
                }
            }

            `when`("the container has exited") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")
                val mock = getMock(apiClient)
                every {
                    mock.inspectContainer(any(), any(), any())
                } returns inspected(DockerContainerStatus.EXITED, exitCode = 137)

                val downSubscriber = TestSubscriber<DockerMonitorDownEvent>()
                eventDispatcher.subscribeToDockerMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should be dispatched with the exit code in the reason") {
                    downSubscriber.awaitCount(1)
                    val downEvent = downSubscriber.values().shouldHaveSize(1).first()
                    downEvent.uptimeStatus shouldBe UptimeStatus.DOWN
                    downEvent.error shouldBe "The container exited (137)"
                }

                // The daemon answered perfectly well, so the round-trip is still a valid measurement
                then("the API latency should still be recorded") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().first().latencyInMs shouldBe LATENCY_MS
                    metricsLogRepository.fetchLastByMonitorId(monitor.id).shouldNotBeNull()
                }

                then("no sampling should have been attempted on a stopped container") {
                    verify(exactly = 0) { mock.containerStats(any(), any(), any()) }
                }
            }

            `when`("a running container is recreated from another image") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")
                val mock = getMock(apiClient)
                every {
                    mock.inspectContainer(any(), any(), any())
                } returns inspected(DockerContainerStatus.RUNNING) andThen
                    inspected(DockerContainerStatus.RUNNING, image = "nginx:1.28")
                every { mock.containerStats(any(), any(), any()) } returns DockerStatsResult.Measured(SAMPLE)

                uptimeChecker.check(monitor)
                uptimeChecker.check(monitor)

                then("the ongoing UP event should carry the new image") {
                    with(uptimeEventRepository.fetchByMonitorId(monitor.id).shouldHaveSize(1).first()) {
                        status shouldBe UptimeStatus.UP
                        image shouldBe "nginx:1.28"
                    }
                }
            }

            `when`("a running container is restarted by its restart policy between two checks") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")
                val mock = getMock(apiClient)
                every {
                    mock.inspectContainer(any(), any(), any())
                } returns inspected(DockerContainerStatus.RUNNING) andThen
                    inspected(DockerContainerStatus.RUNNING, restartCount = RESTART_COUNT + 1)
                every { mock.containerStats(any(), any(), any()) } returns DockerStatsResult.Measured(SAMPLE)

                uptimeChecker.check(monitor)
                uptimeChecker.check(monitor)

                then("the ongoing UP event should carry the new restart count") {
                    with(uptimeEventRepository.fetchByMonitorId(monitor.id).shouldHaveSize(1).first()) {
                        status shouldBe UptimeStatus.UP
                        restartCount shouldBe RESTART_COUNT + 1
                    }
                }
            }

            `when`("the daemon of a running container becomes unreachable") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")
                val mock = getMock(apiClient)
                every {
                    mock.inspectContainer(any(), any(), any())
                } returns inspected(DockerContainerStatus.RUNNING) andThen
                    DockerInspectResult.Unreachable("connection refused")
                every { mock.containerStats(any(), any(), any()) } returns DockerStatsResult.Measured(SAMPLE)

                uptimeChecker.check(monitor)
                uptimeChecker.check(monitor)

                then("the new DOWN event should carry the restart count and creation time over from the UP event") {
                    val events = uptimeEventRepository.fetchByMonitorId(monitor.id).shouldHaveSize(2)
                    with(events.single { it.status == UptimeStatus.DOWN }) {
                        image shouldBe null
                        restartCount shouldBe RESTART_COUNT
                        containerCreatedAt shouldBe CREATED_AT
                    }
                }
            }

            `when`("the container of a monitor that is already down disappears") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")
                val mock = getMock(apiClient)
                every {
                    mock.inspectContainer(any(), any(), any())
                } returns inspected(DockerContainerStatus.EXITED, exitCode = 1) andThen
                    DockerInspectResult.ContainerNotFound(LATENCY_MS)

                uptimeChecker.check(monitor)
                uptimeChecker.check(monitor)

                then("the ongoing DOWN event should keep the image, restart count and creation time it knew") {
                    with(uptimeEventRepository.fetchByMonitorId(monitor.id).shouldHaveSize(1).first()) {
                        error shouldBe
                            """Reason: There is no container called "${monitor.container}" on the Docker host "local""""
                        image shouldBe IMAGE
                        restartCount shouldBe RESTART_COUNT
                        containerCreatedAt shouldBe CREATED_AT
                    }
                }
            }

            `when`("the daemon of a monitor that is already down becomes unreachable") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")
                val mock = getMock(apiClient)
                every {
                    mock.inspectContainer(any(), any(), any())
                } returns inspected(DockerContainerStatus.EXITED, exitCode = 1) andThen
                    DockerInspectResult.Unreachable("connection refused")

                uptimeChecker.check(monitor)
                uptimeChecker.check(monitor)

                then("the ongoing DOWN event should take the new error, but keep the known image") {
                    with(uptimeEventRepository.fetchByMonitorId(monitor.id).shouldHaveSize(1).first()) {
                        error shouldBe """Reason: The Docker host "local" cannot be reached: connection refused"""
                        image shouldBe IMAGE
                    }
                }
            }

            `when`("the daemon cannot be reached") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")
                val mock = getMock(apiClient)
                every {
                    mock.inspectContainer(any(), any(), any())
                } returns DockerInspectResult.Unreachable("connection refused")

                val downSubscriber = TestSubscriber<DockerMonitorDownEvent>()
                eventDispatcher.subscribeToDockerMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should name the host and the cause, with no image to tell") {
                    downSubscriber.awaitCount(1)
                    with(downSubscriber.values().shouldHaveSize(1).first()) {
                        error shouldBe """The Docker host "local" cannot be reached: connection refused"""
                        image shouldBe null
                    }
                }

                then("with no previous event to carry them over from, the restart count should stay unknown") {
                    with(uptimeEventRepository.fetchByMonitorId(monitor.id).shouldHaveSize(1).first()) {
                        restartCount shouldBe null
                        containerCreatedAt shouldBe null
                    }
                }

                // There was no round-trip to time, so the row would carry nothing but nulls
                then("no metrics log row should be written") {
                    metricsLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()
                }
            }

            `when`("the monitor names a Docker host that is not configured") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "gone")

                val downSubscriber = TestSubscriber<DockerMonitorDownEvent>()
                eventDispatcher.subscribeToDockerMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should say so instead of failing silently") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1).first().error shouldBe
                        """The Docker host "gone" is not configured"""
                }

                then("the daemon should not have been called at all") {
                    verify(exactly = 0) { getMock(apiClient).inspectContainer(any(), any(), any()) }
                }
            }

            `when`("the monitor keeps no metrics history") {
                val monitor = createDockerMonitor(
                    monitorRepository,
                    dockerHost = "local",
                    metricsHistoryEnabled = false,
                )
                val mock = getMock(apiClient)
                every { mock.inspectContainer(any(), any(), any()) } returns inspected(DockerContainerStatus.RUNNING)

                uptimeChecker.check(monitor)

                then("nothing should be written to the metrics log") {
                    metricsLogRepository.fetchLatestByMonitorId(monitor.id).shouldBeEmpty()
                }

                // The sampling costs about a second of the daemon's collection cycle, so it is skipped entirely
                then("the container should not be sampled") {
                    verify(exactly = 0) { mock.containerStats(any(), any(), any()) }
                }
            }

            `when`("the container is running but the sampling fails") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")
                val mock = getMock(apiClient)
                every { mock.inspectContainer(any(), any(), any()) } returns inspected(DockerContainerStatus.RUNNING)
                every {
                    mock.containerStats(any(), any(), any())
                } returns DockerStatsResult.Unavailable("the daemon answered 403")

                val upSubscriber = TestSubscriber<DockerMonitorUpEvent>()
                eventDispatcher.subscribeToDockerMonitorUpEvents { it.forwardToSubscriber(upSubscriber) }

                uptimeChecker.check(monitor)

                then("the monitor should still be UP, because sampling never decides that") {
                    upSubscriber.awaitCount(1)
                    upSubscriber.values().shouldHaveSize(1).first().uptimeStatus shouldBe UptimeStatus.UP
                }

                then("the latency should be logged with no resource counters") {
                    val log = metricsLogRepository.fetchLastByMonitorId(monitor.id).shouldNotBeNull()
                    log.cpuUsagePercent shouldBe null
                    log.memoryUsageBytes shouldBe null
                }
            }

            `when`("the container is running but unhealthy") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")
                val mock = getMock(apiClient)
                every {
                    mock.inspectContainer(any(), any(), any())
                } returns inspected(DockerContainerStatus.RUNNING, health = DockerHealthStatus.UNHEALTHY)
                every { mock.containerStats(any(), any(), any()) } returns DockerStatsResult.Measured(SAMPLE)

                val downSubscriber = TestSubscriber<DockerMonitorDownEvent>()
                eventDispatcher.subscribeToDockerMonitorDownEvents { it.forwardToSubscriber(downSubscriber) }

                uptimeChecker.check(monitor)

                then("a DOWN event should be dispatched naming the healthcheck") {
                    downSubscriber.awaitCount(1)
                    downSubscriber.values().shouldHaveSize(1).first().error shouldBe
                        "The container is running, but its healthcheck reports it unhealthy"
                }
            }

            `when`("a doAfter hook is given") {
                val monitor = createDockerMonitor(monitorRepository, dockerHost = "local")
                val mock = getMock(apiClient)
                every { mock.inspectContainer(any(), any(), any()) } returns inspected(DockerContainerStatus.RUNNING)
                every { mock.containerStats(any(), any(), any()) } returns DockerStatsResult.Measured(SAMPLE)

                var called = 0
                uptimeChecker.check(monitor) { called++ }

                then("it should be called with the up-to-date monitor") {
                    called shouldBe 1
                }
            }
        }

        given("DockerUptimeChecker restart alerts") {

            fun restartSubscriber() = TestSubscriber<DockerContainerRestartedEvent>().also { subscriber ->
                eventDispatcher.subscribeToDockerContainerRestartedEvents { it.forwardToSubscriber(subscriber) }
            }

            fun checkWith(monitor: DockerMonitorRecord, vararg inspections: DockerInspectResult) {
                val mock = getMock(apiClient)
                every { mock.inspectContainer(any(), any(), any()) } returnsMany inspections.toList()
                every { mock.containerStats(any(), any(), any()) } returns DockerStatsResult.Measured(SAMPLE)
                repeat(inspections.size) { uptimeChecker.check(monitor) }
            }

            `when`("the restart policy restarted a running container between two checks") {
                val monitor = createDockerMonitor(monitorRepository, restartAlertEnabled = true)
                val subscriber = restartSubscriber()

                checkWith(
                    monitor,
                    inspected(DockerContainerStatus.RUNNING),
                    inspected(DockerContainerStatus.RUNNING, restartCount = RESTART_COUNT + 2),
                )

                then("exactly one restart event should be dispatched with both counts") {
                    subscriber.awaitCount(1)
                    with(subscriber.values().shouldHaveSize(1).first()) {
                        this.monitor.id shouldBe monitor.id
                        previousRestartCount shouldBe RESTART_COUNT
                        currentRestartCount shouldBe RESTART_COUNT + 2
                    }
                }
            }

            `when`("the monitor is checked for the first time") {
                val monitor = createDockerMonitor(monitorRepository, restartAlertEnabled = true)
                val subscriber = restartSubscriber()

                checkWith(monitor, inspected(DockerContainerStatus.RUNNING))

                then("the restart count should be stored silently") {
                    uptimeEventRepository.fetchByMonitorId(monitor.id).single().restartCount shouldBe RESTART_COUNT
                    subscriber.values().shouldBeEmpty()
                }
            }

            `when`("restart alerts are disabled on the monitor") {
                val monitor = createDockerMonitor(monitorRepository, restartAlertEnabled = false)
                val subscriber = restartSubscriber()

                checkWith(
                    monitor,
                    inspected(DockerContainerStatus.RUNNING),
                    inspected(DockerContainerStatus.RUNNING, restartCount = RESTART_COUNT + 1),
                )

                then("no restart event should be dispatched, but the count should still be recorded") {
                    uptimeEventRepository.fetchByMonitorId(monitor.id).single().restartCount shouldBe RESTART_COUNT + 1
                    subscriber.values().shouldBeEmpty()
                }
            }

            `when`("the restart count is lower than before, because the container was restarted manually") {
                val monitor = createDockerMonitor(monitorRepository, restartAlertEnabled = true)
                val subscriber = restartSubscriber()

                checkWith(
                    monitor,
                    inspected(DockerContainerStatus.RUNNING),
                    inspected(DockerContainerStatus.RUNNING, restartCount = 0),
                )

                then("the lower count should be stored silently") {
                    uptimeEventRepository.fetchByMonitorId(monitor.id).single().restartCount shouldBe 0
                    subscriber.values().shouldBeEmpty()
                }
            }

            `when`("the container was recreated, with a higher restart count") {
                val monitor = createDockerMonitor(monitorRepository, restartAlertEnabled = true)
                val subscriber = restartSubscriber()
                val recreatedAt = CREATED_AT.plusHours(1)

                checkWith(
                    monitor,
                    inspected(DockerContainerStatus.RUNNING),
                    inspected(DockerContainerStatus.RUNNING, restartCount = RESTART_COUNT + 1, createdAt = recreatedAt),
                )

                then("the count of the new container should be stored silently") {
                    with(uptimeEventRepository.fetchByMonitorId(monitor.id).single()) {
                        restartCount shouldBe RESTART_COUNT + 1
                        containerCreatedAt.toInstant() shouldBe recreatedAt.toInstant()
                    }
                    subscriber.values().shouldBeEmpty()
                }
            }

            `when`("the container is caught restarting, which takes the monitor DOWN") {
                val monitor = createDockerMonitor(monitorRepository, restartAlertEnabled = true)
                val subscriber = restartSubscriber()

                checkWith(
                    monitor,
                    inspected(DockerContainerStatus.RUNNING),
                    inspected(DockerContainerStatus.RESTARTING, restartCount = RESTART_COUNT + 1),
                    inspected(DockerContainerStatus.RUNNING, restartCount = RESTART_COUNT + 1),
                )

                then("the DOWN event should absorb the restart, so it is not alerted on again after recovering") {
                    val events = uptimeEventRepository.fetchByMonitorId(monitor.id).shouldHaveSize(3)
                    events.single { it.status == UptimeStatus.DOWN }.restartCount shouldBe RESTART_COUNT + 1
                    subscriber.values().shouldBeEmpty()
                }
            }

            `when`("the container restarts while the DOWN is still pending its failure threshold") {
                val monitor = createDockerMonitor(
                    monitorRepository,
                    restartAlertEnabled = true,
                    failureCountThreshold = 2,
                )
                val subscriber = restartSubscriber()

                checkWith(
                    monitor,
                    inspected(DockerContainerStatus.RUNNING),
                    inspected(DockerContainerStatus.RESTARTING, restartCount = RESTART_COUNT + 1),
                    inspected(DockerContainerStatus.RUNNING, restartCount = RESTART_COUNT + 2),
                )

                then("no DOWN should have been recorded, and the next UP check should alert on every restart") {
                    uptimeEventRepository.fetchByMonitorId(monitor.id).single().status shouldBe UptimeStatus.UP
                    subscriber.awaitCount(1)
                    with(subscriber.values().shouldHaveSize(1).first()) {
                        previousRestartCount shouldBe RESTART_COUNT
                        currentRestartCount shouldBe RESTART_COUNT + 2
                    }
                }
            }

            `when`("the container restarts while its daemon cannot be reached") {
                val monitor = createDockerMonitor(monitorRepository, restartAlertEnabled = true)
                val subscriber = restartSubscriber()

                checkWith(
                    monitor,
                    inspected(DockerContainerStatus.RUNNING),
                    DockerInspectResult.Unreachable("connection refused"),
                    inspected(DockerContainerStatus.RUNNING, restartCount = RESTART_COUNT + 1),
                )

                then("the count carried over to the DOWN event should let the recovery alert on the restart") {
                    subscriber.awaitCount(1)
                    with(subscriber.values().shouldHaveSize(1).first()) {
                        previousRestartCount shouldBe RESTART_COUNT
                        currentRestartCount shouldBe RESTART_COUNT + 1
                    }
                }
            }
        }
    }
}
