package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createPendingFailure
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class PushMonitorRepositoryTest(
    private val pushMonitorRepository: PushMonitorRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the fetchWithMissedHeartbeats() method") {

            `when`("a monitor doesn't have a recorded heartbeat") {
                createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    lastHeartbeat = null,
                )

                then("it should not return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null).shouldBeEmpty()
                }
            }

            `when`("a monitor has a fresh enough heartbeat - no grace period") {
                createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 4,
                    lastHeartbeat = getCurrentTimestamp().minusSeconds(3),
                )

                then("it should not return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null).shouldBeEmpty()
                }
            }

            `when`("a disabled monitor has an old heartbeat - no grace period") {
                createPushMonitor(
                    pushMonitorRepository,
                    enabled = false,
                    heartbeatInterval = 4,
                    lastHeartbeat = getCurrentTimestamp().minusSeconds(5),
                )

                then("it should not return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null).shouldBeEmpty()
                }
            }

            `when`("a disabled monitor has an old heartbeat - with grace period") {
                createPushMonitor(
                    pushMonitorRepository,
                    enabled = false,
                    heartbeatInterval = 4,
                    gracePeriod = 1,
                    lastHeartbeat = getCurrentTimestamp().minusSeconds(6),
                )

                then("it should not return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null).shouldBeEmpty()
                }
            }

            `when`("a monitor has a fresh enough heartbeat - with grace period") {
                createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 3,
                    gracePeriod = 1,
                    lastHeartbeat = getCurrentTimestamp().minusSeconds(3),
                )

                then("it should not return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null).shouldBeEmpty()
                }
            }

            `when`("a monitor has an old heartbeat - no grace period") {
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 3,
                    lastHeartbeat = getCurrentTimestamp().minusSeconds(4),
                )

                then("it should return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null) shouldHaveSingleElement monitor
                }
            }

            `when`("a monitor has an old heartbeat - with grace period") {
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 3,
                    gracePeriod = 3,
                    lastHeartbeat = getCurrentTimestamp().minusSeconds(7),
                )

                then("it should return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null) shouldHaveSingleElement monitor
                }
            }

            `when`("a monitor has a pending failure, but its next heartbeat is not missed yet") {
                val lastHeartbeat = getCurrentTimestamp().minusSeconds(6)
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 3,
                    gracePeriod = 1,
                    failureCountThreshold = 3,
                    lastHeartbeat = lastHeartbeat,
                )
                createPendingFailure(dslContext, monitor.id, updatedAt = lastHeartbeat.plusSeconds(4))

                then("it should not return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null).shouldBeEmpty()
                }
            }

            `when`("a monitor has a pending failure and its next heartbeat is missed, too") {
                val lastHeartbeat = getCurrentTimestamp().minusSeconds(8)
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 3,
                    gracePeriod = 1,
                    failureCountThreshold = 3,
                    lastHeartbeat = lastHeartbeat,
                )
                createPendingFailure(dslContext, monitor.id, updatedAt = lastHeartbeat.plusSeconds(4))

                then("it should return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null) shouldHaveSingleElement monitor
                }
            }

            `when`("a monitor has multiple pending failures, but its next heartbeat is not missed yet") {
                val lastHeartbeat = getCurrentTimestamp().minusSeconds(9)
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 3,
                    gracePeriod = 1,
                    failureCountThreshold = 3,
                    lastHeartbeat = lastHeartbeat,
                )
                createPendingFailure(
                    dslContext,
                    monitor.id,
                    failureCount = 2,
                    updatedAt = lastHeartbeat.plusSeconds(7),
                )

                then("it should not return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null).shouldBeEmpty()
                }
            }

            `when`("a monitor has multiple pending failures and its next heartbeat is missed, too") {
                val lastHeartbeat = getCurrentTimestamp().minusSeconds(11)
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 3,
                    gracePeriod = 1,
                    failureCountThreshold = 3,
                    lastHeartbeat = lastHeartbeat,
                )
                createPendingFailure(
                    dslContext,
                    monitor.id,
                    failureCount = 2,
                    updatedAt = lastHeartbeat.plusSeconds(7),
                )

                then("it should return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null) shouldHaveSingleElement monitor
                }
            }

            `when`("a monitor's heartbeat is long overdue, but its last failure was recorded recently") {
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 3,
                    gracePeriod = 1,
                    failureCountThreshold = 3,
                    lastHeartbeat = getCurrentTimestamp().minusSeconds(60),
                )
                createPendingFailure(dslContext, monitor.id, updatedAt = getCurrentTimestamp().minusSeconds(1))

                then("it should not return it before a whole heartbeat interval elapses") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null).shouldBeEmpty()
                }
            }

            `when`("a monitor's heartbeat is long overdue and its last failure was recorded long ago") {
                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 3,
                    gracePeriod = 1,
                    failureCountThreshold = 3,
                    lastHeartbeat = getCurrentTimestamp().minusSeconds(60),
                )
                createPendingFailure(dslContext, monitor.id, updatedAt = getCurrentTimestamp().minusSeconds(4))

                then("it should return it") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null) shouldHaveSingleElement monitor
                }
            }

            `when`("only one of the monitors has a pending failure") {
                val lastHeartbeat = getCurrentTimestamp().minusSeconds(5)
                val withPendingFailure = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 3,
                    gracePeriod = 1,
                    failureCountThreshold = 2,
                    lastHeartbeat = lastHeartbeat,
                )
                createPendingFailure(dslContext, withPendingFailure.id, updatedAt = lastHeartbeat.plusSeconds(4))
                val withoutPendingFailure = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    heartbeatInterval = 3,
                    gracePeriod = 1,
                    failureCountThreshold = 2,
                    lastHeartbeat = lastHeartbeat,
                )

                then("it should return only the one without a pending failure") {
                    pushMonitorRepository.fetchWithMissedHeartbeats(null)
                        .shouldHaveSize(1)
                        .single().id shouldBe withoutPendingFailure.id
                }
            }
        }

        given("the updateLastHeartbeat() method") {

            `when`("the client secret doesn't match any of the monitors in the DB") {

                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    lastHeartbeat = null,
                )

                then("it should not update any monitor") {

                    pushMonitorRepository.updateLastHeartbeat(
                        clientSecret = "not-matching",
                        timestamp = getCurrentTimestamp(),
                    ) shouldBe null

                    pushMonitorRepository.findById(monitor.id, null)
                        .shouldNotBeNull()
                        .lastHeartbeat.shouldBeNull()
                }
            }

            `when`("the client secret matches a monitor in the DB - enabled monitor") {

                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    lastHeartbeat = null,
                    enabled = true,
                )

                then("it should update the monitor's last heartbeat") {

                    val now = getCurrentTimestamp()
                    val updatedMonitor = pushMonitorRepository.updateLastHeartbeat(
                        clientSecret = monitor.clientSecret,
                        timestamp = now,
                    ).shouldNotBeNull()

                    val monitorAfterUpdateInDb = pushMonitorRepository.findById(
                        monitorId = monitor.id,
                        txCtx = null,
                    ).shouldNotBeNull()

                    updatedMonitor.id shouldBe monitorAfterUpdateInDb.id
                    updatedMonitor.updatedAt.shouldNotBeNull() shouldBeAfter monitor.updatedAt.shouldNotBeNull()
                    updatedMonitor.lastHeartbeat shouldBe now
                    monitorAfterUpdateInDb.lastHeartbeat shouldBe now
                }
            }

            `when`("the client secret matches a monitor in the DB - disabled monitor") {

                val monitor = createPushMonitor(
                    pushMonitorRepository,
                    lastHeartbeat = getCurrentTimestamp().minusDays(2),
                    enabled = false,
                )

                then("it should update the monitor's last heartbeat") {

                    val now = getCurrentTimestamp()
                    val updatedMonitor = pushMonitorRepository.updateLastHeartbeat(
                        clientSecret = monitor.clientSecret,
                        timestamp = now,
                    ).shouldNotBeNull()

                    val monitorAfterUpdateInDb = pushMonitorRepository.findById(
                        monitorId = monitor.id,
                        txCtx = null,
                    ).shouldNotBeNull()

                    updatedMonitor.id shouldBe monitorAfterUpdateInDb.id
                    updatedMonitor.updatedAt.shouldNotBeNull() shouldBeAfter monitor.updatedAt.shouldNotBeNull()
                    updatedMonitor.lastHeartbeat shouldBe now
                    monitorAfterUpdateInDb.lastHeartbeat shouldBe now
                }
            }
        }
    }
}
