package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorDefaults
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
 * These tests are meant to check if a wrongly configured DnsMonitorConfig in the YAML files
 * really hinders the application from starting as expected.
 */
class DnsMonitorConfigValidationTest : BehaviorSpec({

    given("a DnsMonitorConfig bean") {

        `when`("name is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("dns-monitor-blank-name")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DnsMonitorConfig.getName - ${MonitorValidationMessages.NAME_NOT_BLANK}"
            }
        }

        `when`("host is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("dns-monitor-blank-host")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DnsMonitorConfig.getHost - ${MonitorValidationMessages.HOST_NOT_BLANK}"
            }
        }

        `when`("uptimeCheckInterval is less than the minimum") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("dns-monitor-short-uptime-check-interval")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DnsMonitorConfig.getUptimeCheckInterval - Uptime check interval must be at least 5 seconds"
            }
        }

        `when`("failureCountThreshold is less than 1") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("dns-monitor-zero-failure-count-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DnsMonitorConfig.getFailureCountThreshold - " +
                    MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE
            }
        }

        `when`("resolverPort is out of range") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("dns-monitor-out-of-range-resolver-port")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DnsMonitorConfig.getResolverPort - ${MonitorValidationMessages.RESOLVER_PORT_MAX}"
                        .replace("{value}", "65535")
            }
        }

        `when`("a record matcher has an invalid regex pattern") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("dns-monitor-invalid-record-matcher")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain "DnsMonitorConfig.getRecordMatchers"
            }
        }

        `when`("timeoutMs is out of range") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("dns-monitor-out-of-range-timeout")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DnsMonitorConfig.getTimeoutMs - ${MonitorValidationMessages.TIMEOUT_MILLIS_MAX}"
                        .replace("{value}", "30000")
            }
        }

        `when`("latencyThresholdMs is invalid") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("dns-monitor-invalid-latency-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DnsMonitorConfig.getLatencyThresholdMs - ${MonitorValidationMessages.LATENCY_THRESHOLD_MIN}"
                        .replace("{value}", "1")
            }
        }
    }
})

/**
 * These tests are meant to check if a DnsMonitorConfig bean with default values
 * is created correctly when not all the properties are explicitly set in the YAML.
 *
 * It extends a DatabaseBehaviorSpec to delete the inserted monitor properly to not affect other tests
 */
@MicronautTest(startApplication = false, environments = ["dns-monitor-without-defaults"])
class DnsMonitorConfigDefaultValuesTest(applicationContext: ApplicationContext) : DatabaseBehaviorSpec({

    given("a DnsMonitorConfig bean") {

        `when`("not all the properties are explicitly set in the YAML") {

            then("it should fall back to the right default values") {
                val monitorConfig = applicationContext.getBean<DnsMonitorConfig>()
                monitorConfig.enabled shouldBe DnsMonitorDefaults.MONITOR_ENABLED
                monitorConfig.timeoutMs shouldBe DnsMonitorDefaults.TIMEOUT_MS
                monitorConfig.latencyThresholdMs.shouldBeNull()
                monitorConfig.failureCountThreshold shouldBe DnsMonitorDefaults.FAILURE_COUNT_THRESHOLD
                monitorConfig.metricsHistoryEnabled shouldBe DnsMonitorDefaults.METRICS_HISTORY_ENABLED
                monitorConfig.integrations.shouldBeNull()
                monitorConfig.resolverHost.shouldBeNull()
                monitorConfig.resolverPort shouldBe DnsMonitorDefaults.RESOLVER_PORT
                monitorConfig.transport shouldBe DnsTransport.UDP
                monitorConfig.expectedResponseCode shouldBe DnsResponseCode.NOERROR
                monitorConfig.driftDetectionEnabled shouldBe DnsMonitorDefaults.DRIFT_DETECTION_ENABLED
                monitorConfig.driftRecordTypes.shouldBeNull()
                monitorConfig.recordMatchers.shouldBeNull()
            }
        }
    }
})
