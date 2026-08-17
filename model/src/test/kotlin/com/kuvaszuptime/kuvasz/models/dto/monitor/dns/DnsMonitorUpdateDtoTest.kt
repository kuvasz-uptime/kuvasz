package com.kuvaszuptime.kuvasz.models.dto.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.shouldHaveSingleError
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.validation.validator.DefaultValidator

@MicronautTest(startApplication = false)
class DnsMonitorUpdateDtoTest(validator: DefaultValidator) : BehaviorSpec({

    fun validUpdateDto(
        expectedResponseCode: DnsResponseCode = DnsResponseCode.NOERROR,
        recordMatchers: List<DnsRecordMatcher> = emptyList(),
    ) = DnsMonitorUpdateDto(
        name = "Test Monitor",
        host = "example.com",
        resolverHost = null,
        resolverPort = 53,
        transport = DnsTransport.UDP,
        recordMatchers = recordMatchers,
        expectedResponseCode = expectedResponseCode,
        driftDetectionEnabled = false,
        driftRecordTypes = emptyList(),
        uptimeCheckInterval = 60,
        timeoutMs = 5000,
        latencyThresholdMs = null,
        failureCountThreshold = 1L,
        enabled = true,
        integrations = null,
        metricsHistoryEnabled = true,
        category = null,
    )

    given("the validation setup of a DnsMonitorUpdateDto") {

        `when`("host is an empty string") {
            val dto = validUpdateDto().copy(host = "")

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "host",
                    message = MonitorValidationMessages.HOST_NOT_BLANK
                )
            }
        }

        `when`("a REGEX matcher has an invalid pattern") {
            val dto = validUpdateDto(
                recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.TXT, DnsMatchType.REGEX, "[unclosed"))
            )

            then("bean validation should signal an error") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "recordMatchers",
                    message = ValidationMessages.VALID_DNS_RECORD_MATCHERS
                )
            }
        }

        `when`("expectedResponseCode is not NOERROR but matchers are configured") {
            val dto = validUpdateDto(
                expectedResponseCode = DnsResponseCode.NXDOMAIN,
                recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4")),
            )

            then("the class-level cross-field validator should kick in on the update DTO") {
                validator.validate(dto).shouldHaveSingleError(
                    propertyPath = "",
                    message = MonitorValidationMessages.DNS_RESPONSE_CODE_REQUIRES_NO_MATCHERS
                )
            }
        }

        `when`("expectedResponseCode is NXDOMAIN with no matchers") {
            val dto = validUpdateDto(expectedResponseCode = DnsResponseCode.NXDOMAIN)

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }

        `when`("expectedResponseCode is NOERROR with matchers") {
            val dto = validUpdateDto(
                recordMatchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.CONTAINS, "1.2.3")),
            )

            then("bean validation should NOT signal an error") {
                validator.validate(dto).shouldBeEmpty()
            }
        }
    }
})
