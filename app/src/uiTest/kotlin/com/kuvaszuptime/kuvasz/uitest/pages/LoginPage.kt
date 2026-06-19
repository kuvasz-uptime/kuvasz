package com.kuvaszuptime.kuvasz.uitest.pages

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

// The `/login` page with the username/password form
class LoginPage(private val page: Page) {

    val usernameInput: Locator get() = page.locator("input[name=username]")
    val passwordInput: Locator get() = page.locator("input[name=password]")
    val signInButton: Locator get() = page.getByTestId("login-submit-button")

    val errorAlert: Locator get() = page.getByTestId("login-error")

    fun navigate() {
        page.navigate("/login")
    }

    // Fills and submits the form without waiting for an outcome (used for the bad-credentials path).
    fun submit(username: String, password: String) {
        navigate()
        usernameInput.fill(username)
        passwordInput.fill(password)
        signInButton.click()
    }

    // Logs in and blocks until the post-login redirect has navigated away from `/login`.
    fun loginAs(username: String, password: String) {
        submit(username, password)
        page.waitForURL { url -> !url.contains("/login") }
    }
}
