package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// The authenticated dashboard at `/`. Its overview is filled in via HTMX after load, and it swaps the verdict into the
// page header out-of-band.
class DashboardPage(private val page: Page) {

    val heading: Locator get() = page.getByTestId("dashboard-title")

    // Only rendered once the overview has been loaded, and only if there is at least one monitor
    val statusIndicator: Locator get() = page.getByTestId("dashboard-status-indicator")

    val statusDetails: Locator get() = page.getByTestId("dashboard-status-details")

    // The placeholder shown as long as there isn't a single monitor of any type
    val emptyState: Locator get() = page.getByTestId("empty-state")

    val periodSelector: Locator get() = page.getByTestId("dashboard-period-selector")

    val createMonitorButton: Locator get() = page.getByTestId("dashboard-create-monitor-button")

    val refreshButton: Locator get() = page.getByTestId("dashboard-refresh-button")

    val uptimeCard: Locator get() = page.getByTestId("dashboard-uptime")

    val incidentsCountCard: Locator get() = page.getByTestId("dashboard-incidents-count")

    val monitorTypesCard: Locator get() = page.getByTestId("dashboard-monitor-types")

    val recentIncidentsCard: Locator get() = page.getByTestId("dashboard-recent-incidents")

    val incidents: Locator get() = recentIncidentsCard.getByTestId("dashboard-incident")

    val viewAllIncidentsLink: Locator get() = recentIncidentsCard.byRole(AriaRole.LINK, "View all incidents")

    val certificatesCard: Locator get() = page.getByTestId("dashboard-certificates")

    val certificates: Locator get() = certificatesCard.getByTestId("dashboard-certificate")

    val maintenanceCard: Locator get() = page.getByTestId("dashboard-maintenance")

    val maintenanceWindows: Locator get() = maintenanceCard.getByTestId("dashboard-maintenance-window")

    val manageMaintenanceWindowsLink: Locator
        get() = maintenanceCard.byRole(AriaRole.LINK, "Manage maintenance windows")

    val leastReliableMonitorsCard: Locator get() = page.getByTestId("dashboard-least-reliable-monitors")

    val leastReliableMonitors: Locator
        get() = leastReliableMonitorsCard.getByTestId("dashboard-least-reliable-monitor")

    fun navigate() {
        page.navigate("/")
    }

    /** The row of a monitor type in the monitors card, e.g. `http`. Types without monitors don't get one. */
    fun monitorTypeRow(type: String): Locator = page.getByTestId("dashboard-monitor-type-$type")

    fun incident(monitorName: String): Locator = incidents.filter(Locator.FilterOptions().setHasText(monitorName))

    /** The dot of a row of the dashboard's lists, which tells the state of the item through its label. */
    fun statusDotOf(row: Locator): Locator = row.getByTestId("dashboard-status-dot")

    /** Picks a period by the ISO-8601 value of its option, which reloads the whole page. */
    fun selectPeriod(isoPeriod: String) {
        periodSelector.selectOption(isoPeriod)
    }

    fun refresh() {
        refreshButton.click()
    }
}
