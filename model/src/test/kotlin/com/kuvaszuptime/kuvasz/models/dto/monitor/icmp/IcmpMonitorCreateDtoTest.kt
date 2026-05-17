package com.kuvaszuptime.kuvasz.models.dto.monitor.icmp

import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.shouldHaveSingleError
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.validation.validator.DefaultValidator

@MicronautTest(startApplication = false)
class IcmpMonitorCreateDtoTest(validator: DefaultValidator) : BehaviorSpec({

    given("the validation setup of an IcmpMonitorCreateDto") {

        `when`("name is an empty string") {
            val dto = IcmpMonitorCreateDto(
                name = "",
                host = "example.com",
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
            val dto = IcmpMonitorCreateDto(
                name = "Test Monitor",
                host = "",
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
            val dto = IcmpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 4,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "uptimeCheckInterval",
                    message = "Uptime check interval must be at least 5 seconds"
                )
            }
        }

        `when`("packetCount is less than 1") {
            val dto = IcmpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                packetCount = 0,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "packetCount",
                    message = "Packet count must be at least 1"
                )
            }
        }

        `when`("packetCount is greater than 10") {
            val dto = IcmpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                packetCount = 11,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "packetCount",
                    message = "Packet count must be at most 10"
                )
            }
        }

        `when`("timeoutSeconds is less than 1") {
            val dto = IcmpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                timeoutSeconds = 0,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "timeoutSeconds",
                    message = "Timeout must be at least 1 second(s)"
                )
            }
        }

        `when`("timeoutSeconds is greater than 30") {
            val dto = IcmpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                timeoutSeconds = 31,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "timeoutSeconds",
                    message = "Timeout must be at most 30 seconds"
                )
            }
        }

        `when`("packetLossThreshold is less than 1") {
            val dto = IcmpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                packetLossThreshold = 0,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "packetLossThreshold",
                    message = "Packet loss threshold must be at least 1%"
                )
            }
        }

        `when`("packetLossThreshold is greater than 100") {
            val dto = IcmpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                packetLossThreshold = 101,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "packetLossThreshold",
                    message = "Packet loss threshold must be at most 100%"
                )
            }
        }

        `when`("failureCountThreshold is 0") {
            val dto = IcmpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
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
            val dto = IcmpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
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
            val dto = IcmpMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }
    }
})

class IcmpMonitorCreateDtoDefaultsTest : BehaviorSpec({

    given("an IcmpMonitorCreateDto with default values") {
        val dto = IcmpMonitorCreateDto(
            name = "Test Monitor",
            host = "example.com",
            uptimeCheckInterval = 60,
        )

        then("the default values should be set correctly") {
            dto.enabled shouldBe IcmpMonitorDefaults.MONITOR_ENABLED
            dto.packetCount shouldBe IcmpMonitorDefaults.PACKET_COUNT
            dto.timeoutSeconds shouldBe IcmpMonitorDefaults.TIMEOUT_SECONDS
            dto.packetLossThreshold shouldBe IcmpMonitorDefaults.PACKET_LOSS_THRESHOLD
            dto.failureCountThreshold shouldBe IcmpMonitorDefaults.FAILURE_COUNT_THRESHOLD
            dto.metricsHistoryEnabled shouldBe IcmpMonitorDefaults.METRICS_HISTORY_ENABLED
            dto.integrations shouldBe emptyList()
        }
    }
})
