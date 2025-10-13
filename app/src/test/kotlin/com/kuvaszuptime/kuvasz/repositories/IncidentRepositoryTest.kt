package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.Duration

@MicronautTest(startApplication = false)
class IncidentRepositoryTest(
    httpMonitorRepository: HttpMonitorRepository,
    pushMonitorRepository: PushMonitorRepository,
    incidentRepository: IncidentRepository
) : DatabaseBehaviorSpec() {
    init {

        given("the getIncidents() method") {

            `when`("it is called without explicit parameters") {

                then("it should return the right incidents") {

                    val httpMonitor1 = createHttpMonitor(httpMonitorRepository)
                    val httpMonitor2 = createHttpMonitor(httpMonitorRepository)
                    val pushMonitor1 = createPushMonitor(pushMonitorRepository)
                    val pushMonitor2 = createPushMonitor(pushMonitorRepository)

                    // Should be ignored, it's not an incident
                    createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = UptimeStatus.UP,
                        startedAt = getCurrentTimestamp(),
                        endedAt = null,
                    )
                    createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor1.id,
                        status = UptimeStatus.UP,
                        startedAt = getCurrentTimestamp(),
                        endedAt = null,
                    )
                    // Should be ignored, it's not an incident
                    createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = SslStatus.VALID,
                        startedAt = getCurrentTimestamp().minusDays(90),
                        endedAt = getCurrentTimestamp().minusDays(1),
                    )
                    // Should be ignored, it's not an incident
                    createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = SslStatus.WILL_EXPIRE,
                        startedAt = getCurrentTimestamp().minusDays(20),
                        endedAt = getCurrentTimestamp().minusDays(10),
                    )
                    val openDownHttpMonitor1 = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    val resolvedDownHttpMonitor1 = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownPushMonitor1 = createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    val resolvedDownPushMonitor1 = createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openInvalidMonitor1 = createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(4),
                        endedAt = null,
                    )
                    val resolvedInvalidMonitor1 = createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(10),
                        endedAt = getCurrentTimestamp().minusDays(5),
                    )

                    val openDownHttpMonitor2 = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    val resolvedDownHttpMonitor2 = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownPushMonitor2 = createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    val resolvedDownPushMonitor2 = createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )

                    val openInvalidMonitor2 = createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(4),
                        endedAt = null,
                        error = "something"
                    )
                    val resolvedInvalidMonitor2 = createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(10),
                        endedAt = getCurrentTimestamp().minusDays(5),
                    )

                    val incidentsWithResolved = incidentRepository.getIncidents(includeResolved = true)
                    incidentsWithResolved shouldHaveSize 12

                    incidentsWithResolved.forOne { resolvedHttpMonitor2 ->
                        resolvedHttpMonitor2.monitorId shouldBe resolvedDownHttpMonitor2.monitorId
                        resolvedHttpMonitor2.monitorName shouldBe httpMonitor2.name
                        resolvedHttpMonitor2.isMonitorEnabled shouldBe httpMonitor2.enabled
                        resolvedHttpMonitor2.status shouldBe IncidentStatus.RESOLVED
                        resolvedHttpMonitor2.startedAt shouldBe resolvedDownHttpMonitor2.startedAt
                        resolvedHttpMonitor2.endedAt shouldBe resolvedDownHttpMonitor2.endedAt.shouldNotBeNull()
                        resolvedHttpMonitor2.updatedAt shouldBe resolvedDownHttpMonitor2.updatedAt
                        resolvedHttpMonitor2.incidentType shouldBe IncidentType.HTTP
                        resolvedHttpMonitor2.details shouldBe null
                    }

                    incidentsWithResolved.forOne { resolvedHttpMonitor1 ->
                        resolvedHttpMonitor1.monitorId shouldBe resolvedDownHttpMonitor1.monitorId
                        resolvedHttpMonitor1.monitorName shouldBe httpMonitor1.name
                        resolvedHttpMonitor1.isMonitorEnabled shouldBe httpMonitor1.enabled
                        resolvedHttpMonitor1.status shouldBe IncidentStatus.RESOLVED
                        resolvedHttpMonitor1.startedAt shouldBe resolvedDownHttpMonitor1.startedAt
                        resolvedHttpMonitor1.endedAt shouldBe resolvedDownHttpMonitor1.endedAt.shouldNotBeNull()
                        resolvedHttpMonitor1.updatedAt shouldBe resolvedDownHttpMonitor1.updatedAt
                        resolvedHttpMonitor1.incidentType shouldBe IncidentType.HTTP
                        resolvedHttpMonitor1.details shouldBe null
                    }

                    incidentsWithResolved.forOne { openHttpMonitor2 ->
                        openHttpMonitor2.monitorId shouldBe openDownHttpMonitor2.monitorId
                        openHttpMonitor2.monitorName shouldBe httpMonitor2.name
                        openHttpMonitor2.isMonitorEnabled shouldBe httpMonitor2.enabled
                        openHttpMonitor2.status shouldBe IncidentStatus.ONGOING
                        openHttpMonitor2.startedAt shouldBe openDownHttpMonitor2.startedAt
                        openHttpMonitor2.endedAt shouldBe null
                        openHttpMonitor2.updatedAt shouldBe openDownHttpMonitor2.updatedAt
                        openHttpMonitor2.incidentType shouldBe IncidentType.HTTP
                        openHttpMonitor2.details shouldBe openDownHttpMonitor2.error
                    }

                    incidentsWithResolved.forOne { openHttpMonitor1 ->
                        openHttpMonitor1.monitorId shouldBe openDownHttpMonitor1.monitorId
                        openHttpMonitor1.monitorName shouldBe httpMonitor1.name
                        openHttpMonitor1.isMonitorEnabled shouldBe httpMonitor1.enabled
                        openHttpMonitor1.status shouldBe IncidentStatus.ONGOING
                        openHttpMonitor1.startedAt shouldBe openDownHttpMonitor1.startedAt
                        openHttpMonitor1.endedAt shouldBe null
                        openHttpMonitor1.updatedAt shouldBe openDownHttpMonitor1.updatedAt
                        openHttpMonitor1.incidentType shouldBe IncidentType.HTTP
                        openHttpMonitor1.details shouldBe null
                    }

                    incidentsWithResolved.forOne { resolvedPushMonitor2 ->
                        resolvedPushMonitor2.monitorId shouldBe resolvedDownPushMonitor2.monitorId
                        resolvedPushMonitor2.monitorName shouldBe pushMonitor2.name
                        resolvedPushMonitor2.isMonitorEnabled shouldBe pushMonitor2.enabled
                        resolvedPushMonitor2.status shouldBe IncidentStatus.RESOLVED
                        resolvedPushMonitor2.startedAt shouldBe resolvedDownPushMonitor2.startedAt
                        resolvedPushMonitor2.endedAt shouldBe resolvedDownPushMonitor2.endedAt.shouldNotBeNull()
                        resolvedPushMonitor2.updatedAt shouldBe resolvedDownPushMonitor2.updatedAt
                        resolvedPushMonitor2.incidentType shouldBe IncidentType.PUSH
                        resolvedPushMonitor2.details shouldBe null
                    }

                    incidentsWithResolved.forOne { resolvedPushMonitor1 ->
                        resolvedPushMonitor1.monitorId shouldBe resolvedDownPushMonitor1.monitorId
                        resolvedPushMonitor1.monitorName shouldBe pushMonitor1.name
                        resolvedPushMonitor1.isMonitorEnabled shouldBe pushMonitor1.enabled
                        resolvedPushMonitor1.status shouldBe IncidentStatus.RESOLVED
                        resolvedPushMonitor1.startedAt shouldBe resolvedDownPushMonitor1.startedAt
                        resolvedPushMonitor1.endedAt shouldBe resolvedDownPushMonitor1.endedAt.shouldNotBeNull()
                        resolvedPushMonitor1.updatedAt shouldBe resolvedDownPushMonitor1.updatedAt
                        resolvedPushMonitor1.incidentType shouldBe IncidentType.PUSH
                        resolvedPushMonitor1.details shouldBe null
                    }

                    incidentsWithResolved.forOne { openPushMonitor2 ->
                        openPushMonitor2.monitorId shouldBe openDownPushMonitor2.monitorId
                        openPushMonitor2.monitorName shouldBe pushMonitor2.name
                        openPushMonitor2.isMonitorEnabled shouldBe pushMonitor2.enabled
                        openPushMonitor2.status shouldBe IncidentStatus.ONGOING
                        openPushMonitor2.startedAt shouldBe openDownPushMonitor2.startedAt
                        openPushMonitor2.endedAt shouldBe null
                        openPushMonitor2.updatedAt shouldBe openDownPushMonitor2.updatedAt
                        openPushMonitor2.incidentType shouldBe IncidentType.PUSH
                        openPushMonitor2.details shouldBe openDownPushMonitor2.error
                    }

                    incidentsWithResolved.forOne { openPushMonitor1 ->
                        openPushMonitor1.monitorId shouldBe openDownPushMonitor1.monitorId
                        openPushMonitor1.monitorName shouldBe pushMonitor1.name
                        openPushMonitor1.isMonitorEnabled shouldBe pushMonitor1.enabled
                        openPushMonitor1.status shouldBe IncidentStatus.ONGOING
                        openPushMonitor1.startedAt shouldBe openDownPushMonitor1.startedAt
                        openPushMonitor1.endedAt shouldBe null
                        openPushMonitor1.updatedAt shouldBe openDownPushMonitor1.updatedAt
                        openPushMonitor1.incidentType shouldBe IncidentType.PUSH
                        openPushMonitor1.details shouldBe null
                    }

                    incidentsWithResolved.forOne { openInvalidSSLMonitor1 ->
                        openInvalidSSLMonitor1.monitorId shouldBe openInvalidMonitor1.monitorId
                        openInvalidSSLMonitor1.monitorName shouldBe httpMonitor1.name
                        openInvalidSSLMonitor1.isMonitorEnabled shouldBe httpMonitor1.enabled
                        openInvalidSSLMonitor1.status shouldBe IncidentStatus.ONGOING
                        openInvalidSSLMonitor1.startedAt shouldBe openInvalidMonitor1.startedAt
                        openInvalidSSLMonitor1.endedAt shouldBe null
                        openInvalidSSLMonitor1.updatedAt shouldBe openInvalidMonitor1.updatedAt
                        openInvalidSSLMonitor1.incidentType shouldBe IncidentType.SSL
                        openInvalidSSLMonitor1.details shouldBe null
                    }

                    incidentsWithResolved.forOne { openInvalidSSLMonitor2 ->
                        openInvalidSSLMonitor2.monitorId shouldBe openInvalidMonitor2.monitorId
                        openInvalidSSLMonitor2.monitorName shouldBe httpMonitor2.name
                        openInvalidSSLMonitor2.isMonitorEnabled shouldBe httpMonitor2.enabled
                        openInvalidSSLMonitor2.status shouldBe IncidentStatus.ONGOING
                        openInvalidSSLMonitor2.startedAt shouldBe openInvalidMonitor2.startedAt
                        openInvalidSSLMonitor2.endedAt shouldBe null
                        openInvalidSSLMonitor2.updatedAt shouldBe openInvalidMonitor2.updatedAt
                        openInvalidSSLMonitor2.incidentType shouldBe IncidentType.SSL
                        openInvalidSSLMonitor2.details shouldBe "something"
                    }

                    incidentsWithResolved.forOne { resolvedInvalidSSLMonitor1 ->
                        resolvedInvalidSSLMonitor1.monitorId shouldBe resolvedInvalidMonitor1.monitorId
                        resolvedInvalidSSLMonitor1.monitorName shouldBe httpMonitor1.name
                        resolvedInvalidSSLMonitor1.isMonitorEnabled shouldBe httpMonitor1.enabled
                        resolvedInvalidSSLMonitor1.status shouldBe IncidentStatus.RESOLVED
                        resolvedInvalidSSLMonitor1.startedAt shouldBe resolvedInvalidMonitor1.startedAt
                        resolvedInvalidSSLMonitor1.endedAt shouldBe resolvedInvalidMonitor1.endedAt
                        resolvedInvalidSSLMonitor1.updatedAt shouldBe resolvedInvalidMonitor1.updatedAt
                        resolvedInvalidSSLMonitor1.incidentType shouldBe IncidentType.SSL
                        resolvedInvalidSSLMonitor1.details shouldBe null
                    }

                    incidentsWithResolved.forOne { resolvedInvalidSSLMonitor2 ->
                        resolvedInvalidSSLMonitor2.monitorId shouldBe resolvedInvalidMonitor2.monitorId
                        resolvedInvalidSSLMonitor2.monitorName shouldBe httpMonitor2.name
                        resolvedInvalidSSLMonitor2.isMonitorEnabled shouldBe httpMonitor2.enabled
                        resolvedInvalidSSLMonitor2.status shouldBe IncidentStatus.RESOLVED
                        resolvedInvalidSSLMonitor2.startedAt shouldBe resolvedInvalidMonitor2.startedAt
                        resolvedInvalidSSLMonitor2.endedAt shouldBe resolvedInvalidMonitor2.endedAt
                        resolvedInvalidSSLMonitor2.updatedAt shouldBe resolvedInvalidMonitor2.updatedAt
                        resolvedInvalidSSLMonitor2.incidentType shouldBe IncidentType.SSL
                        resolvedInvalidSSLMonitor2.details shouldBe null
                    }

                    val incidentsWithoutResolved = incidentRepository.getIncidents(includeResolved = false)
                    incidentsWithoutResolved shouldHaveSize 6

                    incidentsWithoutResolved.forOne { openHttpMonitor2 ->
                        openHttpMonitor2.monitorId shouldBe openDownHttpMonitor2.monitorId
                        openHttpMonitor2.monitorName shouldBe httpMonitor2.name
                        openHttpMonitor2.isMonitorEnabled shouldBe httpMonitor2.enabled
                        openHttpMonitor2.status shouldBe IncidentStatus.ONGOING
                        openHttpMonitor2.startedAt shouldBe openDownHttpMonitor2.startedAt
                        openHttpMonitor2.endedAt shouldBe null
                        openHttpMonitor2.updatedAt shouldBe openDownHttpMonitor2.updatedAt
                        openHttpMonitor2.incidentType shouldBe IncidentType.HTTP
                        openHttpMonitor2.details shouldBe openDownHttpMonitor2.error
                    }

                    incidentsWithoutResolved.forOne { openHttpMonitor1 ->
                        openHttpMonitor1.monitorId shouldBe openDownHttpMonitor1.monitorId
                        openHttpMonitor1.monitorName shouldBe httpMonitor1.name
                        openHttpMonitor1.isMonitorEnabled shouldBe httpMonitor1.enabled
                        openHttpMonitor1.status shouldBe IncidentStatus.ONGOING
                        openHttpMonitor1.startedAt shouldBe openDownHttpMonitor1.startedAt
                        openHttpMonitor1.endedAt shouldBe null
                        openHttpMonitor1.updatedAt shouldBe openDownHttpMonitor1.updatedAt
                        openHttpMonitor1.incidentType shouldBe IncidentType.HTTP
                        openHttpMonitor1.details shouldBe null
                    }

                    incidentsWithoutResolved.forOne { openPushMonitor2 ->
                        openPushMonitor2.monitorId shouldBe openDownPushMonitor2.monitorId
                        openPushMonitor2.monitorName shouldBe pushMonitor2.name
                        openPushMonitor2.isMonitorEnabled shouldBe pushMonitor2.enabled
                        openPushMonitor2.status shouldBe IncidentStatus.ONGOING
                        openPushMonitor2.startedAt shouldBe openDownPushMonitor2.startedAt
                        openPushMonitor2.endedAt shouldBe null
                        openPushMonitor2.updatedAt shouldBe openDownPushMonitor2.updatedAt
                        openPushMonitor2.incidentType shouldBe IncidentType.PUSH
                        openPushMonitor2.details shouldBe openDownPushMonitor2.error
                    }

                    incidentsWithoutResolved.forOne { openPushMonitor1 ->
                        openPushMonitor1.monitorId shouldBe openDownPushMonitor1.monitorId
                        openPushMonitor1.monitorName shouldBe pushMonitor1.name
                        openPushMonitor1.isMonitorEnabled shouldBe pushMonitor1.enabled
                        openPushMonitor1.status shouldBe IncidentStatus.ONGOING
                        openPushMonitor1.startedAt shouldBe openDownPushMonitor1.startedAt
                        openPushMonitor1.endedAt shouldBe null
                        openPushMonitor1.updatedAt shouldBe openDownPushMonitor1.updatedAt
                        openPushMonitor1.incidentType shouldBe IncidentType.PUSH
                        openPushMonitor1.details shouldBe null
                    }

                    incidentsWithoutResolved.forOne { openInvalidSSLMonitor1 ->
                        openInvalidSSLMonitor1.monitorId shouldBe openInvalidMonitor1.monitorId
                        openInvalidSSLMonitor1.monitorName shouldBe httpMonitor1.name
                        openInvalidSSLMonitor1.isMonitorEnabled shouldBe httpMonitor1.enabled
                        openInvalidSSLMonitor1.status shouldBe IncidentStatus.ONGOING
                        openInvalidSSLMonitor1.startedAt shouldBe openInvalidMonitor1.startedAt
                        openInvalidSSLMonitor1.endedAt shouldBe null
                        openInvalidSSLMonitor1.updatedAt shouldBe openInvalidMonitor1.updatedAt
                        openInvalidSSLMonitor1.incidentType shouldBe IncidentType.SSL
                        openInvalidSSLMonitor1.details shouldBe null
                    }

                    incidentsWithoutResolved.forOne { openInvalidSSLMonitor2 ->
                        openInvalidSSLMonitor2.monitorId shouldBe openInvalidMonitor2.monitorId
                        openInvalidSSLMonitor2.monitorName shouldBe httpMonitor2.name
                        openInvalidSSLMonitor2.isMonitorEnabled shouldBe httpMonitor2.enabled
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

                    val httpMonitor1 = createHttpMonitor(httpMonitorRepository)
                    val httpMonitor2 = createHttpMonitor(httpMonitorRepository)
                    val pushMonitor1 = createPushMonitor(pushMonitorRepository)
                    val pushMonitor2 = createPushMonitor(pushMonitorRepository)

                    val openDownHttpMonitor1 = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownPushMonitor1 = createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openInvalidMonitor1 = createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(4),
                        endedAt = null,
                    )
                    createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(10),
                        endedAt = getCurrentTimestamp().minusDays(5),
                    )

                    val openDownHttpMonitor2 = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    val resolvedDownHttpMonitor2 = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(1),
                        endedAt = getCurrentTimestamp().minusHours(4),
                    )

                    val openDownPushMonitor2 = createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    val resolvedDownPushMonitor2 = createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(1),
                        endedAt = getCurrentTimestamp().minusHours(4),
                    )

                    val openInvalidMonitor2 = createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(4),
                        endedAt = null,
                        error = "something"
                    )
                    createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(10),
                        endedAt = getCurrentTimestamp().minusDays(5),
                    )

                    val incidentsInTheLast2Days = incidentRepository.getIncidents(
                        period = Duration.ofDays(2),
                        includeResolved = true,
                    )
                    incidentsInTheLast2Days shouldHaveSize 8

                    incidentsInTheLast2Days.forOne { resolvedHttpMonitor2 ->
                        resolvedHttpMonitor2.monitorId shouldBe resolvedDownHttpMonitor2.monitorId
                        resolvedHttpMonitor2.incidentType shouldBe IncidentType.HTTP
                        resolvedHttpMonitor2.startedAt shouldBe resolvedDownHttpMonitor2.startedAt
                    }

                    incidentsInTheLast2Days.forOne { openHttpMonitor2 ->
                        openHttpMonitor2.monitorId shouldBe openDownHttpMonitor2.monitorId
                        openHttpMonitor2.incidentType shouldBe IncidentType.HTTP
                        openHttpMonitor2.startedAt shouldBe openDownHttpMonitor2.startedAt
                    }

                    incidentsInTheLast2Days.forOne { openHttpMonitor1 ->
                        openHttpMonitor1.monitorId shouldBe openDownHttpMonitor1.monitorId
                        openHttpMonitor1.incidentType shouldBe IncidentType.HTTP
                        openHttpMonitor1.startedAt shouldBe openDownHttpMonitor1.startedAt
                    }

                    incidentsInTheLast2Days.forOne { resolvedPushMonitor2 ->
                        resolvedPushMonitor2.monitorId shouldBe resolvedDownPushMonitor2.monitorId
                        resolvedPushMonitor2.incidentType shouldBe IncidentType.PUSH
                        resolvedPushMonitor2.startedAt shouldBe resolvedDownPushMonitor2.startedAt
                    }

                    incidentsInTheLast2Days.forOne { openPushMonitor2 ->
                        openPushMonitor2.monitorId shouldBe openDownPushMonitor2.monitorId
                        openPushMonitor2.incidentType shouldBe IncidentType.PUSH
                        openPushMonitor2.startedAt shouldBe openDownPushMonitor2.startedAt
                    }

                    incidentsInTheLast2Days.forOne { openPushMonitor1 ->
                        openPushMonitor1.monitorId shouldBe openDownPushMonitor1.monitorId
                        openPushMonitor1.incidentType shouldBe IncidentType.PUSH
                        openPushMonitor1.startedAt shouldBe openDownPushMonitor1.startedAt
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

                    val httpMonitor1 = createHttpMonitor(httpMonitorRepository)
                    val httpMonitor2 = createHttpMonitor(httpMonitorRepository, enabled = false)
                    val pushMonitor1 = createPushMonitor(pushMonitorRepository)
                    val pushMonitor2 = createPushMonitor(pushMonitorRepository, enabled = false)

                    val openDownHttpMonitor1 = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openInvalidMonitor1 = createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(4),
                        endedAt = null,
                    )
                    createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor1.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(10),
                        endedAt = getCurrentTimestamp().minusDays(5),
                    )

                    val openDownPushMonitor1 = createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )

                    val openDownHttpMonitor2 = createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openInvalidMonitor2 = createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(4),
                        endedAt = null,
                        error = "something"
                    )
                    createSSLEventRecord(
                        dslContext,
                        monitorId = httpMonitor2.id,
                        status = SslStatus.INVALID,
                        startedAt = getCurrentTimestamp().minusDays(10),
                        endedAt = getCurrentTimestamp().minusDays(5),
                    )

                    val openDownPushMonitor2 = createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    createPushUptimeEventRecord(
                        dslContext,
                        monitorId = pushMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )

                    val incidentsOfHttpMonitor1 = incidentRepository.getIncidents(
                        includeResolved = false,
                        monitorId = httpMonitor1.id,
                    )
                    incidentsOfHttpMonitor1 shouldHaveSize 2

                    incidentsOfHttpMonitor1.forOne { openMonitor1 ->
                        openMonitor1.monitorId shouldBe openDownHttpMonitor1.monitorId
                        openMonitor1.status shouldBe IncidentStatus.ONGOING
                        openMonitor1.incidentType shouldBe IncidentType.HTTP
                    }

                    incidentsOfHttpMonitor1.forOne { openInvalidSSLMonitor1 ->
                        openInvalidSSLMonitor1.monitorId shouldBe openInvalidMonitor1.monitorId
                        openInvalidSSLMonitor1.status shouldBe IncidentStatus.ONGOING
                        openInvalidSSLMonitor1.incidentType shouldBe IncidentType.SSL
                    }

                    val incidentsOfPushMonitor1 = incidentRepository.getIncidents(
                        includeResolved = false,
                        monitorId = pushMonitor1.id,
                    )
                    incidentsOfPushMonitor1 shouldHaveSize 1

                    incidentsOfPushMonitor1.forOne { openMonitor1 ->
                        openMonitor1.monitorId shouldBe openDownPushMonitor1.monitorId
                        openMonitor1.status shouldBe IncidentStatus.ONGOING
                        openMonitor1.incidentType shouldBe IncidentType.PUSH
                    }

                    // Should return events for the disabled monitor as well
                    val incidentsOfHttpMonitor2 = incidentRepository.getIncidents(
                        includeResolved = false,
                        monitorId = httpMonitor2.id,
                    )

                    incidentsOfHttpMonitor2 shouldHaveSize 2

                    incidentsOfHttpMonitor2.forOne { openMonitor2 ->
                        openMonitor2.monitorId shouldBe openDownHttpMonitor2.monitorId
                        openMonitor2.status shouldBe IncidentStatus.ONGOING
                        openMonitor2.incidentType shouldBe IncidentType.HTTP
                    }

                    incidentsOfHttpMonitor2.forOne { openInvalidSSLMonitor2 ->
                        openInvalidSSLMonitor2.monitorId shouldBe openInvalidMonitor2.monitorId
                        openInvalidSSLMonitor2.status shouldBe IncidentStatus.ONGOING
                        openInvalidSSLMonitor2.incidentType shouldBe IncidentType.SSL
                    }

                    val incidentsOfPushMonitor2 = incidentRepository.getIncidents(
                        includeResolved = false,
                        monitorId = pushMonitor2.id,
                    )

                    incidentsOfPushMonitor2 shouldHaveSize 1

                    incidentsOfPushMonitor2.forOne { openMonitor2 ->
                        openMonitor2.monitorId shouldBe openDownPushMonitor2.monitorId
                        openMonitor2.status shouldBe IncidentStatus.ONGOING
                        openMonitor2.incidentType shouldBe IncidentType.PUSH
                    }
                }
            }
        }
    }
}
