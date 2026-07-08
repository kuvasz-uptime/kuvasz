package com.kuvaszuptime.kuvasz.uitest.pages.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Page

// Page object for the "Import maintenance windows (YAML)" modal opened from the Settings backup dropdown.
class MaintenanceWindowImportModalView(page: Page) : ImportModalView(
    page,
    testIdPrefix = "maintenance-window-import",
    fileInputId = "maintenance-window-import-file-input",
    warningText = Messages.maintenanceWindowImportWarning(),
)
