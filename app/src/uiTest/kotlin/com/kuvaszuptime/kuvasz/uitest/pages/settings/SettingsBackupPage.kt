package com.kuvaszuptime.kuvasz.uitest.pages.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.pages.common.byRole
import com.microsoft.playwright.Download
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// The Settings page (`/settings`) and its "Backup & Restore" dropdown.
class SettingsBackupPage(private val page: Page) {

    private val backupMenuToggle: Locator get() = page.byRole(AriaRole.LINK, Messages.backupAndRestore())
    val exportMonitorsItem: Locator get() = page.getByTestId("export-monitors-item")
    val importMonitorsItem: Locator get() = page.getByTestId("import-monitors-item")

    fun navigate() {
        page.navigate("/settings")
    }

    private fun openBackupMenu() {
        backupMenuToggle.click()
    }

    // Opens the dropdown and triggers the monitor export, returning the captured browser download.
    fun exportMonitors(): Download {
        openBackupMenu()
        return page.waitForDownload { exportMonitorsItem.click() }
    }

    fun openImportModal(): MonitorImportModalView {
        openBackupMenu()
        importMonitorsItem.click()
        return MonitorImportModalView(page).also { it.waitUntilOpen() }
    }
}
