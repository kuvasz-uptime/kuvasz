package com.kuvaszuptime.kuvasz.models.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode

interface DnsResponseCodeMatchers {
    val expectedResponseCode: DnsResponseCode?
    val recordMatchers: List<DnsRecordMatcher>?
}
