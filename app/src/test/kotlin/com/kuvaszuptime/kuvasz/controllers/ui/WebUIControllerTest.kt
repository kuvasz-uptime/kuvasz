package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.services.MonitorCrudService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class WebUIControllerTest(
    controller: WebUIController,
    monitorRepository: MonitorRepository,
    monitorCrudService: MonitorCrudService,
) : DatabaseBehaviorSpec({

    given("the WebUIController's /monitor/{monitorId} endpoint") {

        `when`("it is called with a non-existing monitorId") {

            then("it should throw a MonitorNotFoundException") {
                shouldThrow<MonitorNotFoundException> { controller.monitorDetails(1) }
            }
        }

        `when`("it is called with an existing monitorId") {
            val monitor = createMonitor(monitorRepository)
            val monitorDetails = monitorCrudService.getMonitorDetails(monitor.id)

            val viewParams = controller.monitorDetails(monitor.id)

            then("it should return it under the 'monitor' key") {
                viewParams["monitor"] shouldBe monitorDetails
            }
        }
    }
})
