package com.kuvaszuptime.kuvasz.uitest

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import java.io.File

/**
 * Lifecycle helpers around Playwright: how to launch the browser and where to write failure artifacts. The per-spec /
 * per-test orchestration lives in [UiTestSpec].
 */
object PlaywrightSupport {

    const val UI_TEST_ENV = "ui-test"
    const val VIEWPORT_WIDTH = 1440
    const val VIEWPORT_HEIGHT = 900

    private const val SLOW_MO_MS = 150.0

    val artifactsDir: File = File("build/uiTest-artifacts")

    // Headless by default; pass `-Dui.headed=true` to watch the run (with a small slow-mo) locally.
    private val headed: Boolean get() = System.getProperty("ui.headed", "false").toBoolean()

    fun launchBrowser(playwright: Playwright): Browser =
        playwright.chromium().launch(
            BrowserType.LaunchOptions()
                .setHeadless(!headed)
                .setSlowMo(if (headed) SLOW_MO_MS else 0.0)
        )
}
