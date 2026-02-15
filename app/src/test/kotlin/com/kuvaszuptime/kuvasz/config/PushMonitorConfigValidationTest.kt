package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDefaults
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * These tests are meant to check if a wrongly configured MonitorConfig in the YAML files
 * really hinders the application from starting as expected.
 */
class PushMonitorConfigValidationTest : BehaviorSpec({

    given("a PushMonitorConfig bean") {

        `when`("name is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("push-monitor-blank-name")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "PushMonitorConfig.getName - ${MonitorValidationMessages.NAME_NOT_BLANK}"
            }
        }

        `when`("heartbeatInterval is null") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("push-monitor-null-heartbeat-interval")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "No property found for name [push-monitors[0].heartbeat-interval]"
            }
        }

        `when`("heartbeatInterval is less than 10 seconds") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("push-monitor-short-heartbeat-interval")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "PushMonitorConfig.getHeartbeatInterval - Heartbeat interval must be at least 10 seconds"
            }
        }

        `when`("gracePeriod is less than 0 seconds") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("push-monitor-negative-grace-period")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "PushMonitorConfig.getGracePeriod - " +
                    MonitorValidationMessages.GRACE_PERIOD_POSITIVE_OR_ZERO
            }
        }

        `when`("clientSecret is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("push-monitor-empty-client-secret")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "PushMonitorConfig.getClientSecret - Client secret must be at least 36 characters long"
            }
        }

        `when`("clientSecret is a blank string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("push-monitor-blank-client-secret")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "PushMonitorConfig.getClientSecret - Client secret must not be blank"
            }
        }

        `when`("clientSecret is less than 36 characters long") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("push-monitor-short-client-secret")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "PushMonitorConfig.getClientSecret - Client secret must be at least 36 characters long"
            }
        }

        `when`("a clientSecret is not unique") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("push-monitor-conflicting-client-secret")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "YAML push monitor configs must have unique client secrets!"
            }
        }

        `when`("failureCountThreshold is less than 1") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("push-monitor-zero-failure-count-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exceptionToMessage(exception) shouldContain
                    "PushMonitorConfig.getFailureCountThreshold - " +
                    MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE
            }
        }
    }
})

/**
 * These tests are meant to check if a PushMonitorConfig bean with default values
 * is created correctly when not all the properties are explicitly set in the YAML.
 *
 * It extends a DatabaseBehaviorSpec to delete the inserted monitor properly to not affect other tests
 */
@MicronautTest(startApplication = false, environments = ["push-monitor-without-defaults"])
class PushMonitorConfigDefaultValuesTest(applicationContext: ApplicationContext) : DatabaseBehaviorSpec({

    given("a PushMonitorConfig bean") {

        `when`("not all the properties are explicitly set in the YAML") {

            then("it should fall back to the right default values") {
                val monitorConfig = applicationContext.getBean<PushMonitorConfig>()
                monitorConfig.enabled shouldBe PushMonitorDefaults.MONITOR_ENABLED
                monitorConfig.gracePeriod shouldBe PushMonitorDefaults.GRACE_PERIOD_SECONDS
                monitorConfig.integrations shouldBe null
                monitorConfig.failureCountThreshold shouldBe PushMonitorDefaults.FAILURE_COUNT_THRESHOLD
            }
        }
    }
})
