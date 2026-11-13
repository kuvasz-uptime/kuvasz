package com.kuvaszuptime.kuvasz.models.handlers

import com.kuvaszuptime.kuvasz.models.dto.Validation
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private data class TestEmailNotificationConfig(
    override val toAddress: List<String>,
    override val name: String = "test",
    override val enabled: Boolean = true,
    override val global: Boolean = false,
    override val excludedEvents: List<IntegrationEventType>? = null,
    override val fromAddress: String = "noreply@kuvasz.dev",
    override val fromName: String? = null,
    override val separateEmails: Boolean = false,
) : EmailNotificationConfig

class EmailNotificationConfigTest : BehaviorSpec({

    given("recipientAddresses()") {

        `when`("there is a single recipient") {
            then("it should be returned as-is") {
                TestEmailNotificationConfig(listOf("ops@kuvasz.dev")).recipientAddresses() shouldContainExactly
                    listOf("ops@kuvasz.dev")
            }
        }

        `when`("the recipients are surrounded by whitespace, e.g. because of a comma-separated env variable") {
            then("they should be trimmed") {
                TestEmailNotificationConfig(listOf("ops@kuvasz.dev", " dev@kuvasz.dev", "\tqa@kuvasz.dev \n"))
                    .recipientAddresses() shouldContainExactly
                    listOf("ops@kuvasz.dev", "dev@kuvasz.dev", "qa@kuvasz.dev")
            }
        }

        `when`("the same recipient is listed more than once") {
            then("it should be deduplicated, keeping the original order") {
                TestEmailNotificationConfig(listOf("ops@kuvasz.dev", "dev@kuvasz.dev", "ops@kuvasz.dev"))
                    .recipientAddresses() shouldContainExactly listOf("ops@kuvasz.dev", "dev@kuvasz.dev")
            }
        }

        `when`("two recipients only differ in their surrounding whitespace") {
            then("they should collapse into one, because deduplication happens after the trimming") {
                TestEmailNotificationConfig(listOf("ops@kuvasz.dev", " ops@kuvasz.dev"))
                    .recipientAddresses() shouldContainExactly listOf("ops@kuvasz.dev")
            }
        }
    }

    given("senderName()") {

        `when`("the name is set") {
            then("it should be returned without the surrounding whitespace") {
                TestEmailNotificationConfig(listOf("ops@kuvasz.dev"), fromName = " Kuvasz Alerts\t")
                    .senderName() shouldBe "Kuvasz Alerts"
            }
        }

        `when`("the name is missing") {
            then("it should be null") {
                TestEmailNotificationConfig(listOf("ops@kuvasz.dev")).senderName().shouldBeNull()
            }
        }

        `when`("the name is blank") {
            then("it should be null") {
                TestEmailNotificationConfig(listOf("ops@kuvasz.dev"), fromName = "").senderName().shouldBeNull()
                TestEmailNotificationConfig(listOf("ops@kuvasz.dev"), fromName = " \t").senderName().shouldBeNull()
            }
        }
    }

    given("the validation pattern of the from name") {
        val pattern = Regex(Validation.EMAIL_FROM_NAME_REGEX)

        `when`("the name is a single line, even if it's blank or surrounded by whitespace") {
            then("it should be accepted") {
                listOf("Kuvasz Alerts", "", " ", " Kuvasz Alerts\t", "Kuvasz Alerts\n", "100% Ops & <Prod>")
                    .forAll { name -> pattern.matches(name) shouldBe true }
            }
        }

        `when`("the name contains a line break or its URL-encoded form") {
            then("it should be rejected") {
                listOf("Kuvasz\nAlerts", "Kuvasz\rAlerts", "Kuvasz\r\nAlerts", "Kuvasz%0AAlerts")
                    .forAll { name -> pattern.matches(name) shouldBe false }
            }
        }
    }
})
