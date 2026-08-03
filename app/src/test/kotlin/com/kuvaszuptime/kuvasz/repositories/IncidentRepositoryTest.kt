package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpUptimeEventRecord
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
    icmpMonitorRepository: IcmpMonitorRepository,
    tcpMonitorRepository: TcpMonitorRepository,
    dnsMonitorRepository: DnsMonitorRepository,
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
                    val icmpMonitor1 = createIcmpMonitor(icmpMonitorRepository)
                    val icmpMonitor2 = createIcmpMonitor(icmpMonitorRepository)

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
                    // Should be ignored, it's not an incident
                    createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor1.id,
                        status = UptimeStatus.UP,
                        startedAt = getCurrentTimestamp(),
                        endedAt = null,
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

                    val openDownIcmpMonitor1 = createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    val resolvedDownIcmpMonitor1 = createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownIcmpMonitor2 = createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    val resolvedDownIcmpMonitor2 = createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )

                    val tcpMonitor1 = createTcpMonitor(tcpMonitorRepository)
                    val tcpMonitor2 = createTcpMonitor(tcpMonitorRepository)
                    val openDownTcpMonitor1 = createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    val resolvedDownTcpMonitor1 = createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownTcpMonitor2 = createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    val resolvedDownTcpMonitor2 = createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )

                    val dnsMonitor1 = createDnsMonitor(dnsMonitorRepository)
                    val dnsMonitor2 = createDnsMonitor(dnsMonitorRepository)
                    val openDownDnsMonitor1 = createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    val resolvedDownDnsMonitor1 = createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownDnsMonitor2 = createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    val resolvedDownDnsMonitor2 = createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )

                    val incidentsWithResolved = incidentRepository.getIncidents(includeResolved = true)
                    incidentsWithResolved shouldHaveSize 24

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

                    incidentsWithResolved.forOne { resolvedIcmpMonitor2 ->
                        resolvedIcmpMonitor2.monitorId shouldBe resolvedDownIcmpMonitor2.monitorId
                        resolvedIcmpMonitor2.monitorName shouldBe icmpMonitor2.name
                        resolvedIcmpMonitor2.isMonitorEnabled shouldBe icmpMonitor2.enabled
                        resolvedIcmpMonitor2.status shouldBe IncidentStatus.RESOLVED
                        resolvedIcmpMonitor2.startedAt shouldBe resolvedDownIcmpMonitor2.startedAt
                        resolvedIcmpMonitor2.endedAt shouldBe resolvedDownIcmpMonitor2.endedAt.shouldNotBeNull()
                        resolvedIcmpMonitor2.updatedAt shouldBe resolvedDownIcmpMonitor2.updatedAt
                        resolvedIcmpMonitor2.incidentType shouldBe IncidentType.ICMP
                        resolvedIcmpMonitor2.details shouldBe null
                    }

                    incidentsWithResolved.forOne { resolvedIcmpMonitor1 ->
                        resolvedIcmpMonitor1.monitorId shouldBe resolvedDownIcmpMonitor1.monitorId
                        resolvedIcmpMonitor1.monitorName shouldBe icmpMonitor1.name
                        resolvedIcmpMonitor1.isMonitorEnabled shouldBe icmpMonitor1.enabled
                        resolvedIcmpMonitor1.status shouldBe IncidentStatus.RESOLVED
                        resolvedIcmpMonitor1.startedAt shouldBe resolvedDownIcmpMonitor1.startedAt
                        resolvedIcmpMonitor1.endedAt shouldBe resolvedDownIcmpMonitor1.endedAt.shouldNotBeNull()
                        resolvedIcmpMonitor1.updatedAt shouldBe resolvedDownIcmpMonitor1.updatedAt
                        resolvedIcmpMonitor1.incidentType shouldBe IncidentType.ICMP
                        resolvedIcmpMonitor1.details shouldBe null
                    }

                    incidentsWithResolved.forOne { openIcmpMonitor2 ->
                        openIcmpMonitor2.monitorId shouldBe openDownIcmpMonitor2.monitorId
                        openIcmpMonitor2.monitorName shouldBe icmpMonitor2.name
                        openIcmpMonitor2.isMonitorEnabled shouldBe icmpMonitor2.enabled
                        openIcmpMonitor2.status shouldBe IncidentStatus.ONGOING
                        openIcmpMonitor2.startedAt shouldBe openDownIcmpMonitor2.startedAt
                        openIcmpMonitor2.endedAt shouldBe null
                        openIcmpMonitor2.updatedAt shouldBe openDownIcmpMonitor2.updatedAt
                        openIcmpMonitor2.incidentType shouldBe IncidentType.ICMP
                        openIcmpMonitor2.details shouldBe openDownIcmpMonitor2.error
                    }

                    incidentsWithResolved.forOne { openIcmpMonitor1 ->
                        openIcmpMonitor1.monitorId shouldBe openDownIcmpMonitor1.monitorId
                        openIcmpMonitor1.monitorName shouldBe icmpMonitor1.name
                        openIcmpMonitor1.isMonitorEnabled shouldBe icmpMonitor1.enabled
                        openIcmpMonitor1.status shouldBe IncidentStatus.ONGOING
                        openIcmpMonitor1.startedAt shouldBe openDownIcmpMonitor1.startedAt
                        openIcmpMonitor1.endedAt shouldBe null
                        openIcmpMonitor1.updatedAt shouldBe openDownIcmpMonitor1.updatedAt
                        openIcmpMonitor1.incidentType shouldBe IncidentType.ICMP
                        openIcmpMonitor1.details shouldBe null
                    }

                    incidentsWithResolved.forOne { resolvedTcpMonitor2 ->
                        resolvedTcpMonitor2.monitorId shouldBe resolvedDownTcpMonitor2.monitorId
                        resolvedTcpMonitor2.monitorName shouldBe tcpMonitor2.name
                        resolvedTcpMonitor2.isMonitorEnabled shouldBe tcpMonitor2.enabled
                        resolvedTcpMonitor2.status shouldBe IncidentStatus.RESOLVED
                        resolvedTcpMonitor2.startedAt shouldBe resolvedDownTcpMonitor2.startedAt
                        resolvedTcpMonitor2.endedAt shouldBe resolvedDownTcpMonitor2.endedAt.shouldNotBeNull()
                        resolvedTcpMonitor2.updatedAt shouldBe resolvedDownTcpMonitor2.updatedAt
                        resolvedTcpMonitor2.incidentType shouldBe IncidentType.TCP
                        resolvedTcpMonitor2.details shouldBe null
                    }

                    incidentsWithResolved.forOne { resolvedTcpMonitor1 ->
                        resolvedTcpMonitor1.monitorId shouldBe resolvedDownTcpMonitor1.monitorId
                        resolvedTcpMonitor1.monitorName shouldBe tcpMonitor1.name
                        resolvedTcpMonitor1.isMonitorEnabled shouldBe tcpMonitor1.enabled
                        resolvedTcpMonitor1.status shouldBe IncidentStatus.RESOLVED
                        resolvedTcpMonitor1.startedAt shouldBe resolvedDownTcpMonitor1.startedAt
                        resolvedTcpMonitor1.endedAt shouldBe resolvedDownTcpMonitor1.endedAt.shouldNotBeNull()
                        resolvedTcpMonitor1.updatedAt shouldBe resolvedDownTcpMonitor1.updatedAt
                        resolvedTcpMonitor1.incidentType shouldBe IncidentType.TCP
                        resolvedTcpMonitor1.details shouldBe null
                    }

                    incidentsWithResolved.forOne { openTcpMonitor2 ->
                        openTcpMonitor2.monitorId shouldBe openDownTcpMonitor2.monitorId
                        openTcpMonitor2.monitorName shouldBe tcpMonitor2.name
                        openTcpMonitor2.isMonitorEnabled shouldBe tcpMonitor2.enabled
                        openTcpMonitor2.status shouldBe IncidentStatus.ONGOING
                        openTcpMonitor2.startedAt shouldBe openDownTcpMonitor2.startedAt
                        openTcpMonitor2.endedAt shouldBe null
                        openTcpMonitor2.updatedAt shouldBe openDownTcpMonitor2.updatedAt
                        openTcpMonitor2.incidentType shouldBe IncidentType.TCP
                        openTcpMonitor2.details shouldBe openDownTcpMonitor2.error
                    }

                    incidentsWithResolved.forOne { openTcpMonitor1 ->
                        openTcpMonitor1.monitorId shouldBe openDownTcpMonitor1.monitorId
                        openTcpMonitor1.monitorName shouldBe tcpMonitor1.name
                        openTcpMonitor1.isMonitorEnabled shouldBe tcpMonitor1.enabled
                        openTcpMonitor1.status shouldBe IncidentStatus.ONGOING
                        openTcpMonitor1.startedAt shouldBe openDownTcpMonitor1.startedAt
                        openTcpMonitor1.endedAt shouldBe null
                        openTcpMonitor1.updatedAt shouldBe openDownTcpMonitor1.updatedAt
                        openTcpMonitor1.incidentType shouldBe IncidentType.TCP
                        openTcpMonitor1.details shouldBe null
                    }

                    incidentsWithResolved.forOne { resolvedDnsMonitor2 ->
                        resolvedDnsMonitor2.monitorId shouldBe resolvedDownDnsMonitor2.monitorId
                        resolvedDnsMonitor2.monitorName shouldBe dnsMonitor2.name
                        resolvedDnsMonitor2.isMonitorEnabled shouldBe dnsMonitor2.enabled
                        resolvedDnsMonitor2.status shouldBe IncidentStatus.RESOLVED
                        resolvedDnsMonitor2.startedAt shouldBe resolvedDownDnsMonitor2.startedAt
                        resolvedDnsMonitor2.endedAt shouldBe resolvedDownDnsMonitor2.endedAt.shouldNotBeNull()
                        resolvedDnsMonitor2.updatedAt shouldBe resolvedDownDnsMonitor2.updatedAt
                        resolvedDnsMonitor2.incidentType shouldBe IncidentType.DNS
                        resolvedDnsMonitor2.details shouldBe null
                    }

                    incidentsWithResolved.forOne { resolvedDnsMonitor1 ->
                        resolvedDnsMonitor1.monitorId shouldBe resolvedDownDnsMonitor1.monitorId
                        resolvedDnsMonitor1.monitorName shouldBe dnsMonitor1.name
                        resolvedDnsMonitor1.isMonitorEnabled shouldBe dnsMonitor1.enabled
                        resolvedDnsMonitor1.status shouldBe IncidentStatus.RESOLVED
                        resolvedDnsMonitor1.startedAt shouldBe resolvedDownDnsMonitor1.startedAt
                        resolvedDnsMonitor1.endedAt shouldBe resolvedDownDnsMonitor1.endedAt.shouldNotBeNull()
                        resolvedDnsMonitor1.updatedAt shouldBe resolvedDownDnsMonitor1.updatedAt
                        resolvedDnsMonitor1.incidentType shouldBe IncidentType.DNS
                        resolvedDnsMonitor1.details shouldBe null
                    }

                    incidentsWithResolved.forOne { openDnsMonitor2 ->
                        openDnsMonitor2.monitorId shouldBe openDownDnsMonitor2.monitorId
                        openDnsMonitor2.monitorName shouldBe dnsMonitor2.name
                        openDnsMonitor2.isMonitorEnabled shouldBe dnsMonitor2.enabled
                        openDnsMonitor2.status shouldBe IncidentStatus.ONGOING
                        openDnsMonitor2.startedAt shouldBe openDownDnsMonitor2.startedAt
                        openDnsMonitor2.endedAt shouldBe null
                        openDnsMonitor2.updatedAt shouldBe openDownDnsMonitor2.updatedAt
                        openDnsMonitor2.incidentType shouldBe IncidentType.DNS
                        openDnsMonitor2.details shouldBe openDownDnsMonitor2.error
                    }

                    incidentsWithResolved.forOne { openDnsMonitor1 ->
                        openDnsMonitor1.monitorId shouldBe openDownDnsMonitor1.monitorId
                        openDnsMonitor1.monitorName shouldBe dnsMonitor1.name
                        openDnsMonitor1.isMonitorEnabled shouldBe dnsMonitor1.enabled
                        openDnsMonitor1.status shouldBe IncidentStatus.ONGOING
                        openDnsMonitor1.startedAt shouldBe openDownDnsMonitor1.startedAt
                        openDnsMonitor1.endedAt shouldBe null
                        openDnsMonitor1.updatedAt shouldBe openDownDnsMonitor1.updatedAt
                        openDnsMonitor1.incidentType shouldBe IncidentType.DNS
                        openDnsMonitor1.details shouldBe null
                    }

                    val incidentsWithoutResolved = incidentRepository.getIncidents(includeResolved = false)
                    incidentsWithoutResolved shouldHaveSize 12

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

                    incidentsWithoutResolved.forOne { openIcmpMonitor2 ->
                        openIcmpMonitor2.monitorId shouldBe openDownIcmpMonitor2.monitorId
                        openIcmpMonitor2.monitorName shouldBe icmpMonitor2.name
                        openIcmpMonitor2.isMonitorEnabled shouldBe icmpMonitor2.enabled
                        openIcmpMonitor2.status shouldBe IncidentStatus.ONGOING
                        openIcmpMonitor2.startedAt shouldBe openDownIcmpMonitor2.startedAt
                        openIcmpMonitor2.endedAt shouldBe null
                        openIcmpMonitor2.updatedAt shouldBe openDownIcmpMonitor2.updatedAt
                        openIcmpMonitor2.incidentType shouldBe IncidentType.ICMP
                        openIcmpMonitor2.details shouldBe openDownIcmpMonitor2.error
                    }

                    incidentsWithoutResolved.forOne { openIcmpMonitor1 ->
                        openIcmpMonitor1.monitorId shouldBe openDownIcmpMonitor1.monitorId
                        openIcmpMonitor1.monitorName shouldBe icmpMonitor1.name
                        openIcmpMonitor1.isMonitorEnabled shouldBe icmpMonitor1.enabled
                        openIcmpMonitor1.status shouldBe IncidentStatus.ONGOING
                        openIcmpMonitor1.startedAt shouldBe openDownIcmpMonitor1.startedAt
                        openIcmpMonitor1.endedAt shouldBe null
                        openIcmpMonitor1.updatedAt shouldBe openDownIcmpMonitor1.updatedAt
                        openIcmpMonitor1.incidentType shouldBe IncidentType.ICMP
                        openIcmpMonitor1.details shouldBe null
                    }

                    incidentsWithoutResolved.forOne { openTcpMonitor2 ->
                        openTcpMonitor2.monitorId shouldBe openDownTcpMonitor2.monitorId
                        openTcpMonitor2.monitorName shouldBe tcpMonitor2.name
                        openTcpMonitor2.isMonitorEnabled shouldBe tcpMonitor2.enabled
                        openTcpMonitor2.status shouldBe IncidentStatus.ONGOING
                        openTcpMonitor2.startedAt shouldBe openDownTcpMonitor2.startedAt
                        openTcpMonitor2.endedAt shouldBe null
                        openTcpMonitor2.updatedAt shouldBe openDownTcpMonitor2.updatedAt
                        openTcpMonitor2.incidentType shouldBe IncidentType.TCP
                        openTcpMonitor2.details shouldBe openDownTcpMonitor2.error
                    }

                    incidentsWithoutResolved.forOne { openTcpMonitor1 ->
                        openTcpMonitor1.monitorId shouldBe openDownTcpMonitor1.monitorId
                        openTcpMonitor1.monitorName shouldBe tcpMonitor1.name
                        openTcpMonitor1.isMonitorEnabled shouldBe tcpMonitor1.enabled
                        openTcpMonitor1.status shouldBe IncidentStatus.ONGOING
                        openTcpMonitor1.startedAt shouldBe openDownTcpMonitor1.startedAt
                        openTcpMonitor1.endedAt shouldBe null
                        openTcpMonitor1.updatedAt shouldBe openDownTcpMonitor1.updatedAt
                        openTcpMonitor1.incidentType shouldBe IncidentType.TCP
                        openTcpMonitor1.details shouldBe null
                    }

                    incidentsWithoutResolved.forOne { openDnsMonitor2 ->
                        openDnsMonitor2.monitorId shouldBe openDownDnsMonitor2.monitorId
                        openDnsMonitor2.monitorName shouldBe dnsMonitor2.name
                        openDnsMonitor2.isMonitorEnabled shouldBe dnsMonitor2.enabled
                        openDnsMonitor2.status shouldBe IncidentStatus.ONGOING
                        openDnsMonitor2.startedAt shouldBe openDownDnsMonitor2.startedAt
                        openDnsMonitor2.endedAt shouldBe null
                        openDnsMonitor2.updatedAt shouldBe openDownDnsMonitor2.updatedAt
                        openDnsMonitor2.incidentType shouldBe IncidentType.DNS
                        openDnsMonitor2.details shouldBe openDownDnsMonitor2.error
                    }

                    incidentsWithoutResolved.forOne { openDnsMonitor1 ->
                        openDnsMonitor1.monitorId shouldBe openDownDnsMonitor1.monitorId
                        openDnsMonitor1.monitorName shouldBe dnsMonitor1.name
                        openDnsMonitor1.isMonitorEnabled shouldBe dnsMonitor1.enabled
                        openDnsMonitor1.status shouldBe IncidentStatus.ONGOING
                        openDnsMonitor1.startedAt shouldBe openDownDnsMonitor1.startedAt
                        openDnsMonitor1.endedAt shouldBe null
                        openDnsMonitor1.updatedAt shouldBe openDownDnsMonitor1.updatedAt
                        openDnsMonitor1.incidentType shouldBe IncidentType.DNS
                        openDnsMonitor1.details shouldBe null
                    }
                }
            }

            `when`("it is called with an explicit period") {

                then("it should ignore the incidents out of that period") {

                    val httpMonitor1 = createHttpMonitor(httpMonitorRepository)
                    val httpMonitor2 = createHttpMonitor(httpMonitorRepository)
                    val pushMonitor1 = createPushMonitor(pushMonitorRepository)
                    val pushMonitor2 = createPushMonitor(pushMonitorRepository)
                    val icmpMonitor1 = createIcmpMonitor(icmpMonitorRepository)
                    val icmpMonitor2 = createIcmpMonitor(icmpMonitorRepository)

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

                    val openDownIcmpMonitor1 = createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownIcmpMonitor2 = createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    val resolvedDownIcmpMonitor2 = createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(1),
                        endedAt = getCurrentTimestamp().minusHours(4),
                    )

                    val tcpMonitor1 = createTcpMonitor(tcpMonitorRepository)
                    val tcpMonitor2 = createTcpMonitor(tcpMonitorRepository)
                    val openDownTcpMonitor1 = createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownTcpMonitor2 = createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    val resolvedDownTcpMonitor2 = createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(1),
                        endedAt = getCurrentTimestamp().minusHours(4),
                    )

                    val dnsMonitor1 = createDnsMonitor(dnsMonitorRepository)
                    val dnsMonitor2 = createDnsMonitor(dnsMonitorRepository)
                    val openDownDnsMonitor1 = createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownDnsMonitor2 = createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    val resolvedDownDnsMonitor2 = createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(1),
                        endedAt = getCurrentTimestamp().minusHours(4),
                    )

                    val incidentsInTheLast2Days = incidentRepository.getIncidents(
                        period = Duration.ofDays(2),
                        includeResolved = true,
                    )
                    incidentsInTheLast2Days shouldHaveSize 17

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

                    incidentsInTheLast2Days.forOne { resolvedIcmpMonitor2 ->
                        resolvedIcmpMonitor2.monitorId shouldBe resolvedDownIcmpMonitor2.monitorId
                        resolvedIcmpMonitor2.incidentType shouldBe IncidentType.ICMP
                        resolvedIcmpMonitor2.startedAt shouldBe resolvedDownIcmpMonitor2.startedAt
                    }

                    incidentsInTheLast2Days.forOne { openIcmpMonitor2 ->
                        openIcmpMonitor2.monitorId shouldBe openDownIcmpMonitor2.monitorId
                        openIcmpMonitor2.incidentType shouldBe IncidentType.ICMP
                        openIcmpMonitor2.startedAt shouldBe openDownIcmpMonitor2.startedAt
                    }

                    incidentsInTheLast2Days.forOne { openIcmpMonitor1 ->
                        openIcmpMonitor1.monitorId shouldBe openDownIcmpMonitor1.monitorId
                        openIcmpMonitor1.incidentType shouldBe IncidentType.ICMP
                        openIcmpMonitor1.startedAt shouldBe openDownIcmpMonitor1.startedAt
                    }

                    incidentsInTheLast2Days.forOne { resolvedTcpMonitor2 ->
                        resolvedTcpMonitor2.monitorId shouldBe resolvedDownTcpMonitor2.monitorId
                        resolvedTcpMonitor2.incidentType shouldBe IncidentType.TCP
                        resolvedTcpMonitor2.startedAt shouldBe resolvedDownTcpMonitor2.startedAt
                    }

                    incidentsInTheLast2Days.forOne { openTcpMonitor2 ->
                        openTcpMonitor2.monitorId shouldBe openDownTcpMonitor2.monitorId
                        openTcpMonitor2.incidentType shouldBe IncidentType.TCP
                        openTcpMonitor2.startedAt shouldBe openDownTcpMonitor2.startedAt
                    }

                    incidentsInTheLast2Days.forOne { openTcpMonitor1 ->
                        openTcpMonitor1.monitorId shouldBe openDownTcpMonitor1.monitorId
                        openTcpMonitor1.incidentType shouldBe IncidentType.TCP
                        openTcpMonitor1.startedAt shouldBe openDownTcpMonitor1.startedAt
                    }

                    incidentsInTheLast2Days.forOne { resolvedDnsMonitor2 ->
                        resolvedDnsMonitor2.monitorId shouldBe resolvedDownDnsMonitor2.monitorId
                        resolvedDnsMonitor2.incidentType shouldBe IncidentType.DNS
                        resolvedDnsMonitor2.startedAt shouldBe resolvedDownDnsMonitor2.startedAt
                    }

                    incidentsInTheLast2Days.forOne { openDnsMonitor2 ->
                        openDnsMonitor2.monitorId shouldBe openDownDnsMonitor2.monitorId
                        openDnsMonitor2.incidentType shouldBe IncidentType.DNS
                        openDnsMonitor2.startedAt shouldBe openDownDnsMonitor2.startedAt
                    }

                    incidentsInTheLast2Days.forOne { openDnsMonitor1 ->
                        openDnsMonitor1.monitorId shouldBe openDownDnsMonitor1.monitorId
                        openDnsMonitor1.incidentType shouldBe IncidentType.DNS
                        openDnsMonitor1.startedAt shouldBe openDownDnsMonitor1.startedAt
                    }
                }
            }

            `when`("it is called with an explicit monitorId") {

                then("it should return the incident of that monitor only") {

                    val httpMonitor1 = createHttpMonitor(httpMonitorRepository)
                    val httpMonitor2 = createHttpMonitor(httpMonitorRepository, enabled = false)
                    val pushMonitor1 = createPushMonitor(pushMonitorRepository)
                    val pushMonitor2 = createPushMonitor(pushMonitorRepository, enabled = false)
                    val icmpMonitor1 = createIcmpMonitor(icmpMonitorRepository)
                    val icmpMonitor2 = createIcmpMonitor(icmpMonitorRepository, enabled = false)
                    val tcpMonitor1 = createTcpMonitor(tcpMonitorRepository)
                    val tcpMonitor2 = createTcpMonitor(tcpMonitorRepository, enabled = false)

                    val dnsMonitor1 = createDnsMonitor(dnsMonitorRepository)
                    val dnsMonitor2 = createDnsMonitor(dnsMonitorRepository, enabled = false)

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

                    val openDownIcmpMonitor1 = createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownIcmpMonitor2 = createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    createIcmpUptimeEventRecord(
                        dslContext,
                        monitorId = icmpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )

                    val openDownTcpMonitor1 = createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownTcpMonitor2 = createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    createTcpUptimeEventRecord(
                        dslContext,
                        monitorId = tcpMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )

                    val openDownDnsMonitor1 = createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                    )
                    createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor1.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(6),
                        endedAt = getCurrentTimestamp().minusDays(4),
                    )
                    val openDownDnsMonitor2 = createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor2.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusDays(3),
                        endedAt = null,
                        error = "sh#t happened"
                    )
                    createDnsUptimeEventRecord(
                        dslContext,
                        monitorId = dnsMonitor2.id,
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

                    val incidentsOfIcmpMonitor1 = incidentRepository.getIncidents(
                        includeResolved = false,
                        monitorId = icmpMonitor1.id,
                    )
                    incidentsOfIcmpMonitor1 shouldHaveSize 1

                    incidentsOfIcmpMonitor1.forOne { openMonitor1 ->
                        openMonitor1.monitorId shouldBe openDownIcmpMonitor1.monitorId
                        openMonitor1.status shouldBe IncidentStatus.ONGOING
                        openMonitor1.incidentType shouldBe IncidentType.ICMP
                    }

                    // Should return events for the disabled monitor as well
                    val incidentsOfIcmpMonitor2 = incidentRepository.getIncidents(
                        includeResolved = false,
                        monitorId = icmpMonitor2.id,
                    )

                    incidentsOfIcmpMonitor2 shouldHaveSize 1

                    incidentsOfIcmpMonitor2.forOne { openMonitor2 ->
                        openMonitor2.monitorId shouldBe openDownIcmpMonitor2.monitorId
                        openMonitor2.status shouldBe IncidentStatus.ONGOING
                        openMonitor2.incidentType shouldBe IncidentType.ICMP
                    }

                    val incidentsOfTcpMonitor1 = incidentRepository.getIncidents(
                        includeResolved = false,
                        monitorId = tcpMonitor1.id,
                    )
                    incidentsOfTcpMonitor1 shouldHaveSize 1

                    incidentsOfTcpMonitor1.forOne { openMonitor1 ->
                        openMonitor1.monitorId shouldBe openDownTcpMonitor1.monitorId
                        openMonitor1.status shouldBe IncidentStatus.ONGOING
                        openMonitor1.incidentType shouldBe IncidentType.TCP
                    }

                    // Should return events for the disabled monitor as well
                    val incidentsOfTcpMonitor2 = incidentRepository.getIncidents(
                        includeResolved = false,
                        monitorId = tcpMonitor2.id,
                    )

                    incidentsOfTcpMonitor2 shouldHaveSize 1

                    incidentsOfTcpMonitor2.forOne { openMonitor2 ->
                        openMonitor2.monitorId shouldBe openDownTcpMonitor2.monitorId
                        openMonitor2.status shouldBe IncidentStatus.ONGOING
                        openMonitor2.incidentType shouldBe IncidentType.TCP
                    }

                    val incidentsOfDnsMonitor1 = incidentRepository.getIncidents(
                        includeResolved = false,
                        monitorId = dnsMonitor1.id,
                    )
                    incidentsOfDnsMonitor1 shouldHaveSize 1

                    incidentsOfDnsMonitor1.forOne { openMonitor1 ->
                        openMonitor1.monitorId shouldBe openDownDnsMonitor1.monitorId
                        openMonitor1.status shouldBe IncidentStatus.ONGOING
                        openMonitor1.incidentType shouldBe IncidentType.DNS
                    }

                    // Should return events for the disabled monitor as well
                    val incidentsOfDnsMonitor2 = incidentRepository.getIncidents(
                        includeResolved = false,
                        monitorId = dnsMonitor2.id,
                    )

                    incidentsOfDnsMonitor2 shouldHaveSize 1

                    incidentsOfDnsMonitor2.forOne { openMonitor2 ->
                        openMonitor2.monitorId shouldBe openDownDnsMonitor2.monitorId
                        openMonitor2.status shouldBe IncidentStatus.ONGOING
                        openMonitor2.incidentType shouldBe IncidentType.DNS
                    }
                }
            }
        }
    }
}
