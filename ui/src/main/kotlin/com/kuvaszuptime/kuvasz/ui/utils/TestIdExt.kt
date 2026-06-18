@file:Suppress("MatchingDeclarationName")

package com.kuvaszuptime.kuvasz.ui.utils

import kotlinx.html.HTMLTag

/**
 * Adds a stable `data-testid` hook for the browser-driven (Playwright) E2E suite. Used sparingly, only where an
 * element can't be reached reliably via an accessible/semantic locator (role, label, text) — e.g. action buttons
 * inside monitor table rows that otherwise differ only by icon.
 */
internal fun HTMLTag.testId(value: String) {
    attributes["data-testid"] = value
}
