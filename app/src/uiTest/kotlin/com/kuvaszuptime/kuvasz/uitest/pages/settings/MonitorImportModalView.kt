package com.kuvaszuptime.kuvasz.uitest.pages.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Page

// Page object for the "Import monitors (YAML)" modal opened from the Settings backup dropdown.
class MonitorImportModalView(page: Page) : ImportModalView(
    page,
    testIdPrefix = "monitor-import",
    fileInputId = "monitor-import-file-input",
    warningText = Messages.monitorImportWarning(),
)
