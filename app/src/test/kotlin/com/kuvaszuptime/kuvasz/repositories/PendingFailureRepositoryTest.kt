package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.tables.PendingFailure.PENDING_FAILURE
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPendingFailure
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.withClue
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@MicronautTest(startApplication = false)
class PendingFailureRepositoryTest(
    private val pendingFailureRepository: PendingFailureRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the createOrIncrement() method") {

            `when`("the monitor doesn't have a pending failure yet") {
                val monitor = createPushMonitor(pushMonitorRepository)

                then("it should record the first failure of it") {
                    val record = pendingFailureRepository.createOrIncrement(monitor.id)

                    record.monitorId shouldBe monitor.id
                    record.failureCount shouldBe 1L
                }
            }

            `when`("the monitor already has a pending failure") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val existing = createPendingFailure(
                    dslContext,
                    monitor.id,
                    failureCount = 2,
                    updatedAt = getCurrentTimestamp().minusMinutes(1),
                )

                then("it should increment its count and update its timestamp") {
                    val record = pendingFailureRepository.createOrIncrement(monitor.id)

                    record.monitorId shouldBe monitor.id
                    record.failureCount shouldBe 3L
                    record.updatedAt shouldBeAfter existing.updatedAt
                    record.createdAt shouldBe existing.createdAt
                }
            }

            `when`("it is called concurrently for the same monitor") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val concurrentCalls = 8

                then("it should count every call exactly once") {
                    val executor = Executors.newFixedThreadPool(concurrentCalls)
                    try {
                        val tasks = List(concurrentCalls) {
                            Callable { pendingFailureRepository.createOrIncrement(monitor.id) }
                        }
                        // Throws if any of the concurrent upserts failed, e.g. with a duplicate key violation
                        executor.invokeAll(tasks).forEach { it.get() }
                    } finally {
                        executor.shutdown()
                        executor.awaitTermination(1, TimeUnit.MINUTES)
                    }

                    dslContext
                        .selectFrom(PENDING_FAILURE)
                        .where(PENDING_FAILURE.MONITOR_ID.eq(monitor.id))
                        .fetchOne(PENDING_FAILURE.FAILURE_COUNT) shouldBe concurrentCalls.toLong()
                }
            }

            `when`("it is called within a transaction that is rolled back") {
                val monitor = createPushMonitor(pushMonitorRepository)

                then("it should not record the failure") {
                    runCatching {
                        dslContext.transaction { config ->
                            pendingFailureRepository.createOrIncrement(monitor.id, config.dsl())
                            error("Something went wrong after the failure was recorded")
                        }
                    }

                    dslContext
                        .selectFrom(PENDING_FAILURE)
                        .where(PENDING_FAILURE.MONITOR_ID.eq(monitor.id))
                        .fetchOne()
                        .shouldBeNull()
                }
            }
        }

        given("the deleteByMonitorId() method") {

            `when`("the monitor has a pending failure") {
                val monitor = createPushMonitor(pushMonitorRepository)
                createPendingFailure(dslContext, monitor.id)

                then("it should delete it") {
                    pendingFailureRepository.deleteByMonitorId(monitor.id) shouldBe 1

                    dslContext
                        .selectFrom(PENDING_FAILURE)
                        .where(PENDING_FAILURE.MONITOR_ID.eq(monitor.id))
                        .fetchOne()
                        .shouldBeNull()
                }
            }

            `when`("the monitor doesn't have a pending failure") {
                val monitor = createPushMonitor(pushMonitorRepository)

                then("it should not delete anything") {
                    pendingFailureRepository.deleteByMonitorId(monitor.id) shouldBe 0
                }
            }
        }

        given("a monitor with a pending failure") {

            `when`("the monitor is deleted, whatever its type") {
                val monitors = listOf(
                    Triple("HTTP", createHttpMonitor(httpMonitorRepository).id, httpMonitorRepository),
                    Triple("push", createPushMonitor(pushMonitorRepository).id, pushMonitorRepository),
                    Triple("ICMP", createIcmpMonitor(icmpMonitorRepository).id, icmpMonitorRepository),
                    Triple("TCP", createTcpMonitor(tcpMonitorRepository).id, tcpMonitorRepository),
                    Triple("DNS", createDnsMonitor(dnsMonitorRepository).id, dnsMonitorRepository),
                    Triple("Docker", createDockerMonitor(dockerMonitorRepository).id, dockerMonitorRepository),
                )

                then("its pending failure should be deleted along with it") {
                    monitors.forEach { (type, monitorId, repository) ->
                        createPendingFailure(dslContext, monitorId)

                        repository.deleteById(monitorId, null) shouldBe 1

                        withClue(type) {
                            dslContext
                                .selectFrom(PENDING_FAILURE)
                                .where(PENDING_FAILURE.MONITOR_ID.eq(monitorId))
                                .fetchOne()
                                .shouldBeNull()
                        }
                    }
                }
            }
        }
    }
}
