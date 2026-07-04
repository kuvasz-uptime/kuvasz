package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.models.dto.MaintenanceWindowValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDefaults
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * These tests are meant to check if a wrongly configured MaintenanceWindowConfig in the YAML files
 * really hinders the application from starting as expected.
 */
@MicronautTest(startApplication = false)
class MaintenanceWindowConfigValidationTest : DatabaseBehaviorSpec({

    given("a MaintenanceWindowConfig bean") {

        `when`("the name is a blank string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("mw-blank-name")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain MaintenanceWindowValidationMessages.NAME_NOT_BLANK
            }
        }

        `when`("the cron expression is invalid") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("mw-invalid-cron")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain MaintenanceWindowValidationMessages.CRON_INVALID
            }
        }

        `when`("the duration is invalid") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("mw-invalid-duration")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain MaintenanceWindowValidationMessages.DURATION_INVALID
            }
        }

        `when`("monitors contain an invalid monitor ID") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("mw-invalid-monitor")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain "Invalid monitor ID format: htt:test1. Expected format is 'type:name'"
            }
        }

        `when`("monitors contain a missing monitor ID") {
            then("AppContext should NOT throw a BeanInstantiationException") {
                shouldNotThrowAny {
                    testAppContext("maintenance-windows-missing-monitor")
                }
            }
        }
    }
})

/**
 * These tests are meant to check if a MaintenanceWindowConfig bean with default values is created correctly when not
 * all the properties are explicitly set in the YAML.
 *
 * It extends a DatabaseBehaviorSpec just to delete the imported maintenance windows properly to not affect other tests
 */
@MicronautTest(startApplication = false, environments = ["mw-without-defaults"])
class MaintenanceWindowConfigDefaultValuesTest(applicationContext: ApplicationContext) : DatabaseBehaviorSpec({

    given("the MaintenanceWindow config bean") {

        `when`("not all the properties are explicitly set in the YAML") {

            then("it should fall back to the right default values") {
                val config = applicationContext.getBean<MaintenanceWindowConfig>()
                config.name shouldBe "Valid Name"
                config.description.shouldBeNull()
                config.enabled shouldBe MaintenanceWindowDefaults.ENABLED
                config.global shouldBe MaintenanceWindowDefaults.GLOBAL
                config.showOnStatusPages shouldBe MaintenanceWindowDefaults.SHOW_ON_STATUS_PAGES
                config.cron.shouldBeNull()
                config.start.shouldBeNull()
                config.duration.shouldBeNull()
                config.monitors.shouldBeNull()
                config.integrations.shouldBeNull()
            }
        }
    }
})
