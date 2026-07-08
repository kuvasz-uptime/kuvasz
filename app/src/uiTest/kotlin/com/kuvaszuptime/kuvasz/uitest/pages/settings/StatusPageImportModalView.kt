package com.kuvaszuptime.kuvasz.uitest.pages.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Page

// Page object for the "Import status pages (YAML)" modal opened from the Settings backup dropdown.
class StatusPageImportModalView(page: Page) : ImportModalView(
    page,
    testIdPrefix = "status-page-import",
    fileInputId = "status-page-import-file-input",
    warningText = Messages.statusPageImportWarning(),
)
