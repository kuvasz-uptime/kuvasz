package com.kuvaszuptime.kuvasz.uitest.pages

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// Terser wrappers over Playwright's verbose `getByRole(role, …Options().setName(name))`.
internal fun Page.byRole(role: AriaRole, name: String): Locator =
    getByRole(role, Page.GetByRoleOptions().setName(name))

internal fun Locator.byRole(role: AriaRole, name: String): Locator =
    getByRole(role, Locator.GetByRoleOptions().setName(name))
