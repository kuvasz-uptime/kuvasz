package com.kuvaszuptime.kuvasz.controllers.internal

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest
class InternalValidationControllerTest(
    private val client: InternalValidationClient,
) : BehaviorSpec({

    fun statusFor(value: String): HttpStatus =
        try {
            client.validateCron(value).status
        } catch (ex: HttpClientResponseException) {
            ex.status
        }

    given("the cron validation endpoint") {

        `when`("a valid 5-field cron expression is provided") {
            then("it returns 200") {
                statusFor("0 2 * * *") shouldBe HttpStatus.OK
            }
        }

        `when`("a cron expression using Micronaut-specific extensions is provided") {
            then("it returns 200") {
                statusFor("0 0 L * *") shouldBe HttpStatus.OK
                statusFor("0 0 * * MON#1") shouldBe HttpStatus.OK
                statusFor("0 0 12 * * ?") shouldBe HttpStatus.OK
            }
        }

        `when`("an invalid cron expression is provided") {
            then("it returns 400") {
                statusFor("not a cron") shouldBe HttpStatus.BAD_REQUEST
                statusFor("99 99 99 99 99") shouldBe HttpStatus.BAD_REQUEST
                statusFor("") shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }
})
