package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorDefaults
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
 * These tests are meant to check if a wrongly configured DockerMonitorConfig in the YAML files
 * really hinders the application from starting as expected.
 */
class DockerMonitorConfigValidationTest : BehaviorSpec({

    given("a DockerMonitorConfig bean") {

        `when`("name is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("docker-monitor-blank-name")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DockerMonitorConfig.getName - ${MonitorValidationMessages.NAME_NOT_BLANK}"
            }
        }

        `when`("dockerHost is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("docker-monitor-blank-host")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DockerMonitorConfig.getDockerHost - ${MonitorValidationMessages.DOCKER_HOST_NOT_BLANK}"
            }
        }

        `when`("container is an empty string") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("docker-monitor-blank-container")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DockerMonitorConfig.getContainer - ${MonitorValidationMessages.CONTAINER_NOT_BLANK}"
            }
        }

        `when`("uptimeCheckInterval is less than the minimum") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("docker-monitor-short-uptime-check-interval")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DockerMonitorConfig.getUptimeCheckInterval - Uptime check interval must be at least 5 seconds"
            }
        }

        `when`("failureCountThreshold is less than 1") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("docker-monitor-zero-failure-count-threshold")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DockerMonitorConfig.getFailureCountThreshold - " +
                    MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE
            }
        }

        `when`("timeoutMs is out of range") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext("docker-monitor-out-of-range-timeout")
            }
            then("AppContext should throw a BeanInstantiationException") {
                exception.message shouldContain
                    "DockerMonitorConfig.getTimeoutMs - ${MonitorValidationMessages.TIMEOUT_MILLIS_MAX}"
                        .replace("{value}", "30000")
            }
        }
    }
})

/**
 * These tests are meant to check if a DockerMonitorConfig bean with default values
 * is created correctly when not all the properties are explicitly set in the YAML.
 *
 * It extends a DatabaseBehaviorSpec to delete the inserted monitor properly to not affect other tests
 */
@MicronautTest(startApplication = false, environments = ["docker-monitor-without-defaults", "docker-hosts"])
class DockerMonitorConfigDefaultValuesTest(applicationContext: ApplicationContext) : DatabaseBehaviorSpec({

    given("a DockerMonitorConfig bean") {

        `when`("not all the properties are explicitly set in the YAML") {

            then("it should fall back to the right default values") {
                val monitorConfig = applicationContext.getBean<DockerMonitorConfig>()
                monitorConfig.enabled shouldBe DockerMonitorDefaults.MONITOR_ENABLED
                monitorConfig.timeoutMs shouldBe DockerMonitorDefaults.TIMEOUT_MS
                monitorConfig.failureCountThreshold shouldBe DockerMonitorDefaults.FAILURE_COUNT_THRESHOLD
                monitorConfig.metricsHistoryEnabled shouldBe DockerMonitorDefaults.METRICS_HISTORY_ENABLED
                monitorConfig.integrations.shouldBeNull()
            }
        }

        // The one default that deviates from every other type: a container reached over a local socket does not
        // care whether Kuvasz itself has internet
        `when`("ignoreConnectivityCheck is not set") {

            then("it should default to true, unlike the other monitor types") {
                applicationContext.getBean<DockerMonitorConfig>().ignoreConnectivityCheck shouldBe true
            }
        }
    }
})
