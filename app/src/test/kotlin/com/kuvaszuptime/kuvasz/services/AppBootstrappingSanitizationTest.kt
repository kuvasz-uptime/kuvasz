package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.DatabaseStringSpec
import com.kuvaszuptime.kuvasz.jooq.tables.Monitor.MONITOR
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import org.jooq.DSLContext

@MicronautTest(startApplication = false)
class AppBootstrappingSanitizationTest(
    monitorRepository: MonitorRepository,
    dslContext: DSLContext,
) : DatabaseStringSpec({

    "non-existing integrations should be removed from monitors upon startup, disabled should be kept" {
        val monitor = createMonitor(monitorRepository)

        // Manually adding non-existing integrations to the monitor
        dslContext
            .update(MONITOR)
            .set(
                MONITOR.INTEGRATIONS,
                arrayOf(
                    IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
                    IntegrationID(IntegrationType.EMAIL, "disabled"),
                    IntegrationID(IntegrationType.TELEGRAM, "that_does_not_exist"),
                )
            )
            .awaitFirst()
        val updatedMonitor = monitorRepository.findById(monitor.id).shouldNotBeNull()

        updatedMonitor.integrations shouldContainExactlyInAnyOrder arrayOf(
            IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
            IntegrationID(IntegrationType.EMAIL, "disabled"),
            IntegrationID(IntegrationType.TELEGRAM, "that_does_not_exist"),
        )

        // Simulating the restart of the application
        shouldNotThrowAny { ApplicationContext.run("full-integrations-setup") }
        val sanitizedMonitor = monitorRepository.findById(monitor.id).shouldNotBeNull()

        // The configured ones should be kept, even the disabled one
        sanitizedMonitor.integrations shouldContainExactlyInAnyOrder arrayOf(
            IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled"),
            IntegrationID(IntegrationType.EMAIL, "disabled"),
        )
    }
})
