package com.kuvaszuptime.kuvasz.models.handlers

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly

private data class TestEmailNotificationConfig(
    override val toAddress: List<String>,
    override val name: String = "test",
    override val enabled: Boolean = true,
    override val global: Boolean = false,
    override val excludedEvents: List<IntegrationEventType>? = null,
    override val fromAddress: String = "noreply@kuvasz.dev",
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
})
