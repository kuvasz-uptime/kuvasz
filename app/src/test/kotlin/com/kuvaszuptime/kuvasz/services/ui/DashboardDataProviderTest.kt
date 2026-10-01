package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSorted
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.verify
import io.mockk.spyk
import org.jooq.DSLContext
import java.time.Duration

@MicronautTest(startApplication = false)
class DashboardDataProviderTest(
    private val dashboardDataProvider: DashboardDataProvider,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val incidentRepository: IncidentRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the getOverview() method") {

            `when`("there isn't anything to show") {

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(7))

                then("it should return an empty overview of the requested period") {
                    overview.period shouldBe Duration.ofDays(7)
                    overview.uptimeStats.actual.total shouldBe 0
                    overview.uptimeStats.history.period shouldBe Duration.ofDays(7).toString()
                    overview.recentIncidents.shouldBeEmpty()
                    overview.moreOngoingIncidents shouldBe 0
                    overview.maintenanceWindows.shouldBeEmpty()
                    overview.moreMaintenanceWindows shouldBe 0
                }
            }

            `when`("there are ongoing and resolved incidents") {

                val now = getCurrentTimestamp()
                val httpMonitor = createHttpMonitor(httpMonitorRepository, monitorName = "HTTP")
                val pushMonitor = createPushMonitor(pushMonitorRepository, monitorName = "Push")
                val pausedMonitor = createPushMonitor(pushMonitorRepository, monitorName = "Paused", enabled = false)
                // Resolved in the period: the one that started first was resolved the latest
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = httpMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(5),
                    endedAt = now.minusHours(1),
                )
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pushMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(3),
                    endedAt = now.minusHours(2),
                )
                // Started before the period, but resolved in it
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pushMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(30),
                    endedAt = now.minusHours(20),
                )
                // Resolved before the period
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pushMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusDays(3),
                    endedAt = now.minusDays(2),
                )
                // Ongoing ones, the one that started earlier (even before the period) got updated the latest
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = httpMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusDays(2),
                    endedAt = null,
                    updatedAt = now,
                )
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pushMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusMinutes(30),
                    endedAt = null,
                    updatedAt = now.minusMinutes(10),
                )
                // Neither the ongoing, nor the resolved incidents of the paused monitors are listed
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(4),
                    endedAt = now.minusHours(3),
                )
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(1),
                    endedAt = null,
                )

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(1))

                then("it should list the ongoing ones first, the latest started first") {
                    overview.recentIncidents.take(2).map { it.monitorName to it.status } shouldBe listOf(
                        "Push" to IncidentStatus.ONGOING,
                        "HTTP" to IncidentStatus.ONGOING,
                    )
                }

                then("it should top them up with the ones resolved in the period, the latest resolved first") {
                    overview.recentIncidents.drop(2).map { it.monitorName to it.status } shouldBe listOf(
                        "HTTP" to IncidentStatus.RESOLVED,
                        "Push" to IncidentStatus.RESOLVED,
                        "Push" to IncidentStatus.RESOLVED,
                    )
                    overview.recentIncidents.drop(2).mapNotNull { it.endedAt }.reversed().shouldBeSorted()
                }
            }

            `when`("there are exactly as many ongoing incidents as the limit") {

                then("it should list only them, without even looking for the resolved ones") {
                    val now = getCurrentTimestamp()
                    repeat(DashboardDataProvider.RECENT_INCIDENTS_LIMIT) { index ->
                        val monitor = createHttpMonitor(httpMonitorRepository)
                        createHttpUptimeEventRecord(
                            dslContext = dslContext,
                            monitorId = monitor.id,
                            status = UptimeStatus.DOWN,
                            startedAt = now.minusMinutes(index + 1L),
                            endedAt = null,
                        )
                        createHttpUptimeEventRecord(
                            dslContext = dslContext,
                            monitorId = monitor.id,
                            status = UptimeStatus.DOWN,
                            startedAt = now.minusHours(3),
                            endedAt = now.minusHours(2),
                        )
                    }

                    val overview = dashboardDataProvider.getOverview(Duration.ofDays(1))

                    overview.recentIncidents shouldHaveSize DashboardDataProvider.RECENT_INCIDENTS_LIMIT
                    overview.recentIncidents.forAll { it.status shouldBe IncidentStatus.ONGOING }
                    overview.moreOngoingIncidents shouldBe 0
                    verify(exactly = 0) { getMock(incidentRepository).getLatestResolvedIncidents(any(), any(), any()) }
                }
            }

            `when`("there are more incidents than the limit") {

                val now = getCurrentTimestamp()
                val monitor = createHttpMonitor(httpMonitorRepository)
                val downMonitor = createHttpMonitor(httpMonitorRepository)
                repeat(DashboardDataProvider.RECENT_INCIDENTS_LIMIT + 2) { index ->
                    createHttpUptimeEventRecord(
                        dslContext = dslContext,
                        monitorId = monitor.id,
                        status = UptimeStatus.DOWN,
                        startedAt = now.minusHours(index + 1L),
                        endedAt = now.minusHours(index + 1L).plusMinutes(1),
                    )
                }
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusDays(2),
                    endedAt = null,
                    updatedAt = now,
                )

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(1))

                then("it should top up the ongoing ones with the latest resolved ones") {
                    overview.recentIncidents shouldHaveSize DashboardDataProvider.RECENT_INCIDENTS_LIMIT
                    overview.recentIncidents.first().status shouldBe IncidentStatus.ONGOING
                    overview.recentIncidents.drop(1).let { resolvedIncidents ->
                        resolvedIncidents.forAll { it.status shouldBe IncidentStatus.RESOLVED }
                        resolvedIncidents.mapNotNull { it.endedAt }.reversed().shouldBeSorted()
                    }
                }
            }

            `when`("there are more ongoing incidents than the limit") {

                val now = getCurrentTimestamp()
                val downMonitors = (1..DashboardDataProvider.RECENT_INCIDENTS_LIMIT + 2).map { index ->
                    createHttpMonitor(httpMonitorRepository, monitorName = "Down $index").also { monitor ->
                        createHttpUptimeEventRecord(
                            dslContext = dslContext,
                            monitorId = monitor.id,
                            status = UptimeStatus.DOWN,
                            startedAt = now.minusMinutes(index.toLong()),
                            endedAt = null,
                            updatedAt = now,
                        )
                    }
                }
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = downMonitors.first().id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(2),
                    endedAt = now.minusHours(1),
                )

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(1))

                then("it should list the latest ones only, count the rest of them, and list no resolved one") {
                    overview.recentIncidents.map { it.monitorName } shouldBe
                        downMonitors.take(DashboardDataProvider.RECENT_INCIDENTS_LIMIT).map { it.name }
                    overview.recentIncidents.forAll { it.status shouldBe IncidentStatus.ONGOING }
                    overview.moreOngoingIncidents shouldBe 2
                }
            }

            `when`("more monitors are down under maintenance than the limit, besides an older outage outside of it") {

                val now = getCurrentTimestamp()
                val outsideMonitor = createHttpMonitor(httpMonitorRepository, monitorName = "Outside")
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = outsideMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(3),
                    endedAt = null,
                    updatedAt = now,
                )
                val maintainedMonitors = (1..DashboardDataProvider.RECENT_INCIDENTS_LIMIT).map { index ->
                    createHttpMonitor(httpMonitorRepository, monitorName = "Maintained $index").also { monitor ->
                        createHttpUptimeEventRecord(
                            dslContext = dslContext,
                            monitorId = monitor.id,
                            status = UptimeStatus.DOWN,
                            startedAt = now.minusMinutes(index.toLong()),
                            endedAt = null,
                            updatedAt = now,
                        )
                    }
                }
                createMaintenanceWindow(
                    dslContext = dslContext,
                    monitors = maintainedMonitors.map { MonitorID(MonitorType.HTTP_SSL, it.name) },
                )

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(1))

                then("it should list the one outside the maintenance first, and count the maintained ones left out") {
                    overview.recentIncidents.map { it.monitorName } shouldBe listOf(outsideMonitor.name) +
                        maintainedMonitors.take(DashboardDataProvider.RECENT_INCIDENTS_LIMIT - 1).map { it.name }
                    overview.moreOngoingIncidents shouldBe 1
                }
            }

            `when`("there are SSL incidents too") {

                val now = getCurrentTimestamp()
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "HTTP")
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(2),
                    endedAt = now.minusHours(1),
                )
                // An ongoing and a resolved one
                val sslEvents = listOf(now.minusHours(3) to null, now.minusHours(5) to now.minusHours(4))
                sslEvents.forEach { (startedAt, endedAt) ->
                    createSSLEventRecord(
                        dslContext = dslContext,
                        monitorId = monitor.id,
                        status = SslStatus.INVALID,
                        startedAt = startedAt,
                        endedAt = endedAt,
                    )
                }

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(1))

                then("it should only list the uptime incidents, just like the figures next to them count them") {
                    overview.recentIncidents.map { it.incidentType } shouldBe listOf(IncidentType.HTTP)
                }
            }

            `when`("an incident is resolved between fetching the ongoing and the resolved ones") {

                then("it should only be listed as a resolved one") {
                    val now = getCurrentTimestamp()
                    val monitor = createHttpMonitor(httpMonitorRepository)
                    createHttpUptimeEventRecord(
                        dslContext = dslContext,
                        monitorId = monitor.id,
                        status = UptimeStatus.DOWN,
                        startedAt = now.minusHours(2),
                        endedAt = now.minusMinutes(1),
                    )
                    val resolvedIncident = incidentRepository.getIncidents(includeResolved = true).single()
                    // The state it had when the ongoing ones were fetched
                    every {
                        getMock(incidentRepository).getIncidents(includeResolved = false, includeSslIncidents = false)
                    } returns listOf(resolvedIncident.copy(status = IncidentStatus.ONGOING, endedAt = null))

                    val overview = dashboardDataProvider.getOverview(Duration.ofDays(1))

                    overview.recentIncidents shouldBe listOf(resolvedIncident)
                }
            }

            `when`("there are maintenance windows") {

                val now = getCurrentTimestamp()
                createMaintenanceWindow(dslContext, name = "Far away", start = now.plusDays(30), duration = "PT1H")
                createMaintenanceWindow(
                    dslContext,
                    name = "Right after the lookahead",
                    start = now.plus(DashboardDataProvider.MAINTENANCE_LOOKAHEAD).plusHours(1),
                    duration = "PT1H",
                )
                createMaintenanceWindow(
                    dslContext,
                    name = "Right before the end of the lookahead",
                    start = now.plus(DashboardDataProvider.MAINTENANCE_LOOKAHEAD).minusHours(1),
                    duration = "PT1H",
                )
                createMaintenanceWindow(dslContext, name = "Upcoming later", start = now.plusDays(3), duration = "PT1H")
                createMaintenanceWindow(dslContext, name = "Upcoming soon", start = now.plusDays(1), duration = "PT1H")
                createMaintenanceWindow(dslContext, name = "Active", start = now.minusHours(1), duration = "PT2H")
                createMaintenanceWindow(dslContext, name = "Over", start = now.minusDays(1), duration = "PT1H")
                createMaintenanceWindow(
                    dslContext,
                    name = "Disabled",
                    enabled = false,
                    start = now.minusHours(1),
                    duration = "PT2H",
                )
                createMaintenanceWindow(
                    dslContext,
                    name = "Disabled upcoming",
                    enabled = false,
                    start = now.plusHours(2),
                    duration = "PT1H",
                )

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(7))

                then("it should return the enabled ones that are active or start soon, the active ones first") {
                    overview.maintenanceWindows.map { it.name } shouldBe listOf(
                        "Active",
                        "Upcoming soon",
                        "Upcoming later",
                        "Right before the end of the lookahead",
                    )
                    overview.moreMaintenanceWindows shouldBe 0
                }
            }

            `when`("there is a recurring maintenance window") {

                createMaintenanceWindow(dslContext, name = "Daily", cron = "0 2 * * *", duration = "PT1H")

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(7))

                then("it should be listed, as it's either active or starts within a day") {
                    overview.maintenanceWindows.map { it.name } shouldBe listOf("Daily")
                }
            }

            `when`("there are more active and upcoming maintenance windows than the limit") {

                val now = getCurrentTimestamp()
                repeat(DashboardDataProvider.MAINTENANCE_WINDOWS_LIMIT) { index ->
                    createMaintenanceWindow(
                        dslContext,
                        name = "Upcoming $index",
                        start = now.plusHours(index + 1L),
                        duration = "PT1H",
                    )
                }
                createMaintenanceWindow(dslContext, name = "Active", start = now.minusHours(1), duration = "PT2H")

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(7))

                then("it should keep the active ones, and only count the upcoming ones starting the latest") {
                    overview.maintenanceWindows.map { it.name } shouldBe
                        listOf("Active") + (0 until DashboardDataProvider.MAINTENANCE_WINDOWS_LIMIT - 1).map {
                            "Upcoming $it"
                        }
                    overview.moreMaintenanceWindows shouldBe 1
                }
            }

            `when`("there are more upcoming maintenance windows than the limit") {

                val now = getCurrentTimestamp()
                repeat(DashboardDataProvider.MAINTENANCE_WINDOWS_LIMIT + 1) { index ->
                    createMaintenanceWindow(dslContext, start = now.plusHours(index + 1L), duration = "PT1H")
                }

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(7))

                then("it should only return the ones starting the soonest, and count the rest of them") {
                    overview.maintenanceWindows shouldHaveSize DashboardDataProvider.MAINTENANCE_WINDOWS_LIMIT
                    overview.maintenanceWindows.mapNotNull { it.nextStart }.shouldBeSorted()
                    overview.moreMaintenanceWindows shouldBe 1
                }
            }
        }
    }

    // A spy, so every test works with the real incidents, but the result of a query can be stubbed if it's necessary
    @MockBean(IncidentRepository::class)
    fun incidentRepositorySpy(dslContext: DSLContext): IncidentRepository = spyk(IncidentRepository(dslContext))
}
