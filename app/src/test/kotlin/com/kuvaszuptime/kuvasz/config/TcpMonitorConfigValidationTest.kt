package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorDefaults
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * These tests are meant to check if a wrongly configured TcpMonitorConfig in the YAML files
 * really hinders the application from starting as expected.
 */
class TcpMonitorConfigValidationTest : BehaviorSpec({

    given("a TcpMonitorConfig bean") {

        `when`("name is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("tcp-monitor-blank-name")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "TcpMonitorConfig.getName - ${MonitorValidationMessages.NAME_NOT_BLANK}"
            }
        }

        `when`("host is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("tcp-monitor-blank-host")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "TcpMonitorConfig.getHost - ${MonitorValidationMessages.HOST_NOT_BLANK}"
            }
        }

        `when`("uptimeCheckInterval is less than the minimum") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("tcp-monitor-short-uptime-check-interval")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "TcpMonitorConfig.getUptimeCheckInterval - Uptime check interval must be at least 5 seconds"
            }
        }

        `when`("failureCountThreshold is less than 1") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("tcp-monitor-zero-failure-count-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "TcpMonitorConfig.getFailureCountThreshold - " +
                    MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE
            }
        }

        `when`("port is out of range") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("tcp-monitor-out-of-range-port")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "TcpMonitorConfig.getPort - ${MonitorValidationMessages.PORT_MAX}"
                        .replace("{value}", "65535")
            }
        }

        `when`("timeoutMs is out of range") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("tcp-monitor-out-of-range-timeout")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "TcpMonitorConfig.getTimeoutMs - ${MonitorValidationMessages.TIMEOUT_MILLIS_MAX}"
                        .replace("{value}", "30000")
            }
        }

        `when`("latencyThresholdMs is invalid") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("tcp-monitor-invalid-latency-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "TcpMonitorConfig.getLatencyThresholdMs - ${MonitorValidationMessages.LATENCY_THRESHOLD_MIN}"
                        .replace("{value}", "1")
            }
        }
    }
})

/**
 * These tests are meant to check if a TcpMonitorConfig bean with default values
 * is created correctly when not all the properties are explicitly set in the YAML.
 *
 * It extends a DatabaseBehaviorSpec to delete the inserted monitor properly to not affect other tests
 */
@MicronautTest(startApplication = false, environments = ["tcp-monitor-without-defaults"])
class TcpMonitorConfigDefaultValuesTest(applicationContext: ApplicationContext) : DatabaseBehaviorSpec({

    given("a TcpMonitorConfig bean") {

        `when`("not all the properties are explicitly set in the YAML") {

            then("it should fall back to the right default values") {
                val monitorConfig = applicationContext.getBean<TcpMonitorConfig>()
                monitorConfig.enabled shouldBe TcpMonitorDefaults.MONITOR_ENABLED
                monitorConfig.timeoutMs shouldBe TcpMonitorDefaults.TIMEOUT_MS
                monitorConfig.latencyThresholdMs.shouldBeNull()
                monitorConfig.failureCountThreshold shouldBe TcpMonitorDefaults.FAILURE_COUNT_THRESHOLD
                monitorConfig.metricsHistoryEnabled shouldBe TcpMonitorDefaults.METRICS_HISTORY_ENABLED
                monitorConfig.integrations.shouldBeNull()
            }
        }
    }
})
