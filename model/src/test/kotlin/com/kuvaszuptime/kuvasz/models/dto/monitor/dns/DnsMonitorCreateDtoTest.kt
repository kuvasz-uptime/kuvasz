package com.kuvaszuptime.kuvasz.models.dto.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.dns.recordMatchersAsList
import com.kuvaszuptime.kuvasz.models.monitor.dns.toMonitorRecord
import com.kuvaszuptime.kuvasz.models.shouldHaveSingleError
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.validation.validator.DefaultValidator

@MicronautTest(startApplication = false)
class DnsMonitorCreateDtoTest(validator: DefaultValidator) : BehaviorSpec({

    given("the validation setup of a DnsMonitorCreateDto") {

        `when`("name is an empty string") {
            val dto = DnsMonitorCreateDto(name = "", host = "example.com", uptimeCheckInterval = 60)

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "name",
                    message = MonitorValidationMessages.NAME_NOT_BLANK
                )
            }
        }

        `when`("host is an empty string") {
            val dto = DnsMonitorCreateDto(name = "Test Monitor", host = "", uptimeCheckInterval = 60)

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "host",
                    message = MonitorValidationMessages.HOST_NOT_BLANK
                )
            }
        }

        `when`("uptimeCheckInterval is less than 5 seconds") {
            val dto = DnsMonitorCreateDto(name = "Test Monitor", host = "example.com", uptimeCheckInterval = 4)

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "uptimeCheckInterval",
                    message = "Uptime check interval must be at least 5 seconds"
                )
            }
        }

        `when`("resolverPort is less than 1") {
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                resolverPort = 0,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "resolverPort",
                    message = "Resolver port must be at least 1"
                )
            }
        }

        `when`("resolverPort is greater than 65535") {
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                resolverPort = 65536,
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "resolverPort",
                    message = "Resolver port must be at most 65535"
                )
            }
        }

        `when`("timeoutMs is greater than 30000") {
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
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
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
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
            val dto = DnsMonitorCreateDto(
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

        `when`("a matcher has a blank value") {
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "  ")),
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "recordMatchers",
                    message = ValidationMessages.VALID_DNS_RECORD_MATCHERS
                )
            }
        }

        `when`("a REGEX matcher has an invalid pattern") {
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.TXT, DnsMatchType.REGEX, "[unclosed")),
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "recordMatchers",
                    message = ValidationMessages.VALID_DNS_RECORD_MATCHERS
                )
            }
        }

        `when`("a REGEX matcher has a valid pattern") {
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.TXT, DnsMatchType.REGEX, "v=spf1.*")),
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }

        `when`("expectedResponseCode is not NOERROR but matchers are configured") {
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                expectedResponseCode = DnsResponseCode.NXDOMAIN,
                recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4")),
            )

            then("bean validation should signal a cross-field error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "",
                    message = MonitorValidationMessages.DNS_RESPONSE_CODE_REQUIRES_NO_MATCHERS
                )
            }
        }

        `when`("expectedResponseCode is NXDOMAIN with no matchers") {
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "does-not-exist.example.com",
                uptimeCheckInterval = 60,
                expectedResponseCode = DnsResponseCode.NXDOMAIN,
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }

        `when`("all fields are valid with a forced-TCP transport and matchers") {
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                transport = DnsTransport.TCP,
                latencyThresholdMs = 500,
                recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.CONTAINS, "1.2.3")),
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }
        `when`("resolverHost is set to a blank string") {
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                resolverHost = "   ",
            )

            then("bean validation should signal an error, since a blank nameserver cannot be resolved") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "resolverHost",
                    message = MonitorValidationMessages.RESOLVER_HOST_NOT_BLANK
                )
            }
        }

        `when`("resolverHost is not set at all") {
            val dto = DnsMonitorCreateDto(name = "Test Monitor", host = "example.com", uptimeCheckInterval = 60)

            then("bean validation should NOT signal an error, because the system resolver is used then") {
                validator.validate(dto).shouldBeEmpty()
            }
        }

        `when`("the same record matcher is listed twice") {
            val matcher = DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4")
            val dto = DnsMonitorCreateDto(
                name = "Test Monitor",
                host = "example.com",
                uptimeCheckInterval = 60,
                recordMatchers = listOf(matcher, matcher),
            )

            then("the duplicate is dropped when the record is built, since it would be evaluated twice") {
                dto.toMonitorRecord(emptySet()).recordMatchersAsList() shouldBe listOf(matcher)
            }
        }
    }
})

class DnsMonitorCreateDtoDefaultsTest : BehaviorSpec({

    given("a DnsMonitorCreateDto with default values") {
        val dto = DnsMonitorCreateDto(name = "Test Monitor", host = "example.com", uptimeCheckInterval = 60)

        then("the default values should be set correctly") {
            dto.enabled shouldBe DnsMonitorDefaults.MONITOR_ENABLED
            dto.resolverHost shouldBe null
            dto.resolverPort shouldBe DnsMonitorDefaults.RESOLVER_PORT
            dto.transport shouldBe DnsTransport.UDP
            dto.recordMatchers shouldBe emptyList()
            dto.expectedResponseCode shouldBe DnsResponseCode.NOERROR
            dto.driftDetectionEnabled shouldBe DnsMonitorDefaults.DRIFT_DETECTION_ENABLED
            dto.timeoutMs shouldBe DnsMonitorDefaults.TIMEOUT_MS
            dto.latencyThresholdMs shouldBe null
            dto.failureCountThreshold shouldBe DnsMonitorDefaults.FAILURE_COUNT_THRESHOLD
            dto.metricsHistoryEnabled shouldBe DnsMonitorDefaults.METRICS_HISTORY_ENABLED
            dto.integrations shouldBe emptyList()
        }
    }

    given("the toMonitorRecord() mapping of the category") {
        val baseDto = DnsMonitorCreateDto(name = "Test Monitor", host = "example.com", uptimeCheckInterval = 60)

        `when`("the category is null, blank or padded with whitespace") {
            then("it should be persisted as null or trimmed") {
                baseDto.copy(category = null).toMonitorRecord(emptySet()).category shouldBe null
                baseDto.copy(category = "   ").toMonitorRecord(emptySet()).category shouldBe null
                baseDto.copy(category = " Core services ").toMonitorRecord(emptySet()).category shouldBe
                    "Core services"
            }
        }
    }
})
