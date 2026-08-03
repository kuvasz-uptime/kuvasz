package com.kuvaszuptime.kuvasz.models.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode

interface DnsResponseCodeMatchers {
    val expectedResponseCode: DnsResponseCode?
    val recordMatchers: List<DnsRecordMatcher>?
}

/**
 * You cannot assert on the records of a name you expect not to resolve, so a non-NOERROR expected response code
 * requires an empty matcher list. Every write path goes through this: the API DTOs and the import adapters via the
 * `@ValidDnsResponseCode` constraint, and the YAML monitor configs on bootstrap, where Micronaut cannot resolve a
 * class-level constraint on an `@EachProperty` bean.
 */
fun DnsResponseCodeMatchers.hasValidResponseCodeExpectation(): Boolean =
    expectedResponseCode == null ||
        expectedResponseCode == DnsResponseCode.NOERROR ||
        recordMatchers.isNullOrEmpty()
