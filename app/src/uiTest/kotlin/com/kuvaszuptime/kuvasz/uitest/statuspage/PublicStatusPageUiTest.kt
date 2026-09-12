package com.kuvaszuptime.kuvasz.uitest.statuspage

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.statuspage.PublicStatusPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import java.time.OffsetDateTime
import java.util.regex.Pattern

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class PublicStatusPageUiTest(private val httpMonitorRepository: HttpMonitorRepository) : UiTestSpec() {
    init {
        "a public status page is reachable without authentication and shows its monitor" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Public API")
            val pageTitle = "Public Systems Status"
            val slug = "public-systems"
            createStatusPage(
                dslContext,
                title = pageTitle,
                slug = slug,
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            assertThat(statusPage.title(pageTitle).first()).isVisible()
            assertThat(statusPage.monitorCard(monitor.name)).isVisible()
            // Without any categorized monitor the page keeps its plain list, with no cards or sections
            assertThat(statusPage.categoryCards).hasCount(0)
            assertThat(statusPage.categorySections).hasCount(0)
        }

        "a monitor that is currently down is rendered with a DOWN status" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Down Service")
            // An ongoing (endedAt = null) DOWN event makes the monitor's current status DOWN.
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            createStatusPage(
                dslContext,
                title = "Down Status",
                slug = "down-status",
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate("down-status")

            assertThat(statusPage.monitorCardBody("Down Service")).containsText(UptimeStatus.DOWN.literal)
        }

        "a monitor under an active maintenance window is shown with the banner and a grayed-out badge" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Maintained API")
            // An ongoing UP event so the monitor has a concrete status whose label must be kept on the badge.
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.UP,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            val slug = "maintenance-status"
            createStatusPage(
                dslContext,
                title = "Maintenance Status",
                slug = slug,
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )
            // A manual (unscheduled) enabled window is permanently active for its monitors.
            createMaintenanceWindow(
                dslContext,
                name = "Database upgrade",
                description = "Rolling out a new database version",
                enabled = true,
                showOnStatusPages = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            // The active maintenance banner lists the window with its name and description
            assertThat(statusPage.maintenanceBanner()).isVisible()
            assertThat(statusPage.maintenanceBanner()).containsText("Database upgrade")
            assertThat(statusPage.maintenanceBanner()).containsText("Rolling out a new database version")
            // The monitor's badge is grayed out but keeps its UP label
            assertThat(statusPage.monitorMaintenanceBadge(monitor.name)).isVisible()
            assertThat(statusPage.monitorMaintenanceBadge(monitor.name)).containsText(UptimeStatus.UP.literal)
            // A manual window has no scheduled timeframe, so no timeframe pill is rendered
            assertThat(statusPage.maintenanceTimeframe()).hasCount(0)
        }

        "a status page selects its monitors by category, on top of the ones listed explicitly" {
            val payments = createHttpMonitor(httpMonitorRepository, monitorName = "Payments API", category = "Payments")
            val billing = createHttpMonitor(httpMonitorRepository, monitorName = "Billing API", category = "Payments")
            createHttpMonitor(httpMonitorRepository, monitorName = "Search API", category = "Search")
            val pinned = createHttpMonitor(httpMonitorRepository, monitorName = "Landing page")

            val slug = "category-selected-status"
            createStatusPage(
                dslContext,
                title = "Category Selected",
                slug = slug,
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, pinned.name)),
                categories = listOf("Payments"),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            // The whole Payments category is shown, next to the explicitly pinned, uncategorized monitor
            statusPage.monitorNames shouldContainExactlyInAnyOrder
                listOf(payments.name, billing.name, pinned.name)
        }

        "a category that no monitor belongs to leaves the status page empty instead of breaking it" {
            createHttpMonitor(httpMonitorRepository, monitorName = "Search API", category = "Search")

            val slug = "orphan-category-status"
            createStatusPage(
                dslContext,
                title = "Orphan Category",
                slug = slug,
                public = true,
                categories = listOf("Nobody uses me"),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            // The page renders, the unmatched category simply contributes nothing
            assertThat(statusPage.title("Orphan Category")).isVisible()
            assertThat(statusPage.monitorCards).hasCount(0)
        }

        "a maintenance window scoped to a category grays out every monitor of that category" {
            val payments = createHttpMonitor(httpMonitorRepository, monitorName = "Payments API", category = "Payments")
            val search = createHttpMonitor(httpMonitorRepository, monitorName = "Search API", category = "Search")
            listOf(payments, search).forEach { monitor ->
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = OffsetDateTime.now(),
                    endedAt = null,
                )
            }

            val slug = "category-maintenance-status"
            createStatusPage(
                dslContext,
                title = "Category Maintenance",
                slug = slug,
                public = true,
                monitors = listOf(
                    MonitorID(MonitorType.HTTP_SSL, payments.name),
                    MonitorID(MonitorType.HTTP_SSL, search.name),
                ),
            )
            // A manual (unscheduled) enabled window is permanently active for the monitors it covers
            createMaintenanceWindow(
                dslContext,
                name = "Payments upgrade",
                enabled = true,
                showOnStatusPages = true,
                categories = listOf("Payments"),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            assertThat(statusPage.maintenanceBanner()).containsText("Payments upgrade")
            // Only the monitor of the covered category is grayed out
            assertThat(statusPage.monitorMaintenanceBadge(payments.name)).isVisible()
            assertThat(statusPage.monitorMaintenanceBadge(search.name)).hasCount(0)
        }

        "an active scheduled maintenance window renders its timeframe as a start-end pill" {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "Scheduled API")
            val slug = "scheduled-maintenance-status"
            createStatusPage(
                dslContext,
                title = "Scheduled Maintenance Status",
                slug = slug,
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )
            // A single (one-shot) window that started an hour ago and lasts two hours is active right now, so it has a
            // concrete [start, end) interval that must be rendered as a timeframe pill.
            val windowStart = OffsetDateTime.now().minusHours(1)
            createMaintenanceWindow(
                dslContext,
                name = "Scheduled DB maintenance",
                enabled = true,
                showOnStatusPages = true,
                start = windowStart,
                duration = "PT2H",
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            assertThat(statusPage.maintenanceBanner()).containsText("Scheduled DB maintenance")
            // The timeframe pill is rendered with the window's start and end timestamps
            assertThat(statusPage.maintenanceTimeframe()).isVisible()
            assertThat(statusPage.maintenanceTimeframe()).containsText(windowStart.year.toString())
        }

        "monitor cards with the same status are sorted by name, regardless of its casing" {
            val monitors = listOf("Charlie", "bravo", "Delta", "alpha").map { name ->
                createHttpMonitor(httpMonitorRepository, monitorName = name)
            }
            val slug = "sorted-status"
            createStatusPage(
                dslContext,
                title = "Sorted Status",
                slug = slug,
                public = true,
                monitors = monitors.map { MonitorID(MonitorType.HTTP_SSL, it.name) },
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            assertThat(statusPage.monitorCards).hasCount(monitors.size)
            statusPage.monitorNames shouldBe listOf("alpha", "bravo", "Charlie", "Delta")
        }

        "categorized monitors are grouped into sections, reachable from the category cards" {
            val backend = createHttpMonitor(
                httpMonitorRepository,
                monitorName = "Backend API",
                category = "Backend services",
            )
            val web = createHttpMonitor(httpMonitorRepository, monitorName = "Website", category = "Web")
            val other = createHttpMonitor(httpMonitorRepository, monitorName = "Some other service")
            // An ongoing DOWN event turns the "Web" category into an outage state
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = web.id,
                status = UptimeStatus.DOWN,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = backend.id,
                status = UptimeStatus.UP,
                startedAt = OffsetDateTime.now(),
                endedAt = null,
            )
            val slug = "categorized-status"
            createStatusPage(
                dslContext,
                title = "Categorized Status",
                slug = slug,
                public = true,
                monitors = listOf(backend, web, other).map { MonitorID(MonitorType.HTTP_SSL, it.name) },
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            // One card and one section per category, the uncategorized monitors landing in "Other" at the end
            val expectedLabels = listOf("Backend services", "Web", "Other")
            assertThat(statusPage.categoryCards).hasCount(expectedLabels.size)
            assertThat(statusPage.categorySections).hasCount(expectedLabels.size)
            statusPage.categoryLabels shouldBe expectedLabels

            // Each card carries the aggregated status of its own category
            assertThat(statusPage.categoryCard("Backend services")).containsText("Operational")
            assertThat(statusPage.categoryCard("Web")).containsText("Major outage")

            // Every monitor is rendered, each one inside the section of its category
            assertThat(statusPage.monitorCards).hasCount(listOf(backend, web, other).size)
            assertThat(statusPage.categorySection("Backend services").getByText("Backend API")).isVisible()
            assertThat(statusPage.categorySection("Web").getByText("Website")).isVisible()
            assertThat(statusPage.categorySection("Other").getByText("Some other service")).isVisible()

            // A card links to the anchor of its own section
            statusPage.categoryCard("Web").click()
            page.waitForURL("**#*")
            val webSectionId = statusPage.categorySection("Web").getAttribute("id")
            page.url().substringAfter("#") shouldBe webSectionId
        }

        "a category card is tinted by its status and breaks the monitors down into a distribution bar" {
            fun payingMonitor(name: String) =
                createHttpMonitor(httpMonitorRepository, monitorName = name, category = "Payments")

            val up = payingMonitor("Payments UP")
            val down = payingMonitor("Payments DOWN")
            // Down *and* under maintenance: the maintenance has to win, like on the card of the monitor itself
            val maintained = payingMonitor("Payments maintained")
            // Without an uptime event the monitor is still pending
            val pending = payingMonitor("Payments pending")
            val healthy = createHttpMonitor(httpMonitorRepository, monitorName = "Docs", category = "Docs")

            listOf(up to UptimeStatus.UP, down to UptimeStatus.DOWN, maintained to UptimeStatus.DOWN)
                .plus(healthy to UptimeStatus.UP)
                .forEach { (monitor, status) ->
                    createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = monitor.id,
                        status = status,
                        startedAt = OffsetDateTime.now(),
                        endedAt = null,
                    )
                }
            createMaintenanceWindow(
                dslContext,
                name = "Payment gateway upgrade",
                description = "Swapping the payment gateway",
                enabled = true,
                showOnStatusPages = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, maintained.name)),
            )

            val slug = "distribution-status"
            createStatusPage(
                dslContext,
                title = "Distribution Status",
                slug = slug,
                public = true,
                monitors = listOf(up, down, maintained, pending, healthy)
                    .map { MonitorID(MonitorType.HTTP_SSL, it.name) },
            )

            val page = newPage(authenticated = false)
            val statusPage = PublicStatusPage(page)
            statusPage.navigate(slug)

            // One UP and one DOWN monitor make the whole category a partial outage, which tints the card yellow
            assertThat(statusPage.categoryCard("Payments")).containsText("Partial outage")
            assertThat(statusPage.categoryCard("Payments")).hasClass(Pattern.compile(".*card-gradient-yellow.*"))
            assertThat(statusPage.categoryCard("Docs")).hasClass(Pattern.compile(".*card-gradient-green.*"))

            // Four monitors of four different statuses, each one taking a quarter of the bar
            // The widths come back normalized by the browser, `25.00%` as it is rendered turns into `25%` here
            statusPage.categoryDistributionSegments("Payments") shouldBe listOf(
                "bg-success" to "25%",
                "bg-secondary" to "25%",
                "bg-warning" to "25%",
                "bg-danger" to "25%",
            )
            statusPage.categoryDistributionTooltip("Payments") shouldBe
                "1 operational · 1 under maintenance · 1 pending · 1 down"

            // A category without any issue is a single, full width segment
            statusPage.categoryDistributionSegments("Docs") shouldBe listOf("bg-success" to "100%")
            statusPage.categoryDistributionTooltip("Docs") shouldBe "1 operational"

            // The segments carry the very colors of the status stripes of the monitor cards below them
            assertThat(statusPage.monitorMaintenanceBadge(maintained.name)).isVisible()
        }
    }
}
