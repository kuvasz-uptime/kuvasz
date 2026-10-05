package com.kuvaszuptime.kuvasz.uitest.settings

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.services.proxy.ProxyRegistry
import com.kuvaszuptime.kuvasz.uitest.PlaywrightSupport
import com.kuvaszuptime.kuvasz.uitest.UiTestSpec
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk

@MicronautTest(environments = [PlaywrightSupport.UI_TEST_ENV])
class ProxySettingsWithoutProxiesUiTest : UiTestSpec() {

    // The UI test config defines proxies, so their absence can only come from here
    @MockBean(ProxyRegistry::class)
    fun proxyRegistryMock(): ProxyRegistry = mockk {
        every { getProxyDtos() } returns emptyList()
        every { configuredProxies } returns emptyMap()
    }

    init {
        "the proxies card tells that no proxy is configured" {
            val page = newPage()
            page.navigate("/settings")

            val proxies = page.getByTestId("proxies-settings")
            assertThat(proxies).hasText(Messages.notConfigured())
            assertThat(proxies.getByTestId("proxy-settings-row")).hasCount(0)
        }
    }
}
