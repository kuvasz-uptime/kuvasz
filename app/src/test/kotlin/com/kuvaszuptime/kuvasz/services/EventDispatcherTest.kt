@file:Suppress("TooGenericExceptionThrown")

package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlin.time.Duration.Companion.seconds

@MicronautTest(startApplication = false, transactional = false)
class EventDispatcherTest(monitorRepository: HttpMonitorRepository) : DatabaseBehaviorSpec({

    val dispatcher = EventDispatcher()
    var errorCnt = 0
    var successfulInvocationCnt = 0

    dispatcher.subscribeToHttpMonitorUpEvents { event ->
        if (event.latency == 123) {
            errorCnt++
            throw RuntimeException("Simulated error")
        } else {
            successfulInvocationCnt++
        }
    }

    given("an event dispatcher") {

        `when`("an error occurs in a consumer") {
            val monitor = createHttpMonitor(monitorRepository)
            val monitorUpEvent = HttpMonitorUpEvent(
                monitor,
                HttpStatus.OK,
                latency = 123,
                previousEvent = null,
            )

            dispatcher.dispatch(monitorUpEvent)
            dispatcher.dispatch(monitorUpEvent.copy(latency = 343))

            then("it should not cancel the subscription") {
                eventually(2.seconds) {
                    errorCnt shouldBe 1
                    successfulInvocationCnt shouldBe 1
                }
            }
        }
    }
})
