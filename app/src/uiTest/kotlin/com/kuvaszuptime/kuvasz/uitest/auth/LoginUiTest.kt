package com.kuvaszuptime.kuvasz.uitest.auth

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.kuvaszuptime.kuvasz.uitest.pages.LoginPage
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class LoginUiTest : UiTestSpec() {
    init {
        "valid credentials log the admin in and land on the dashboard" {
            val page = newPage(authenticated = false)

            LoginPage(page).loginAs(AdminCredentials.USERNAME, AdminCredentials.PASSWORD)

            assertThat(DashboardPage(page).heading).isVisible()
        }

        "invalid credentials show the error alert on the login page" {
            val page = newPage(authenticated = false)
            val login = LoginPage(page)

            login.submit(AdminCredentials.USERNAME, "definitely-wrong-password")

            assertThat(login.errorAlert).isVisible()
            assertThat(login.errorAlert).containsText(Messages.invalidCredentials())
        }
    }
}
