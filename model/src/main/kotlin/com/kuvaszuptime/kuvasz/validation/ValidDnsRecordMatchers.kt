package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import jakarta.validation.Constraint

@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [])
annotation class ValidDnsRecordMatchers(
    val message: String = ValidationMessages.VALID_DNS_RECORD_MATCHERS,
)

@Factory
class DnsRecordMatchersValidatorFactory {

    @Singleton
    fun dnsRecordMatchersValidator(): ConstraintValidator<ValidDnsRecordMatchers, List<DnsRecordMatcher>> =
        ConstraintValidator { matchers, _, _ ->
            if (matchers.isNullOrEmpty()) return@ConstraintValidator true
            matchers.all { matcher ->
                matcher.value.isNotBlank() && (matcher.matchType != DnsMatchType.REGEX || matcher.value.isValidRegex())
            }
        }
}

private fun String.isValidRegex(): Boolean =
    try {
        Regex(this)
        true
    } catch (_: IllegalArgumentException) {
        false
    }
