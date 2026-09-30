package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSorted
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.Duration

@MicronautTest(startApplication = false)
class DashboardDataProviderTest(
    private val dashboardDataProvider: DashboardDataProvider,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the getOverview() method") {

            `when`("there isn't anything to show") {

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(7))

                then("it should return an empty overview of the requested period") {
                    overview.period shouldBe Duration.ofDays(7)
                    overview.uptimeStats.actual.total shouldBe 0
                    overview.recentIncidents.shouldBeEmpty()
                    overview.maintenanceWindows.shouldBeEmpty()
                    overview.maintenanceLookahead shouldBe DashboardDataProvider.MAINTENANCE_LOOKAHEAD
                }
            }

            `when`("there are ongoing and resolved incidents") {

                val now = getCurrentTimestamp()
                val httpMonitor = createHttpMonitor(httpMonitorRepository, monitorName = "HTTP")
                val pushMonitor = createPushMonitor(pushMonitorRepository, monitorName = "Push")
                val pausedMonitor = createPushMonitor(pushMonitorRepository, monitorName = "Paused", enabled = false)
                // Resolved in the period: 10 and 30 minutes long
                createHttpUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = httpMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(5),
                    endedAt = now.minusHours(5).plusMinutes(10),
                )
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pushMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(3),
                    endedAt = now.minusHours(3).plusMinutes(30),
                )
                // Resolved before the period
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pushMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusDays(3),
                    endedAt = now.minusDays(2),
                )
                // Ongoing ones, started before the period too
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
                    startedAt = now.minusHours(1),
                    endedAt = null,
                    updatedAt = now,
                )
                // The incidents of the paused monitors are left out
                createPushUptimeEventRecord(
                    dslContext = dslContext,
                    monitorId = pausedMonitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(1),
                    endedAt = null,
                )

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(1))

                then("it should return the ongoing incidents first, then the ones resolved in the period") {
                    overview.recentIncidents.map { it.monitorName to it.status } shouldBe listOf(
                        "Push" to IncidentStatus.ONGOING,
                        "HTTP" to IncidentStatus.ONGOING,
                        "Push" to IncidentStatus.RESOLVED,
                        "HTTP" to IncidentStatus.RESOLVED,
                    )
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

            `when`("there are maintenance windows") {

                val now = getCurrentTimestamp()
                createMaintenanceWindow(dslContext, name = "Far away", start = now.plusDays(30), duration = "PT1H")
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

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(7))

                then("it should return the enabled ones that are active or start soon, the active ones first") {
                    overview.maintenanceWindows.map { it.name } shouldBe
                        listOf("Active", "Upcoming soon", "Upcoming later")
                }
            }

            `when`("there are more upcoming maintenance windows than the limit") {

                val now = getCurrentTimestamp()
                repeat(DashboardDataProvider.MAINTENANCE_WINDOWS_LIMIT + 1) { index ->
                    createMaintenanceWindow(dslContext, start = now.plusHours(index + 1L), duration = "PT1H")
                }

                val overview = dashboardDataProvider.getOverview(Duration.ofDays(7))

                then("it should only return the ones starting the soonest") {
                    overview.maintenanceWindows shouldHaveSize DashboardDataProvider.MAINTENANCE_WINDOWS_LIMIT
                    overview.maintenanceWindows.mapNotNull { it.nextStart }.shouldBeSorted()
                }
            }
        }
    }
}
