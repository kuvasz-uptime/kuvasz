package com.kuvaszuptime.kuvasz.jooq

import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class JsonNodeToMatcherListConverterTest : StringSpec({

    val converter = JsonNodeToMatcherListConverter()
    val mapper = jacksonObjectMapper()

    "from() returns an empty list for a null database value" {
        converter.from(null) shouldBe emptyList()
    }

    "from() deserializes a JSON array of matchers, applying the CONTAINS default" {
        val node = mapper.readValue<tools.jackson.databind.JsonNode>(
            """[{"recordType":"A","value":"1.2.3.4"},{"recordType":"TXT","matchType":"REGEX","value":"v=spf1.*"}]"""
        )

        converter.from(node) shouldBe listOf(
            DnsRecordMatcher(DnsRecordType.A, DnsMatchType.CONTAINS, "1.2.3.4"),
            DnsRecordMatcher(DnsRecordType.TXT, DnsMatchType.REGEX, "v=spf1.*"),
        )
    }

    "to() serializes a list of matchers to a JSON array node" {
        val matchers = listOf(
            DnsRecordMatcher(DnsRecordType.MX, DnsMatchType.EXACT, "10 mail.example.com"),
        )

        val node = converter.to(matchers)

        converter.from(node) shouldBe matchers
    }

    "to() returns an empty array node for a null user value" {
        val node = converter.to(null)

        node.isArray shouldBe true
        node.size() shouldBe 0
    }

    "round-trips an empty list" {
        converter.from(converter.to(emptyList())) shouldBe emptyList()
    }
})
