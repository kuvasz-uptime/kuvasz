package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.mocks.createUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.IncidentStatus
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import org.jooq.DSLContext
import java.time.Duration

@MicronautTest(startApplication = false)
class IncidentRepositoryTest(
    httpMonitorRepository: HttpMonitorRepository,
    dslContext: DSLContext,
    incidentRepository: IncidentRepository
) : DatabaseBehaviorSpec({

    given("the getIncidents() method") {

        `when`("it is called without explicit parameters") {

            then("it should return the right incidents") {

                val monitor1 = createMonitor(httpMonitorRepository)
                val monitor2 = createMonitor(httpMonitorRepository)

                // Should be ignored, it's not an incident
                createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )
                // Should be ignored, it's not an incident
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = SslStatus.VALID,
                    startedAt = getCurrentTimestamp().minusDays(90),
                    endedAt = getCurrentTimestamp().minusDays(1),
                )
                // Should be ignored, it's not an incident
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = SslStatus.WILL_EXPIRE,
                    startedAt = getCurrentTimestamp().minusDays(20),
                    endedAt = getCurrentTimestamp().minusDays(10),
                )
                val openDownMonitor1 = createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                )
                val resolvedDownMonitor1 = createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(6),
                    endedAt = getCurrentTimestamp().minusDays(4),
                )
                val openInvalidMonitor1 = createSSLEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(4),
                    endedAt = null,
                )
                val resolvedInvalidMonitor1 = createSSLEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    endedAt = getCurrentTimestamp().minusDays(5),
                )

                val openDownMonitor2 = createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    error = "sh#t happened"
                )
                val resolvedDownMonitor2 = createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(6),
                    endedAt = getCurrentTimestamp().minusDays(4),
                )
                val openInvalidMonitor2 = createSSLEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(4),
                    endedAt = null,
                    error = "something"
                )
                val resolvedInvalidMonitor2 = createSSLEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    endedAt = getCurrentTimestamp().minusDays(5),
                )

                val incidentsWithResolved = incidentRepository.getIncidents(includeResolved = true)
                incidentsWithResolved shouldHaveSize 8

                incidentsWithResolved.forOne { resolvedMonitor2 ->
                    resolvedMonitor2.monitorId shouldBe resolvedDownMonitor2.monitorId
                    resolvedMonitor2.monitorName shouldBe monitor2.name
                    resolvedMonitor2.isMonitorEnabled shouldBe monitor2.enabled
                    resolvedMonitor2.status shouldBe IncidentStatus.RESOLVED
                    resolvedMonitor2.startedAt shouldBe resolvedDownMonitor2.startedAt
                    resolvedMonitor2.endedAt shouldBe resolvedDownMonitor2.endedAt
                    resolvedMonitor2.updatedAt shouldBe resolvedDownMonitor2.updatedAt
                    resolvedMonitor2.incidentType shouldBe IncidentType.HTTP
                    resolvedMonitor2.details shouldBe null
                }

                incidentsWithResolved.forOne { resolvedMonitor1 ->
                    resolvedMonitor1.monitorId shouldBe resolvedDownMonitor1.monitorId
                    resolvedMonitor1.monitorName shouldBe monitor1.name
                    resolvedMonitor1.isMonitorEnabled shouldBe monitor1.enabled
                    resolvedMonitor1.status shouldBe IncidentStatus.RESOLVED
                    resolvedMonitor1.startedAt shouldBe resolvedDownMonitor1.startedAt
                    resolvedMonitor1.endedAt shouldBe resolvedDownMonitor1.endedAt
                    resolvedMonitor1.updatedAt shouldBe resolvedDownMonitor1.updatedAt
                    resolvedMonitor1.incidentType shouldBe IncidentType.HTTP
                    resolvedMonitor1.details shouldBe null
                }

                incidentsWithResolved.forOne { openMonitor2 ->
                    openMonitor2.monitorId shouldBe openDownMonitor2.monitorId
                    openMonitor2.monitorName shouldBe monitor2.name
                    openMonitor2.isMonitorEnabled shouldBe monitor2.enabled
                    openMonitor2.status shouldBe IncidentStatus.ONGOING
                    openMonitor2.startedAt shouldBe openDownMonitor2.startedAt
                    openMonitor2.endedAt shouldBe null
                    openMonitor2.updatedAt shouldBe openDownMonitor2.updatedAt
                    openMonitor2.incidentType shouldBe IncidentType.HTTP
                    openMonitor2.details shouldBe openDownMonitor2.error
                }

                incidentsWithResolved.forOne { openMonitor1 ->
                    openMonitor1.monitorId shouldBe openDownMonitor1.monitorId
                    openMonitor1.monitorName shouldBe monitor1.name
                    openMonitor1.isMonitorEnabled shouldBe monitor1.enabled
                    openMonitor1.status shouldBe IncidentStatus.ONGOING
                    openMonitor1.startedAt shouldBe openDownMonitor1.startedAt
                    openMonitor1.endedAt shouldBe null
                    openMonitor1.updatedAt shouldBe openDownMonitor1.updatedAt
                    openMonitor1.incidentType shouldBe IncidentType.HTTP
                    openMonitor1.details shouldBe null
                }

                incidentsWithResolved.forOne { openInvalidSSLMonitor1 ->
                    openInvalidSSLMonitor1.monitorId shouldBe openInvalidMonitor1.monitorId
                    openInvalidSSLMonitor1.monitorName shouldBe monitor1.name
                    openInvalidSSLMonitor1.isMonitorEnabled shouldBe monitor1.enabled
                    openInvalidSSLMonitor1.status shouldBe IncidentStatus.ONGOING
                    openInvalidSSLMonitor1.startedAt shouldBe openInvalidMonitor1.startedAt
                    openInvalidSSLMonitor1.endedAt shouldBe null
                    openInvalidSSLMonitor1.updatedAt shouldBe openInvalidMonitor1.updatedAt
                    openInvalidSSLMonitor1.incidentType shouldBe IncidentType.SSL
                    openInvalidSSLMonitor1.details shouldBe null
                }

                incidentsWithResolved.forOne { openInvalidSSLMonitor2 ->
                    openInvalidSSLMonitor2.monitorId shouldBe openInvalidMonitor2.monitorId
                    openInvalidSSLMonitor2.monitorName shouldBe monitor2.name
                    openInvalidSSLMonitor2.isMonitorEnabled shouldBe monitor2.enabled
                    openInvalidSSLMonitor2.status shouldBe IncidentStatus.ONGOING
                    openInvalidSSLMonitor2.startedAt shouldBe openInvalidMonitor2.startedAt
                    openInvalidSSLMonitor2.endedAt shouldBe null
                    openInvalidSSLMonitor2.updatedAt shouldBe openInvalidMonitor2.updatedAt
                    openInvalidSSLMonitor2.incidentType shouldBe IncidentType.SSL
                    openInvalidSSLMonitor2.details shouldBe "something"
                }

                incidentsWithResolved.forOne { resolvedInvalidSSLMonitor1 ->
                    resolvedInvalidSSLMonitor1.monitorId shouldBe resolvedInvalidMonitor1.monitorId
                    resolvedInvalidSSLMonitor1.monitorName shouldBe monitor1.name
                    resolvedInvalidSSLMonitor1.isMonitorEnabled shouldBe monitor1.enabled
                    resolvedInvalidSSLMonitor1.status shouldBe IncidentStatus.RESOLVED
                    resolvedInvalidSSLMonitor1.startedAt shouldBe resolvedInvalidMonitor1.startedAt
                    resolvedInvalidSSLMonitor1.endedAt shouldBe resolvedInvalidMonitor1.endedAt
                    resolvedInvalidSSLMonitor1.updatedAt shouldBe resolvedInvalidMonitor1.updatedAt
                    resolvedInvalidSSLMonitor1.incidentType shouldBe IncidentType.SSL
                    resolvedInvalidSSLMonitor1.details shouldBe null
                }

                incidentsWithResolved.forOne { resolvedInvalidSSLMonitor2 ->
                    resolvedInvalidSSLMonitor2.monitorId shouldBe resolvedInvalidMonitor2.monitorId
                    resolvedInvalidSSLMonitor2.monitorName shouldBe monitor2.name
                    resolvedInvalidSSLMonitor2.isMonitorEnabled shouldBe monitor2.enabled
                    resolvedInvalidSSLMonitor2.status shouldBe IncidentStatus.RESOLVED
                    resolvedInvalidSSLMonitor2.startedAt shouldBe resolvedInvalidMonitor2.startedAt
                    resolvedInvalidSSLMonitor2.endedAt shouldBe resolvedInvalidMonitor2.endedAt
                    resolvedInvalidSSLMonitor2.updatedAt shouldBe resolvedInvalidMonitor2.updatedAt
                    resolvedInvalidSSLMonitor2.incidentType shouldBe IncidentType.SSL
                    resolvedInvalidSSLMonitor2.details shouldBe null
                }

                val incidentsWithoutResolved = incidentRepository.getIncidents(includeResolved = false)
                incidentsWithoutResolved shouldHaveSize 4

                incidentsWithoutResolved.forOne { openMonitor2 ->
                    openMonitor2.monitorId shouldBe openDownMonitor2.monitorId
                    openMonitor2.monitorName shouldBe monitor2.name
                    openMonitor2.isMonitorEnabled shouldBe monitor2.enabled
                    openMonitor2.status shouldBe IncidentStatus.ONGOING
                    openMonitor2.startedAt shouldBe openDownMonitor2.startedAt
                    openMonitor2.endedAt shouldBe null
                    openMonitor2.updatedAt shouldBe openDownMonitor2.updatedAt
                    openMonitor2.incidentType shouldBe IncidentType.HTTP
                    openMonitor2.details shouldBe openDownMonitor2.error
                }

                incidentsWithoutResolved.forOne { openMonitor1 ->
                    openMonitor1.monitorId shouldBe openDownMonitor1.monitorId
                    openMonitor1.monitorName shouldBe monitor1.name
                    openMonitor1.isMonitorEnabled shouldBe monitor1.enabled
                    openMonitor1.status shouldBe IncidentStatus.ONGOING
                    openMonitor1.startedAt shouldBe openDownMonitor1.startedAt
                    openMonitor1.endedAt shouldBe null
                    openMonitor1.updatedAt shouldBe openDownMonitor1.updatedAt
                    openMonitor1.incidentType shouldBe IncidentType.HTTP
                    openMonitor1.details shouldBe null
                }

                incidentsWithoutResolved.forOne { openInvalidSSLMonitor1 ->
                    openInvalidSSLMonitor1.monitorId shouldBe openInvalidMonitor1.monitorId
                    openInvalidSSLMonitor1.monitorName shouldBe monitor1.name
                    openInvalidSSLMonitor1.isMonitorEnabled shouldBe monitor1.enabled
                    openInvalidSSLMonitor1.status shouldBe IncidentStatus.ONGOING
                    openInvalidSSLMonitor1.startedAt shouldBe openInvalidMonitor1.startedAt
                    openInvalidSSLMonitor1.endedAt shouldBe null
                    openInvalidSSLMonitor1.updatedAt shouldBe openInvalidMonitor1.updatedAt
                    openInvalidSSLMonitor1.incidentType shouldBe IncidentType.SSL
                    openInvalidSSLMonitor1.details shouldBe null
                }

                incidentsWithoutResolved.forOne { openInvalidSSLMonitor2 ->
                    openInvalidSSLMonitor2.monitorId shouldBe openInvalidMonitor2.monitorId
                    openInvalidSSLMonitor2.monitorName shouldBe monitor2.name
                    openInvalidSSLMonitor2.isMonitorEnabled shouldBe monitor2.enabled
                    openInvalidSSLMonitor2.status shouldBe IncidentStatus.ONGOING
                    openInvalidSSLMonitor2.startedAt shouldBe openInvalidMonitor2.startedAt
                    openInvalidSSLMonitor2.endedAt shouldBe null
                    openInvalidSSLMonitor2.updatedAt shouldBe openInvalidMonitor2.updatedAt
                    openInvalidSSLMonitor2.incidentType shouldBe IncidentType.SSL
                    openInvalidSSLMonitor2.details shouldBe "something"
                }
            }
        }

        `when`("it is called with an explicit period") {

            then("it should ignore the incidents out of that period") {

                val monitor1 = createMonitor(httpMonitorRepository)
                val monitor2 = createMonitor(httpMonitorRepository)

                val openDownMonitor1 = createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                )
                createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(6),
                    endedAt = getCurrentTimestamp().minusDays(4),
                )
                val openInvalidMonitor1 = createSSLEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(4),
                    endedAt = null,
                )
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    endedAt = getCurrentTimestamp().minusDays(5),
                )

                val openDownMonitor2 = createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    error = "sh#t happened"
                )
                val resolvedDownMonitor2 = createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(1),
                    endedAt = getCurrentTimestamp().minusHours(4),
                )
                val openInvalidMonitor2 = createSSLEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(4),
                    endedAt = null,
                    error = "something"
                )
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    endedAt = getCurrentTimestamp().minusDays(5),
                )

                val incidentsInTheLast2Days = incidentRepository.getIncidents(
                    period = Duration.ofDays(2),
                    includeResolved = true,
                )
                incidentsInTheLast2Days shouldHaveSize 5

                incidentsInTheLast2Days.forOne { resolvedMonitor2 ->
                    resolvedMonitor2.monitorId shouldBe resolvedDownMonitor2.monitorId
                    resolvedMonitor2.incidentType shouldBe IncidentType.HTTP
                    resolvedMonitor2.startedAt shouldBe resolvedDownMonitor2.startedAt
                }

                incidentsInTheLast2Days.forOne { openMonitor2 ->
                    openMonitor2.monitorId shouldBe openDownMonitor2.monitorId
                    openMonitor2.incidentType shouldBe IncidentType.HTTP
                    openMonitor2.startedAt shouldBe openDownMonitor2.startedAt
                }

                incidentsInTheLast2Days.forOne { openMonitor1 ->
                    openMonitor1.monitorId shouldBe openDownMonitor1.monitorId
                    openMonitor1.incidentType shouldBe IncidentType.HTTP
                    openMonitor1.startedAt shouldBe openDownMonitor1.startedAt
                }

                incidentsInTheLast2Days.forOne { openInvalidSSLMonitor1 ->
                    openInvalidSSLMonitor1.monitorId shouldBe openInvalidMonitor1.monitorId
                    openInvalidSSLMonitor1.incidentType shouldBe IncidentType.SSL
                    openInvalidSSLMonitor1.startedAt shouldBe openInvalidMonitor1.startedAt
                }

                incidentsInTheLast2Days.forOne { openInvalidSSLMonitor2 ->
                    openInvalidSSLMonitor2.monitorId shouldBe openInvalidMonitor2.monitorId
                    openInvalidSSLMonitor2.incidentType shouldBe IncidentType.SSL
                    openInvalidSSLMonitor2.startedAt shouldBe openInvalidMonitor2.startedAt
                }
            }
        }

        `when`("it is called with an explicit monitorId") {

            then("it should return the incident of that monitor only") {

                val monitor1 = createMonitor(httpMonitorRepository)
                val monitor2 = createMonitor(httpMonitorRepository, enabled = false)

                val openDownMonitor1 = createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                )
                createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(6),
                    endedAt = getCurrentTimestamp().minusDays(4),
                )
                val openInvalidMonitor1 = createSSLEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(4),
                    endedAt = null,
                )
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor1.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    endedAt = getCurrentTimestamp().minusDays(5),
                )

                val openDownMonitor2 = createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    error = "sh#t happened"
                )
                createUptimeEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(6),
                    endedAt = getCurrentTimestamp().minusDays(4),
                )
                val openInvalidMonitor2 = createSSLEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(4),
                    endedAt = null,
                    error = "something"
                )
                createSSLEventRecord(
                    dslContext,
                    monitorId = monitor2.id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp().minusDays(10),
                    endedAt = getCurrentTimestamp().minusDays(5),
                )

                val incidentsOfMonitor1 = incidentRepository.getIncidents(
                    includeResolved = false,
                    monitorId = monitor1.id,
                )
                incidentsOfMonitor1 shouldHaveSize 2

                incidentsOfMonitor1.forOne { openMonitor1 ->
                    openMonitor1.monitorId shouldBe openDownMonitor1.monitorId
                    openMonitor1.status shouldBe IncidentStatus.ONGOING
                    openMonitor1.incidentType shouldBe IncidentType.HTTP
                }

                incidentsOfMonitor1.forOne { openInvalidSSLMonitor1 ->
                    openInvalidSSLMonitor1.monitorId shouldBe openInvalidMonitor1.monitorId
                    openInvalidSSLMonitor1.status shouldBe IncidentStatus.ONGOING
                    openInvalidSSLMonitor1.incidentType shouldBe IncidentType.SSL
                }

                // Should return events for the disabled monitor as well
                val incidentsOfMonitor2 = incidentRepository.getIncidents(
                    includeResolved = false,
                    monitorId = monitor2.id,
                )

                incidentsOfMonitor2 shouldHaveSize 2

                incidentsOfMonitor2.forOne { openMonitor2 ->
                    openMonitor2.monitorId shouldBe openDownMonitor2.monitorId
                    openMonitor2.status shouldBe IncidentStatus.ONGOING
                    openMonitor2.incidentType shouldBe IncidentType.HTTP
                }

                incidentsOfMonitor2.forOne { openInvalidSSLMonitor2 ->
                    openInvalidSSLMonitor2.monitorId shouldBe openInvalidMonitor2.monitorId
                    openInvalidSSLMonitor2.status shouldBe IncidentStatus.ONGOING
                    openInvalidSSLMonitor2.incidentType shouldBe IncidentType.SSL
                }
            }
        }
    }
})
