package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.dashboardActualStats
import com.kuvaszuptime.kuvasz.mocks.dashboardOverview
import com.kuvaszuptime.kuvasz.services.ui.DashboardDataProvider
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration

/**
 * The periods the dashboard's overview is calculated for. What the dashboard renders is covered by the UI tests.
 */
@MicronautTest(startApplication = false)
class WebUIDashboardTest(
    private val controller: WebUIController,
    private val dashboardDataProvider: DashboardDataProvider,
) : DatabaseBehaviorSpec({

    // Resolving the mock while the spec itself is being instantiated would be a circular dependency
    fun provider() = getMock(dashboardDataProvider)

    given("the dashboard overview fragment") {

        listOf(
            null to Duration.ofDays(7),
            "PT0S" to Duration.ofDays(7),
            "-P1D" to Duration.ofDays(7),
            // Only the periods of the selector are accepted
            "PT0.01S" to Duration.ofDays(7),
            "P3D" to Duration.ofDays(7),
            // A malformed one doesn't fail the page either
            "abc" to Duration.ofDays(7),
            "PT1H" to Duration.ofHours(1),
            "PT720H" to Duration.ofDays(30),
        ).forEach { (requestedPeriod, expectedPeriod) ->
            `when`("the requested period is $requestedPeriod") {

                then("it should calculate the overview of $expectedPeriod") {
                    // The mock is refreshed after every test, so it has to be called and verified in the same one
                    every { provider().getOverview(any()) } answers {
                        dashboardOverview(firstArg(), dashboardActualStats())
                    }
                    controller.dashboardOverview(period = requestedPeriod)

                    verify { provider().getOverview(expectedPeriod) }
                }
            }
        }
    }
}) {
    @MockBean(DashboardDataProvider::class)
    fun dashboardDataProviderMock(): DashboardDataProvider = mockk()
}
