package com.kuvaszuptime.kuvasz.uitest.auth

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.testutils.KeycloakTestRealm
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.kuvaszuptime.kuvasz.uitest.pages.DashboardPage
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider

/**
 * Browser-driven end-to-end test of the OIDC login and logout flows against a real Keycloak (the same realm as
 * `OidcAuthenticationE2ETest`, but driving the actual Keycloak login page through Chromium instead of raw HTTP).
 */
@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class OidcLoginUiTest : UiTestSpec(), TestPropertyProvider {

    override fun getProperties(): Map<String, String> {
        if (!keycloak.isRunning) keycloak.start()
        val issuer = KeycloakTestRealm.issuerUrl(keycloak.authServerUrl)
        return mapOf(
            "micronaut.security.oauth2.clients.oidc.enabled" to "true",
            "micronaut.security.oauth2.clients.oidc.client-id" to KeycloakTestRealm.CLIENT_ID,
            "micronaut.security.oauth2.clients.oidc.client-secret" to KeycloakTestRealm.CLIENT_SECRET,
            "micronaut.security.oauth2.clients.oidc.openid.issuer" to issuer,
            // Modern Keycloak issuers aren't auto-detected, so the authorization server has to be set explicitly.
            "micronaut.security.oauth2.clients.oidc.authorization-server" to "keycloak",
        )
    }

    init {
        afterSpec { keycloak.stop() }

        "an OIDC user signs in through Keycloak and lands on the authenticated dashboard" {
            val page = newPage(authenticated = false)

            page.navigate("/login")
            assertThat(oidcLoginButton(page)).isVisible()
            assertThat(page.locator("input[name=username]")).hasCount(0)

            signInViaOidc(page)

            assertThat(DashboardPage(page).heading).isVisible()
            page.context().cookies().map { it.name } shouldContain "JWT"
        }

        "signing out clears the session via the IdP and returns to the login page" {
            val page = newPage(authenticated = false)
            signInViaOidc(page)
            assertThat(DashboardPage(page).heading).isVisible()

            // Sign-out goes through Keycloak's end-session endpoint, which requires confirming on its logout page.
            page.getByRole(AriaRole.LINK, Page.GetByRoleOptions().setName(Messages.signOut())).click()
            page.click("#kc-logout")
            assertThat(oidcLoginButton(page)).isVisible()
            page.context().cookies().map { it.name } shouldNotContain "JWT"

            page.navigate("/")
            assertThat(oidcLoginButton(page)).isVisible()
        }
    }

    private fun oidcLoginButton(page: Page) =
        page.getByRole(AriaRole.LINK, Page.GetByRoleOptions().setName(Messages.loginWithOidc()))

    // Drives the authorization-code flow: from the Kuvasz login page through the Keycloak form, back to the app.
    private fun signInViaOidc(page: Page) {
        page.navigate("/login")
        oidcLoginButton(page).click()
        page.fill("#username", KeycloakTestRealm.USERNAME)
        page.fill("#password", KeycloakTestRealm.PASSWORD)
        page.click("#kc-login")
    }

    companion object {
        private val keycloak = KeycloakTestRealm.newContainer()
    }
}
