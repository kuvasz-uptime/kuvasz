package com.kuvaszuptime.kuvasz.controllers.integration

import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.integration.IntegrationConfigDto
import com.kuvaszuptime.kuvasz.models.handlers.DiscordNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import com.kuvaszuptime.kuvasz.services.integrations.DiscordWebhookService
import com.kuvaszuptime.kuvasz.services.integrations.EmailTestService
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.NotificationTestResult
import com.kuvaszuptime.kuvasz.services.integrations.PagerdutyTestService
import com.kuvaszuptime.kuvasz.services.integrations.SlackWebhookService
import com.kuvaszuptime.kuvasz.services.integrations.TelegramAPIService
import com.kuvaszuptime.kuvasz.services.integrations.TestableNotificationService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.validation.Validated
import io.reactivex.rxjava3.core.Single
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory

@Controller("${API_V2_PREFIX}/integrations", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = "Integrations")
class IntegrationController(
    private val testServices: List<TestableNotificationService<IntegrationConfig>>,
    private val integrationRepository: IntegrationRepository,
) : IntegrationOperations {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "The result of the test",
            content = [Content(schema = Schema(implementation = NotificationTestResult::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
    )
    override fun sendTestNotification(integrationId: IntegrationID): Single<NotificationTestResult> {
        logger.debug("Sending test notification for integration with ID: $integrationId")
        val config = integrationRepository.configuredIntegrations[integrationId] ?: return Single.just(
            NotificationTestResult(
                success = false,
                message = Messages.integrationNotFoundInTest(integrationId),
            )
        )

        val result = when (config) {
            is DiscordNotificationConfig -> sendTestMessage<DiscordNotificationConfig, DiscordWebhookService>(config)
            is EmailNotificationConfig -> sendTestMessage<EmailNotificationConfig, EmailTestService>(config)
            is PagerdutyConfig -> sendTestMessage<PagerdutyConfig, PagerdutyTestService>(config)
            is SlackNotificationConfig -> sendTestMessage<SlackNotificationConfig, SlackWebhookService>(config)
            is TelegramNotificationConfig -> sendTestMessage<TelegramNotificationConfig, TelegramAPIService>(config)
        }
        logger.debug("Test notification result for integration ID $integrationId: $result")
        return result
    }

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "The list of configured integrations",
        ),
    )
    override fun getIntegrations(): List<IntegrationConfigDto> =
        integrationRepository.getConfiguredIntegrationDtos().sortedBy { it.name }

    private inline fun <reified C : IntegrationConfig, reified S : TestableNotificationService<C>> sendTestMessage(
        config: C
    ): Single<NotificationTestResult> = testServices
        .firstOrNull { it is S }
        ?.sendTestMessage(config)
        ?: Single.just( // Unlikely, the service depends on at least one config being present, it should return earlier
            NotificationTestResult(
                success = false,
                message = Messages.noServiceConfiguredForTest(C::class.simpleName.orEmpty()),
            )
        )
}
