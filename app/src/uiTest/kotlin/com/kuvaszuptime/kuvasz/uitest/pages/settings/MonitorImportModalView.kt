package com.kuvaszuptime.kuvasz.uitest.pages.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// Page object for the "Import monitors (YAML)" modal opened from the Settings backup dropdown.
class MonitorImportModalView(page: Page) : ImportModalView(
    page,
    testIdPrefix = "monitor-import",
    fileInputId = "monitor-import-file-input",
    warningText = Messages.monitorImportWarning(),
) {
    fun importedBadges(type: MonitorType): Locator = modal.getByTestId("monitor-import-result-imported-${type.name}")

    fun ignoredIntegrationBadges(type: MonitorType): Locator =
        modal.getByTestId("monitor-import-result-ignored-integrations-${type.name}")
}
