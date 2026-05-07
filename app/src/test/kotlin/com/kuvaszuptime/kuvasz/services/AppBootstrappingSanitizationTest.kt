package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseStringSpec
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.testAppContext
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import org.jooq.DSLContext

@MicronautTest(startApplication = false)
class AppBootstrappingSanitizationTest(
    httpMonitorRepository: HttpMonitorRepository,
    pushMonitorRepository: PushMonitorRepository,
    dslContext: DSLContext,
) : DatabaseStringSpec({

    "non-existing integrations should be removed from HTTP monitors upon startup, disabled should be kept" {
        val monitor = createHttpMonitor(httpMonitorRepository)

        // Manually adding non-existing integrations to the monitor
        dslContext
            .update(HTTP_MONITOR)
            .set(
                HTTP_MONITOR.INTEGRATIONS,
                arrayOf(
                    IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
                    IntegrationID(IntegrationType.EMAIL, "disabled"),
                    IntegrationID(IntegrationType.TELEGRAM, "that_does_not_exist"),
                    IntegrationID(IntegrationType.WEBHOOK, "disabled"),
                )
            )
            .awaitFirst()
        val updatedMonitor = httpMonitorRepository.findById(monitor.id, null).shouldNotBeNull()

        updatedMonitor.integrations shouldContainExactlyInAnyOrder arrayOf(
            IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
            IntegrationID(IntegrationType.EMAIL, "disabled"),
            IntegrationID(IntegrationType.TELEGRAM, "that_does_not_exist"),
            IntegrationID(IntegrationType.WEBHOOK, "disabled"),
        )

        // Simulating the restart of the application
        shouldNotThrowAny { testAppContext("full-integrations-setup") }
        val sanitizedMonitor = httpMonitorRepository.findById(monitor.id, null).shouldNotBeNull()

        // The configured ones should be kept, even the disabled one
        sanitizedMonitor.integrations shouldContainExactlyInAnyOrder arrayOf(
            IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
            IntegrationID(IntegrationType.EMAIL, "disabled"),
            IntegrationID(IntegrationType.WEBHOOK, "disabled"),
        )
    }

    "non-existing integrations should be removed from PUSH monitors upon startup, disabled should be kept" {
        val monitor = createPushMonitor(pushMonitorRepository)

        // Manually adding non-existing integrations to the monitor
        dslContext
            .update(PUSH_MONITOR)
            .set(
                PUSH_MONITOR.INTEGRATIONS,
                arrayOf(
                    IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
                    IntegrationID(IntegrationType.EMAIL, "disabled"),
                    IntegrationID(IntegrationType.TELEGRAM, "that_does_not_exist"),
                )
            )
            .awaitFirst()
        val updatedMonitor = pushMonitorRepository.findById(monitor.id, null).shouldNotBeNull()

        updatedMonitor.integrations shouldContainExactlyInAnyOrder arrayOf(
            IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
            IntegrationID(IntegrationType.EMAIL, "disabled"),
            IntegrationID(IntegrationType.TELEGRAM, "that_does_not_exist"),
        )

        // Simulating the restart of the application
        shouldNotThrowAny { testAppContext("full-integrations-setup") }
        val sanitizedMonitor = pushMonitorRepository.findById(monitor.id, null).shouldNotBeNull()

        // The configured ones should be kept, even the disabled one
        sanitizedMonitor.integrations shouldContainExactlyInAnyOrder arrayOf(
            IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
            IntegrationID(IntegrationType.EMAIL, "disabled"),
        )
    }
})
