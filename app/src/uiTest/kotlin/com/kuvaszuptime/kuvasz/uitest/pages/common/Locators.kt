package com.kuvaszuptime.kuvasz.uitest.pages.common

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

internal fun Page.byRole(role: AriaRole, name: String): Locator =
    getByRole(role, Page.GetByRoleOptions().setName(name))

internal fun Locator.byRole(role: AriaRole, name: String): Locator =
    getByRole(role, Locator.GetByRoleOptions().setName(name))

/**
 * The tooltip of the element. Bootstrap moves the rendered `title` into `data-bs-original-title` when it takes the
 * element over, so both are read in one go: read one by one, the move could happen in between, missing both.
 */
internal fun Locator.tooltipText(): String? =
    evaluate("element => element.getAttribute('data-bs-original-title') ?? element.getAttribute('title')") as String?
