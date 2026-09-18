package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.models.settings.ConnectivityState
import com.kuvaszuptime.kuvasz.models.settings.ConnectivityStatus
import com.kuvaszuptime.kuvasz.services.connectivity.ConnectivityChecker
import com.kuvaszuptime.kuvasz.testutils.ENABLED_CONNECTIVITY_CHECK
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk

@MicronautTest(startApplication = false, environments = [ENABLED_CONNECTIVITY_CHECK])
class WebUIConnectivityBadgeTest(
    private val controller: WebUIController,
    private val connectivityChecker: ConnectivityChecker,
) : DatabaseBehaviorSpec({

    fun status(state: ConnectivityState) = ConnectivityStatus(
        state = state,
        targets = listOf("127.0.0.1:1"),
        intervalSeconds = 3600,
        timeoutSeconds = 5,
        lastCheckedAt = null,
        lastSuccessfulCheckAt = null,
        downSince = null,
        lastError = null,
    )

    given("the connectivity badge fragment endpoint") {

        `when`("Kuvasz has no outbound connectivity") {
            every { getMock(connectivityChecker).getStatus() } returns status(ConnectivityState.DOWN)
            val html = controller.connectivityBadge()

            then("it renders the warning badge, pointing at the settings page") {
                html shouldContain "connectivity-lost-badge"
                html shouldContain "/settings"
            }
        }

        // The badge must disappear on its own once the connectivity is back, without a page reload
        listOf(ConnectivityState.UP, ConnectivityState.UNKNOWN).forEach { state ->
            `when`("the connectivity is fine ($state)") {
                every { getMock(connectivityChecker).getStatus() } returns status(state)
                val html = controller.connectivityBadge()

                then("it renders nothing at all") {
                    html.shouldBeEmpty()
                }
            }
        }
    }
}) {
    @MockBean(ConnectivityChecker::class)
    fun connectivityCheckerMock(): ConnectivityChecker = mockk(relaxed = true)
}
