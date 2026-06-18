package com.kuvaszuptime.kuvasz.uitest.pages

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole

// The `/login` page with the username/password form (security is ON in the UI-test environment, OIDC off).
class LoginPage(private val page: Page) {

    val usernameInput: Locator get() = page.locator("input[name=username]")
    val passwordInput: Locator get() = page.locator("input[name=password]")
    val signInButton: Locator get() = page.byRole(AriaRole.BUTTON, Messages.signIn())

    // The dismissible alert shown after a failed login (rendered when `/login?error=true`).
    val errorAlert: Locator get() = page.locator(".alert-danger")

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
