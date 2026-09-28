package com.kuvaszuptime.kuvasz.uitest.pages.settings

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The "Appearance" card of the Settings page (`/settings`), with the gray palette and the accent color pickers.
class SettingsAppearancePage(private val page: Page) {

    fun navigate() {
        page.navigate("/settings")
    }

    // The radio of a value of a theme option ("base" or "primary")
    fun optionRadio(option: String, value: String): Locator =
        page.getByTestId("theme-$option-picker").locator("input[value='$value']")

    // The radios are visually hidden behind their swatches, so they need a forced check.
    fun pick(option: String, value: String) {
        optionRadio(option, value).check(Locator.CheckOptions().setForce(true))
    }
}
