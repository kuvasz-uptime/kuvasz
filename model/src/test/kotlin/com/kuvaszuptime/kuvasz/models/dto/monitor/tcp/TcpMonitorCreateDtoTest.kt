package com.kuvaszuptime.kuvasz.models.dto.monitor.tcp

import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.shouldHaveSingleError
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.validation.validator.DefaultValidator

@MicronautTest(startApplication = false)
class TcpMonitorCreateDtoTest(validator: DefaultValidator) : BehaviorSpec({

    given("the validation setup of a TcpMonitorCreateDto") {

        `when`("name is an empty string") {
            val dto = TcpMonitorCreateDto(
                name = "",
                host = "example.com",
                port = 8080,
                uptimeCheckInterval = 60,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "name",
                    message = MonitorValidationMessages.NAME_NOT_BLANK
                )
            }
        }

        `when`("host is an empty string") {
            val dto = TcpMonitorCreateDto(
                name = "Test Monitor",
                host = "",
                port = 8080,
                uptimeCheckInterval = 60,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "host",
                    message = MonitorValidationMessages.HOST_NOT_BLANK
                )
            }
        }

        `when`("uptimeCheckInterval is less than 5 seconds") {
            val dto = TcpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                port = 8080,
                uptimeCheckInterval = 4,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "uptimeCheckInterval",
                    message = "Uptime check interval must be at least 5 seconds"
                )
            }
        }

        `when`("port is less than 1") {
            val dto = TcpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                port = 0,
                uptimeCheckInterval = 60,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "port",
                    message = "Port must be at least 1"
                )
            }
        }

        `when`("port is greater than 65535") {
            val dto = TcpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                port = 65536,
                uptimeCheckInterval = 60,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "port",
                    message = "Port must be at most 65535"
                )
            }
        }

        `when`("timeoutMs is less than 1") {
            val dto = TcpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                port = 8080,
                uptimeCheckInterval = 60,
                timeoutMs = 0,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "timeoutMs",
                    message = "Timeout must be at least 1 millisecond(s)"
                )
            }
        }

        `when`("timeoutMs is greater than 30000") {
            val dto = TcpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                port = 8080,
                uptimeCheckInterval = 60,
                timeoutMs = 30001,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "timeoutMs",
                    message = "Timeout must be at most 30000 milliseconds"
                )
            }
        }

        `when`("latencyThresholdMs is less than 1") {
            val dto = TcpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                port = 8080,
                uptimeCheckInterval = 60,
                latencyThresholdMs = 0,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "latencyThresholdMs",
                    message = "Latency threshold must be at least 1 millisecond(s)"
                )
            }
        }

        `when`("failureCountThreshold is 0") {
            val dto = TcpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                port = 8080,
                uptimeCheckInterval = 60,
                failureCountThreshold = 0L,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "failureCountThreshold",
                    message = MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE
                )
            }
        }

        `when`("failureCountThreshold is negative") {
            val dto = TcpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                port = 8080,
                uptimeCheckInterval = 60,
                failureCountThreshold = -1L,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "failureCountThreshold",
                    message = MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE
                )
            }
        }

        `when`("all fields are valid") {
            val dto = TcpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                port = 8080,
                uptimeCheckInterval = 60,
                latencyThresholdMs = 500,
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }

        `when`("the optional latencyThresholdMs is null") {
            val dto = TcpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                port = 8080,
                uptimeCheckInterval = 60,
                latencyThresholdMs = null,
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }
    }
})

class TcpMonitorCreateDtoDefaultsTest : BehaviorSpec({

    given("a TcpMonitorCreateDto with default values") {
        val dto = TcpMonitorCreateDto(
            name = "Test Monitor",
            host = "example.com",
            port = 8080,
            uptimeCheckInterval = 60,
        )

        then("the default values should be set correctly") {
            dto.enabled shouldBe TcpMonitorDefaults.MONITOR_ENABLED
            dto.timeoutMs shouldBe TcpMonitorDefaults.TIMEOUT_MS
            dto.latencyThresholdMs shouldBe null
            dto.failureCountThreshold shouldBe TcpMonitorDefaults.FAILURE_COUNT_THRESHOLD
            dto.metricsHistoryEnabled shouldBe TcpMonitorDefaults.METRICS_HISTORY_ENABLED
            dto.integrations shouldBe emptyList()
        }
    }
})
