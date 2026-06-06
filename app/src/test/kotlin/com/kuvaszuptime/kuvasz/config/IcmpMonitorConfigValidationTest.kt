package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDefaults
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
 * These tests are meant to check if a wrongly configured IcmpMonitorConfig in the YAML files
 * really hinders the application from starting as expected.
 */
class IcmpMonitorConfigValidationTest : BehaviorSpec({

    given("an IcmpMonitorConfig bean") {

        `when`("name is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("icmp-monitor-blank-name")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "IcmpMonitorConfig.getName - ${MonitorValidationMessages.NAME_NOT_BLANK}"
            }
        }

        `when`("host is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("icmp-monitor-blank-host")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "IcmpMonitorConfig.getHost - ${MonitorValidationMessages.HOST_NOT_BLANK}"
            }
        }

        `when`("uptimeCheckInterval is less than the minimum") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("icmp-monitor-short-uptime-check-interval")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "IcmpMonitorConfig.getUptimeCheckInterval - Uptime check interval must be at least 5 seconds"
            }
        }

        `when`("failureCountThreshold is less than 1") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("icmp-monitor-zero-failure-count-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "IcmpMonitorConfig.getFailureCountThreshold - " +
                    MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE
            }
        }

        `when`("packetCount is out of range") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("icmp-monitor-out-of-range-packet-count")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "IcmpMonitorConfig.getPacketCount - ${MonitorValidationMessages.PACKET_COUNT_MAX}"
                        .replace("{value}", "10")
            }
        }

        `when`("timeoutSeconds is out of range") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("icmp-monitor-out-of-range-timeout")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "IcmpMonitorConfig.getTimeoutSeconds - ${MonitorValidationMessages.TIMEOUT_SECONDS_MAX}"
                        .replace("{value}", "30")
            }
        }

        `when`("packetLossThreshold is out of range") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("icmp-monitor-out-of-range-packet-loss-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "IcmpMonitorConfig.getPacketLossThreshold - ${MonitorValidationMessages.PACKET_LOSS_THRESHOLD_MIN}"
                        .replace("{value}", "1")
            }
        }
    }
})

/**
 * These tests are meant to check if an IcmpMonitorConfig bean with default values
 * is created correctly when not all the properties are explicitly set in the YAML.
 *
 * It extends a DatabaseBehaviorSpec to delete the inserted monitor properly to not affect other tests
 */
@MicronautTest(startApplication = false, environments = ["icmp-monitor-without-defaults"])
class IcmpMonitorConfigDefaultValuesTest(applicationContext: ApplicationContext) : DatabaseBehaviorSpec({

    given("an IcmpMonitorConfig bean") {

        `when`("not all the properties are explicitly set in the YAML") {

            then("it should fall back to the right default values") {
                val monitorConfig = applicationContext.getBean<IcmpMonitorConfig>()
                monitorConfig.enabled shouldBe IcmpMonitorDefaults.MONITOR_ENABLED
                monitorConfig.packetCount shouldBe IcmpMonitorDefaults.PACKET_COUNT
                monitorConfig.timeoutSeconds shouldBe IcmpMonitorDefaults.TIMEOUT_SECONDS
                monitorConfig.packetLossThreshold shouldBe IcmpMonitorDefaults.PACKET_LOSS_THRESHOLD
                monitorConfig.failureCountThreshold shouldBe IcmpMonitorDefaults.FAILURE_COUNT_THRESHOLD
                monitorConfig.metricsHistoryEnabled shouldBe IcmpMonitorDefaults.METRICS_HISTORY_ENABLED
                monitorConfig.integrations.shouldBeNull()
            }
        }
    }
})
