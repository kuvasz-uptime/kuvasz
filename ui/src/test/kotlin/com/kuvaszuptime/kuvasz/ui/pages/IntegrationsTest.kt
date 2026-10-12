package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.models.dto.integration.EmailNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.SlackBotNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.SlackNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.handlers.IncidentNoteMirroring
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private fun slackBot(
    triggerReaction: String? = null,
    resolutionReaction: String? = null,
    incidentNotes: IncidentNoteMirroring = IncidentNoteMirroring.PUBLIC,
) = SlackBotNotificationConfigDto(
    id = IntegrationID(IntegrationType.SLACK_BOT, "ops"),
    type = IntegrationType.SLACK_BOT,
    name = "ops",
    enabled = true,
    global = false,
    excludedEvents = emptyList(),
    channelId = "C0123",
    triggerReaction = triggerReaction,
    resolutionReaction = resolutionReaction,
    incidentNotes = incidentNotes,
)

class IntegrationsTest : BehaviorSpec({

    given("the details tooltip of an integration") {

        `when`("it's an email integration") {
            val integration = EmailNotificationConfigDto(
                id = IntegrationID(IntegrationType.EMAIL, "mail"),
                type = IntegrationType.EMAIL,
                name = "mail",
                enabled = true,
                global = false,
                excludedEvents = emptyList(),
                fromAddress = "noreply@kuvasz.dev",
                toAddress = "ops@kuvasz.dev",
            )

            then("it should contain the sender and the recipient") {
                integration.detailsTooltip() shouldBe
                    "<b>From</b>: \"noreply@kuvasz.dev\"<br> <b>To</b>: \"ops@kuvasz.dev\""
            }
        }

        `when`("it's a Slack Bot integration without reactions") {
            then("it should contain its channel and how it handles the notes, but never its token") {
                slackBot().detailsTooltip() shouldBe
                    "<b>Channel ID</b>: \"C0123\"<br><b>Incident notes</b>: only the public ones"
            }
        }

        `when`("it's a Slack Bot integration with both reactions") {
            then("it should contain the reactions too") {
                slackBot(
                    triggerReaction = "rotating_light",
                    resolutionReaction = ":white_check_mark:",
                    incidentNotes = IncidentNoteMirroring.ALL,
                ).detailsTooltip() shouldBe
                    "<b>Channel ID</b>: \"C0123\"<br><b>Reactions</b>: rotating_light → :white_check_mark:<br>" +
                    "<b>Incident notes</b>: all of them"
            }
        }

        `when`("it's a Slack Bot integration with only one of the reactions, and a blank other one") {
            then("it should show the missing reaction as a dash") {
                slackBot(
                    triggerReaction = " ",
                    resolutionReaction = "white_check_mark",
                    incidentNotes = IncidentNoteMirroring.NONE,
                ).detailsTooltip() shouldBe
                    "<b>Channel ID</b>: \"C0123\"<br><b>Reactions</b>: - → white_check_mark<br>" +
                    "<b>Incident notes</b>: none of them"
            }
        }

        `when`("it's a Slack Bot integration with blank reactions only") {
            then("it should leave the reactions out") {
                slackBot(triggerReaction = "", resolutionReaction = " ").detailsTooltip() shouldBe
                    "<b>Channel ID</b>: \"C0123\"<br><b>Incident notes</b>: only the public ones"
            }
        }

        `when`("it's an integration without any details worth showing") {
            val integration = SlackNotificationConfigDto(
                id = IntegrationID(IntegrationType.SLACK, "webhook"),
                type = IntegrationType.SLACK,
                name = "webhook",
                enabled = true,
                global = false,
                excludedEvents = emptyList(),
            )

            then("it shouldn't have a tooltip") {
                integration.detailsTooltip().shouldBeNull()
            }
        }
    }
})
