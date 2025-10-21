package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSingleElement
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
