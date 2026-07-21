package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsResponseCodeMatchers
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint

/**
 * Class-level cross-field constraint: you cannot assert on the records of a name you expect not to resolve, so a
 * non-NOERROR expected response code requires an empty matcher list.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Constraint(validatedBy = [])
annotation class ValidDnsResponseCode(
    val message: String = MonitorValidationMessages.DNS_RESPONSE_CODE_REQUIRES_NO_MATCHERS,
)

@Factory
class DnsResponseCodeValidatorFactory {

    @Singleton
    fun dnsResponseCodeValidator(): ConstraintValidator<ValidDnsResponseCode, DnsResponseCodeMatchers> =
        ConstraintValidator { value, _, _ ->
            if (value == null) return@ConstraintValidator true
            val expectedResponseCode = value.expectedResponseCode
            expectedResponseCode == null
                || expectedResponseCode == DnsResponseCode.NOERROR
                || value.recordMatchers.isNullOrEmpty()
        }
}
