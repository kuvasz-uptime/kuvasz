package com.kuvaszuptime.kuvasz.jooq

import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TextArrayToDnsRecordTypeArrayConverterTest : StringSpec({

    val converter = TextArrayToDnsRecordTypeArrayConverter()

    "from() returns an empty array for a null database value" {
        converter.from(null).toList() shouldBe emptyList()
    }

    "from() maps the stored names to record types" {
        converter.from(arrayOf("NS", "MX")).toList() shouldBe listOf(DnsRecordType.NS, DnsRecordType.MX)
    }

    "from() drops unknown values instead of failing the whole lookup" {
        converter.from(arrayOf("NS", "NOT_A_RECORD_TYPE")).toList() shouldBe listOf(DnsRecordType.NS)
    }

    "to() serializes the record types to their names" {
        converter.to(arrayOf(DnsRecordType.TXT, DnsRecordType.CAA)).toList() shouldBe listOf("TXT", "CAA")
    }

    "to() returns an empty array for a null user value" {
        converter.to(null).toList() shouldBe emptyList()
    }

    "round-trips a list of record types" {
        val types = arrayOf(DnsRecordType.A, DnsRecordType.AAAA, DnsRecordType.SRV)

        converter.from(converter.to(types)).toList() shouldBe types.toList()
    }
})
