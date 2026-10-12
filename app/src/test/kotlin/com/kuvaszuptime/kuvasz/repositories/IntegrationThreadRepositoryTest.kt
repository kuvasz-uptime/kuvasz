package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.IntegrationThreadSubject
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.IntegrationThreadRecord
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime

@MicronautTest(startApplication = false)
class IntegrationThreadRepositoryTest(
    private val threadRepository: IntegrationThreadRepository,
    httpMonitorRepository: HttpMonitorRepository,
    pushMonitorRepository: PushMonitorRepository,
    icmpMonitorRepository: IcmpMonitorRepository,
    tcpMonitorRepository: TcpMonitorRepository,
    dnsMonitorRepository: DnsMonitorRepository,
    dockerMonitorRepository: DockerMonitorRepository,
    httpUptimeEventRepository: HttpUptimeEventRepository,
    pushUptimeEventRepository: PushUptimeEventRepository,
    icmpUptimeEventRepository: IcmpUptimeEventRepository,
    tcpUptimeEventRepository: TcpUptimeEventRepository,
    dnsUptimeEventRepository: DnsUptimeEventRepository,
    dockerUptimeEventRepository: DockerUptimeEventRepository,
    sslEventRepository: SSLEventRepository,
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
) : DatabaseBehaviorSpec() {

    private val integrationId = IntegrationID(IntegrationType.SLACK_BOT, "ops")
    private val otherIntegrationId = IntegrationID(IntegrationType.SLACK_BOT, "other")

    private fun thread(
        subjectType: IntegrationThreadSubject,
        subjectId: Long,
        integration: IntegrationID = integrationId,
        ts: String = "1700000000.000100",
        rootText: String = "*Root*",
    ) = IntegrationThreadRecord()
        .setIntegrationId(integration)
        .setSubjectType(subjectType)
        .setSubjectId(subjectId)
        .setTs(ts)
        .setRootText(rootText)

    /**
     * The events that can have a thread, created through a monitor of their own, which can be deleted with its events,
     * or the events can be deleted by the retention cleanup of their type.
     */
    private inner class EventFixture(
        val subjectType: IntegrationThreadSubject,
        private val create: (startedAt: OffsetDateTime, endedAt: OffsetDateTime?) -> Pair<Long, Long>,
        val deleteMonitor: (monitorId: Long) -> Unit,
        val deleteEventsBefore: (limit: OffsetDateTime) -> Unit,
    ) {
        /** Creates an event of a new monitor, returning the ID of the monitor and the event */
        fun createEvent(startedAt: OffsetDateTime, endedAt: OffsetDateTime?) = create(startedAt, endedAt)
    }

    private val eventFixtures = listOf(
        EventFixture(
            IntegrationThreadSubject.HTTP,
            { startedAt, endedAt ->
                val monitor = createHttpMonitor(httpMonitorRepository)
                monitor.id to createHttpUptimeEventRecord(
                    dslContext, monitor.id, UptimeStatus.DOWN, startedAt = startedAt, endedAt = endedAt,
                ).id
            },
            { monitorId -> httpMonitorRepository.deleteById(monitorId, null) },
            { limit -> httpUptimeEventRepository.deleteEventsBeforeDate(limit) },
        ),
        EventFixture(
            IntegrationThreadSubject.PUSH,
            { startedAt, endedAt ->
                val monitor = createPushMonitor(pushMonitorRepository)
                monitor.id to createPushUptimeEventRecord(
                    dslContext, monitor.id, UptimeStatus.DOWN, startedAt = startedAt, endedAt = endedAt,
                ).id
            },
            { monitorId -> pushMonitorRepository.deleteById(monitorId, null) },
            { limit -> pushUptimeEventRepository.deleteEventsBeforeDate(limit) },
        ),
        EventFixture(
            IntegrationThreadSubject.ICMP,
            { startedAt, endedAt ->
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                monitor.id to createIcmpUptimeEventRecord(
                    dslContext, monitor.id, UptimeStatus.DOWN, startedAt = startedAt, endedAt = endedAt,
                ).id
            },
            { monitorId -> icmpMonitorRepository.deleteById(monitorId, null) },
            { limit -> icmpUptimeEventRepository.deleteEventsBeforeDate(limit) },
        ),
        EventFixture(
            IntegrationThreadSubject.TCP,
            { startedAt, endedAt ->
                val monitor = createTcpMonitor(tcpMonitorRepository)
                monitor.id to createTcpUptimeEventRecord(
                    dslContext, monitor.id, UptimeStatus.DOWN, startedAt = startedAt, endedAt = endedAt,
                ).id
            },
            { monitorId -> tcpMonitorRepository.deleteById(monitorId, null) },
            { limit -> tcpUptimeEventRepository.deleteEventsBeforeDate(limit) },
        ),
        EventFixture(
            IntegrationThreadSubject.DNS,
            { startedAt, endedAt ->
                val monitor = createDnsMonitor(dnsMonitorRepository)
                monitor.id to createDnsUptimeEventRecord(
                    dslContext, monitor.id, UptimeStatus.DOWN, startedAt = startedAt, endedAt = endedAt,
                ).id
            },
            { monitorId -> dnsMonitorRepository.deleteById(monitorId, null) },
            { limit -> dnsUptimeEventRepository.deleteEventsBeforeDate(limit) },
        ),
        EventFixture(
            IntegrationThreadSubject.DOCKER,
            { startedAt, endedAt ->
                val monitor = createDockerMonitor(dockerMonitorRepository)
                monitor.id to createDockerUptimeEventRecord(
                    dslContext, monitor.id, UptimeStatus.DOWN, startedAt = startedAt, endedAt = endedAt,
                ).id
            },
            { monitorId -> dockerMonitorRepository.deleteById(monitorId, null) },
            { limit -> dockerUptimeEventRepository.deleteEventsBeforeDate(limit) },
        ),
        EventFixture(
            IntegrationThreadSubject.SSL,
            { startedAt, endedAt ->
                val monitor = createHttpMonitor(httpMonitorRepository)
                monitor.id to createSSLEventRecord(
                    dslContext, monitor.id, SslStatus.INVALID, startedAt = startedAt, endedAt = endedAt,
                ).id
            },
            { monitorId -> httpMonitorRepository.deleteById(monitorId, null) },
            { limit -> sslEventRepository.deleteEventsBeforeDate(limit) },
        ),
    )

    init {
        given("the upsert() method") {

            `when`("a thread is stored for a new subject") {
                threadRepository.upsert(thread(IntegrationThreadSubject.HTTP, 1)) shouldBe 1

                then("it should be found with every column of it") {
                    val found = threadRepository.find(integrationId, IntegrationThreadSubject.HTTP, 1).shouldNotBeNull()
                    found.integrationId shouldBe integrationId
                    found.subjectType shouldBe IntegrationThreadSubject.HTTP
                    found.subjectId shouldBe 1
                    found.ts shouldBe "1700000000.000100"
                    found.rootText shouldBe "*Root*"
                }
            }

            `when`("a thread is stored again for the same integration and subject") {
                threadRepository.upsert(thread(IntegrationThreadSubject.SSL, 1, ts = "1.1", rootText = "First"))
                threadRepository.upsert(thread(IntegrationThreadSubject.SSL, 1, ts = "2.2", rootText = "Second"))

                then("it should replace both its root and its text") {
                    val found = threadRepository.find(integrationId, IntegrationThreadSubject.SSL, 1).shouldNotBeNull()
                    found.ts shouldBe "2.2"
                    found.rootText shouldBe "Second"
                }
            }

            `when`("threads are stored for the same subject ID, but for different integrations and subject types") {
                threadRepository.upsert(thread(IntegrationThreadSubject.HTTP, 1, ts = "1.1"))
                threadRepository.upsert(thread(IntegrationThreadSubject.HTTP, 1, otherIntegrationId, ts = "2.2"))
                threadRepository.upsert(thread(IntegrationThreadSubject.PUSH, 1, ts = "3.3"))

                then("they should be stored side by side") {
                    threadRepository.find(integrationId, IntegrationThreadSubject.HTTP, 1)?.ts shouldBe "1.1"
                    threadRepository.find(otherIntegrationId, IntegrationThreadSubject.HTTP, 1)?.ts shouldBe "2.2"
                    threadRepository.find(integrationId, IntegrationThreadSubject.PUSH, 1)?.ts shouldBe "3.3"
                }
            }
        }

        given("the find() method") {

            `when`("there is a thread") {
                threadRepository.upsert(thread(IntegrationThreadSubject.DNS, 1))

                then("it should not find anything for another integration, subject type or subject ID") {
                    threadRepository.find(otherIntegrationId, IntegrationThreadSubject.DNS, 1).shouldBeNull()
                    threadRepository.find(integrationId, IntegrationThreadSubject.TCP, 1).shouldBeNull()
                    threadRepository.find(integrationId, IntegrationThreadSubject.DNS, 2).shouldBeNull()
                }
            }
        }

        given("the findBySubject() method") {

            `when`("several integrations have a thread of the same subject") {
                threadRepository.upsert(thread(IntegrationThreadSubject.SSL, 1, ts = "1.1"))
                threadRepository.upsert(thread(IntegrationThreadSubject.SSL, 1, otherIntegrationId, ts = "2.2"))
                threadRepository.upsert(thread(IntegrationThreadSubject.SSL, 2))
                threadRepository.upsert(thread(IntegrationThreadSubject.HTTP, 1))

                then("it should return the thread of every integration, but only of that subject") {
                    threadRepository.findBySubject(IntegrationThreadSubject.SSL, 1)
                        .map { thread -> thread.integrationId to thread.ts } shouldContainExactlyInAnyOrder
                        listOf(integrationId to "1.1", otherIntegrationId to "2.2")
                }
            }

            `when`("there is no thread of the subject") {
                then("it should return nothing") {
                    threadRepository.findBySubject(IntegrationThreadSubject.DNS, 1).shouldBeEmpty()
                }
            }
        }

        given("the delete() method") {

            `when`("a thread is deleted") {
                threadRepository.upsert(thread(IntegrationThreadSubject.MAINTENANCE, 1))
                threadRepository.upsert(thread(IntegrationThreadSubject.MAINTENANCE, 1, otherIntegrationId))
                threadRepository.upsert(thread(IntegrationThreadSubject.HTTP, 1))
                threadRepository.upsert(thread(IntegrationThreadSubject.MAINTENANCE, 2))

                val deleted = threadRepository.delete(integrationId, IntegrationThreadSubject.MAINTENANCE, 1)

                then("only that thread should be gone") {
                    deleted shouldBe 1
                    threadRepository.find(integrationId, IntegrationThreadSubject.MAINTENANCE, 1).shouldBeNull()
                    threadRepository.find(otherIntegrationId, IntegrationThreadSubject.MAINTENANCE, 1)
                        .shouldNotBeNull()
                    threadRepository.find(integrationId, IntegrationThreadSubject.HTTP, 1).shouldNotBeNull()
                    threadRepository.find(integrationId, IntegrationThreadSubject.MAINTENANCE, 2).shouldNotBeNull()
                }
            }

            `when`("a non-existing thread is deleted") {
                then("it should not delete anything") {
                    threadRepository.delete(integrationId, IntegrationThreadSubject.MAINTENANCE, 1) shouldBe 0
                }
            }
        }

        eventFixtures.forEach { fixture ->
            given("the threads of the ${fixture.subjectType} events") {

                `when`("their monitor is deleted") {
                    val (monitorId, eventId) = fixture.createEvent(getCurrentTimestamp(), null)
                    val (_, otherEventId) = fixture.createEvent(getCurrentTimestamp(), null)
                    threadRepository.upsert(thread(fixture.subjectType, eventId))
                    threadRepository.upsert(thread(fixture.subjectType, eventId, otherIntegrationId))
                    threadRepository.upsert(thread(fixture.subjectType, otherEventId))
                    // A thread of another subject type, with the very same subject ID
                    threadRepository.upsert(thread(IntegrationThreadSubject.MAINTENANCE, eventId))

                    fixture.deleteMonitor(monitorId)

                    then("the threads of its events should be deleted together with them, for every integration") {
                        threadRepository.find(integrationId, fixture.subjectType, eventId).shouldBeNull()
                        threadRepository.find(otherIntegrationId, fixture.subjectType, eventId).shouldBeNull()
                    }

                    then("the threads of other monitors, and of other subject types should be kept") {
                        threadRepository.find(integrationId, fixture.subjectType, otherEventId).shouldNotBeNull()
                        threadRepository.find(integrationId, IntegrationThreadSubject.MAINTENANCE, eventId)
                            .shouldNotBeNull()
                    }
                }

                `when`("several of their events are deleted at once by the retention cleanup") {
                    val now = getCurrentTimestamp()
                    val oldEventIds = (1..3).map {
                        fixture.createEvent(now.minusDays(10), now.minusDays(9)).second
                    }
                    val (_, recentEventId) = fixture.createEvent(now.minusDays(1), null)
                    (oldEventIds + recentEventId).forEach { eventId ->
                        threadRepository.upsert(thread(fixture.subjectType, eventId))
                    }

                    fixture.deleteEventsBefore(now.minusDays(5))

                    then("the threads of every deleted event should be deleted too") {
                        oldEventIds.forAll { eventId ->
                            threadRepository.find(integrationId, fixture.subjectType, eventId).shouldBeNull()
                        }
                        threadRepository.find(integrationId, fixture.subjectType, recentEventId).shouldNotBeNull()
                    }
                }
            }
        }

        given("the threads of the maintenance windows") {

            `when`("a maintenance window is deleted") {
                val window = createMaintenanceWindow(dslContext)
                threadRepository.upsert(thread(IntegrationThreadSubject.MAINTENANCE, window.id))

                maintenanceWindowRepository.deleteById(window.id)

                then("its thread should be kept, because its end event still needs it") {
                    threadRepository.find(integrationId, IntegrationThreadSubject.MAINTENANCE, window.id)
                        .shouldNotBeNull()
                }
            }

            `when`("the threads of the inactive windows are deleted") {
                listOf(1L, 2L, 3L).forEach { windowId ->
                    threadRepository.upsert(thread(IntegrationThreadSubject.MAINTENANCE, windowId))
                    threadRepository.upsert(thread(IntegrationThreadSubject.MAINTENANCE, windowId, otherIntegrationId))
                }
                // The threads of the incidents with the same IDs are not affected
                threadRepository.upsert(thread(IntegrationThreadSubject.HTTP, 1))
                threadRepository.upsert(thread(IntegrationThreadSubject.SSL, 3))

                val deleted = threadRepository.deleteMaintenanceThreadsExcept(listOf(2))

                then("only the threads of the active windows, and of the incidents should be kept") {
                    deleted shouldBe 4
                    listOf(integrationId, otherIntegrationId).forAll { integration ->
                        threadRepository.find(integration, IntegrationThreadSubject.MAINTENANCE, 1).shouldBeNull()
                        threadRepository.find(integration, IntegrationThreadSubject.MAINTENANCE, 2).shouldNotBeNull()
                        threadRepository.find(integration, IntegrationThreadSubject.MAINTENANCE, 3).shouldBeNull()
                    }
                    threadRepository.find(integrationId, IntegrationThreadSubject.HTTP, 1).shouldNotBeNull()
                    threadRepository.find(integrationId, IntegrationThreadSubject.SSL, 3).shouldNotBeNull()
                }
            }

            `when`("there is no active window at all") {
                threadRepository.upsert(thread(IntegrationThreadSubject.MAINTENANCE, 1))
                threadRepository.upsert(thread(IntegrationThreadSubject.MAINTENANCE, 2, otherIntegrationId))
                threadRepository.upsert(thread(IntegrationThreadSubject.HTTP, 1))

                val deleted = threadRepository.deleteMaintenanceThreadsExcept(emptyList())

                then("every maintenance thread should be deleted") {
                    deleted shouldBe 2
                    threadRepository.find(integrationId, IntegrationThreadSubject.MAINTENANCE, 1).shouldBeNull()
                    threadRepository.find(otherIntegrationId, IntegrationThreadSubject.MAINTENANCE, 2).shouldBeNull()
                    threadRepository.find(integrationId, IntegrationThreadSubject.HTTP, 1).shouldNotBeNull()
                }
            }

            `when`("there are no maintenance threads") {
                then("it should not delete anything") {
                    threadRepository.deleteMaintenanceThreadsExcept(listOf(1)) shouldBe 0
                }
            }
        }
    }
}
