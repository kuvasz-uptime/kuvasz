package com.kuvaszuptime.kuvasz.uitest.pages.docker

import com.kuvaszuptime.kuvasz.uitest.pages.common.tooltipText
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The Docker monitor list page at `/docker-monitors`; the table is swapped into `#docker-monitors-list` via HTMX.
class DockerMonitorListPage(private val page: Page) {

    val newMonitorButton: Locator get() = page.getByTestId("add-new-button")

    val rows: Locator get() = page.getByTestId("docker-monitor-row")

    val names: List<String> get() = rows.locator("td:first-of-type").allInnerTexts().map { it.trim() }

    val emptyState: Locator get() = page.getByTestId("empty-state")

    fun navigate() {
        page.navigate("/docker-monitors")
    }

    fun rowByName(name: String): Locator = rows.filter(Locator.FilterOptions().setHasText(name))

    // The grayed-out uptime badge (with a tool icon) shown in a row while its monitor is under maintenance.
    fun maintenanceBadge(name: String): Locator =
        rowByName(name).locator(".status.status-gray:has(.icon-tabler-tool)")

    // The label-less badge next to the name, flagging a monitor whose Docker host is not in the config anymore.
    fun danglingHostBadge(name: String): Locator = rowByName(name).getByTestId("docker-host-not-configured-badge")

    fun danglingHostBadgeTooltip(name: String): String? = danglingHostBadge(name).tooltipText()

    fun openCreateModal(): DockerMonitorFormModal {
        newMonitorButton.click()
        return DockerMonitorFormModal(page)
    }

    // Clones the given monitor, returning the pre-filled create modal.
    fun cloneMonitor(name: String): DockerMonitorFormModal {
        rowByName(name).getByTestId("docker-monitor-clone-button").click()
        return DockerMonitorFormModal(page)
    }

    // Opens the given monitor's configuration from its row, returning the pre-filled update modal.
    fun configureMonitor(name: String): DockerMonitorFormModal {
        configureButtonIn(name).click()
        return DockerMonitorFormModal(page)
    }

    fun configureButtonIn(name: String): Locator = rowByName(name).getByTestId("docker-monitor-configure-button")

    // Only rendered when the monitors are read-only, in place of every other action of the row.
    fun configurationButtonIn(name: String): Locator =
        rowByName(name).getByTestId("docker-monitor-configuration-button")

    fun toggleMonitor(name: String) {
        rowByName(name).getByTestId("docker-monitor-toggle-button").click()
    }

    fun deleteMonitor(name: String) {
        rowByName(name).getByTestId("docker-monitor-delete-button").click()
        page.locator(".modal.show").getByTestId("delete-confirm-button").click()
    }
}
