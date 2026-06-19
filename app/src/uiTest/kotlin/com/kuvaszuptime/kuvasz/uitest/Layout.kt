package com.kuvaszuptime.kuvasz.uitest

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

val Page.htmlRoot: Locator
    get() = locator("html")
val Page.darkThemeToggle: Locator
    get() = getByTestId("theme-toggle-dark")
val Page.lightThemeToggle: Locator
    get() = getByTestId("theme-toggle-light")
