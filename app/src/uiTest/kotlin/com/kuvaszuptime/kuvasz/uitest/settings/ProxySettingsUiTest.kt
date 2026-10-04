package com.kuvaszuptime.kuvasz.uitest.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class ProxySettingsUiTest : UiTestSpec() {

    init {
        "the settings page lists every configured proxy in its own card, with its address, type and auth state" {
            val page = newPage()
            page.navigate("/settings")

            val card = page.proxiesCard()
            assertThat(card).containsText(Messages.proxiesSettings())
            assertThat(card.locator(".icon-tabler-route-square")).hasCount(1)

            val rows = page.getByTestId("proxies-settings").getByTestId("proxy-settings-row")
            assertThat(rows).hasCount(2)

            with(page.proxyRow("corporate-egress")) {
                assertThat(this).containsText("10.0.0.10:3128")
                assertThat(getByTestId("proxy-type")).hasText("HTTP")
                assertThat(getByTestId("proxy-authenticated")).hasText(Messages.proxyAuthenticated())
            }
            with(page.proxyRow("office-network")) {
                assertThat(this).containsText("127.0.0.1:1080")
                assertThat(getByTestId("proxy-type")).hasText("SOCKS5")
                assertThat(getByTestId("proxy-authenticated")).hasCount(0)
            }
        }

        "the settings page never renders the credentials of a proxy" {
            val page = newPage()
            page.navigate("/settings")
            assertThat(page.getByTestId("proxy-settings-row")).hasCount(2)

            page.content() shouldNotContain "e2e-proxy-user"
            page.content() shouldNotContain "e2e-proxy-password"
        }
    }

    private fun Page.proxiesCard(): Locator =
        locator(".card").filter(Locator.FilterOptions().setHas(getByTestId("proxies-settings")))

    private fun Page.proxyRow(name: String): Locator =
        getByTestId("proxy-settings-row").filter(Locator.FilterOptions().setHasText(name))
}
