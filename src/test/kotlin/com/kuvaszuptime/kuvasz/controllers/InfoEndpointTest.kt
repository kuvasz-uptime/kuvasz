package com.kuvaszuptime.kuvasz.controllers

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest

@MicronautTest
class InfoEndpointTest(
    @Client("/") private val client: RxHttpClient
) : BehaviorSpec({
    given("the /info endpoint") {
        `when`("it has been called") {
            val response = client.toBlocking().retrieve("/info")
            then("it should return information about the event handlers") {
                response shouldContain "log-event-handler.enabled"
                response shouldContain "smtp-event-handler.enabled"
                response shouldContain "slack-event-handler.enabled"
            }
        }
    }
})
