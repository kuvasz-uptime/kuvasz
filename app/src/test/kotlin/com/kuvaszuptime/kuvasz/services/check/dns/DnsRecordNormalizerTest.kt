package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMatchType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.CAARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.MXRecord
import org.xbill.DNS.NSRecord
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Record
import org.xbill.DNS.SOARecord
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.TXTRecord
import java.net.InetAddress

private const val TTL = 300L
private val ORIGIN = Name.fromConstantString("example.com.")
private fun name(fqdn: String) = Name.fromConstantString(fqdn)
private fun ip(value: String) = InetAddress.getByName(value)
private fun a(value: String) = ARecord(ORIGIN, DClass.IN, TTL, ip(value))
private fun aaaa(value: String) = AAAARecord(ORIGIN, DClass.IN, TTL, ip(value))
private fun cname(target: String) = CNAMERecord(ORIGIN, DClass.IN, TTL, name(target))
private fun ns(target: String) = NSRecord(ORIGIN, DClass.IN, TTL, name(target))
private fun ptr(target: String) = PTRRecord(ORIGIN, DClass.IN, TTL, name(target))
private fun mx(priority: Int, target: String) = MXRecord(ORIGIN, DClass.IN, TTL, priority, name(target))
private fun caa(flags: Int, tag: String, value: String) = CAARecord(ORIGIN, DClass.IN, TTL, flags, tag, value)
private fun txt(vararg chunks: String) = TXTRecord(ORIGIN, DClass.IN, TTL, chunks.toList())
private fun srv(priority: Int, weight: Int, port: Int, target: String) =
    SRVRecord(ORIGIN, DClass.IN, TTL, priority, weight, port, name(target))
private fun soa() = SOARecord(
    ORIGIN, DClass.IN, TTL,
    name("ns.example.com."), name("hostmaster.example.com."),
    2024010101L, 7200L, 3600L, 1209600L, 3600L,
)
private const val SOA_NORMALIZED = "ns.example.com. hostmaster.example.com. 2024010101 7200 3600 1209600 3600"

class DnsRecordNormalizerTest : BehaviorSpec({

    given("DnsRecordNormalizer.normalize across record types") {

        // record -> expected canonical string
        val cases: List<Triple<String, Record, String>> = listOf(
            Triple("A", a("93.184.216.34"), "93.184.216.34"),
            Triple("AAAA lowercased", aaaa("2001:DB8::AbCd"), "2001:db8:0:0:0:0:0:abcd"),
            Triple("CNAME strips dot + lowercases", cname("Target.Example.COM."), "target.example.com"),
            Triple("NS", ns("ns1.example.com."), "ns1.example.com"),
            Triple("PTR", ptr("example.com."), "example.com"),
            Triple("MX", mx(10, "mail.example.com."), "10 mail.example.com"),
            Triple("SRV", srv(10, 60, 5060, "sip.example.com."), "10 60 5060 sip.example.com"),
            Triple("CAA drops the value quotes", caa(0, "issue", "letsencrypt.org"), "0 issue letsencrypt.org"),
            Triple("CAA critical + issuewild", caa(128, "issuewild", "sectigo.com"), "128 issuewild sectigo.com"),
            Triple("MX with priority 0", mx(0, "mail.example.com."), "0 mail.example.com"),
            Triple("SRV with weight 0", srv(5, 0, 443, "svc.example.com."), "5 0 443 svc.example.com"),
            Triple("SOA keeps the interior name dots", soa(), SOA_NORMALIZED),
            Triple("TXT single chunk", txt("v=spf1 ~all"), "v=spf1 ~all"),
            Triple("TXT collapses internal whitespace", txt("v=spf1    ~all"), "v=spf1 ~all"),
        )

        cases.forEach { (label, record, expected) ->
            `when`("normalizing a $label record") {
                then("it produces '$expected'") {
                    DnsRecordNormalizer.normalize(record) shouldBe expected
                }
            }
        }

        `when`("normalizing a TXT record split into >255-byte chunks") {
            val fullValue = "v=DKIM1; k=rsa; p=" + "A".repeat(400)
            val record = txt(*fullValue.chunked(255).toTypedArray())

            then("it rejoins the chunks into one logical, lowercased value") {
                DnsRecordNormalizer.normalize(record) shouldBe fullValue.lowercase()
            }
        }
    }

    given("DnsRecordNormalizer.normalizeValue") {

        // raw input -> expected canonical string
        val cases = listOf(
            "\"mail.example.com.\"" to "mail.example.com",
            "  Mail.Example.COM.  " to "mail.example.com",
            "10   mail.example.com" to "10 mail.example.com",
            "10\tmail.example.com" to "10 mail.example.com",
            "letsencrypt.org" to "letsencrypt.org",
            "\"v=spf1 ~all\"" to "v=spf1 ~all",
            "v=\"spf1\"" to "v=spf1",
            "" to "",
            "\"\"" to "",
        )

        cases.forEach { (raw, expected) ->
            `when`("canonicalizing '$raw'") {
                then("it produces '$expected'") {
                    DnsRecordNormalizer.normalizeValue(raw) shouldBe expected
                }
            }
        }
    }

    given("record and matcher-value normalization are symmetric") {

        `when`("a dig-style MX value is compared to the resolved record") {
            val record = mx(10, "mail.example.com.")

            then("the two normalize to the same string") {
                DnsRecordNormalizer.normalizeValue("10 mail.example.com.") shouldBe
                    DnsRecordNormalizer.normalize(record)
            }
        }

        `when`("a dig-style SOA value with trailing dots is compared to the resolved record") {
            then("the two normalize to the same string") {
                DnsRecordNormalizer.normalizeValue(SOA_NORMALIZED) shouldBe DnsRecordNormalizer.normalize(soa())
            }
        }
    }

    given("DnsRecordNormalizer.evaluate") {

        val records = mapOf(
            DnsRecordType.A to listOf("1.2.3.4", "5.6.7.8"),
            DnsRecordType.TXT to listOf("v=spf1 include:_spf.example.com ~all"),
            DnsRecordType.MX to listOf("10 mail.example.com", "20 backup.example.com"),
        )

        `when`("no matchers are configured") {
            then("the assertion passes with no failures") {
                val result = DnsRecordNormalizer.evaluate(emptyList(), records)
                result.matched.shouldBeTrue()
                result.failedMatchers.shouldBeEmpty()
            }
        }

        `when`("an EXACT matcher targets one of several records of its type") {
            val matchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "5.6.7.8"))

            then("it passes because any record of the type may satisfy it") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeTrue()
            }
        }

        `when`("an EXACT matcher uses a dig-style trailing dot") {
            val matchers = listOf(DnsRecordMatcher(DnsRecordType.MX, DnsMatchType.EXACT, "10 mail.example.com."))

            then("the trailing dot is normalized away and it matches") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeTrue()
            }
        }

        `when`("two EXACT matchers require both values to be present (contains-all)") {
            val matchers = listOf(
                DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4"),
                DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "5.6.7.8"),
            )

            then("it passes only when every required value is present") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeTrue()
            }
        }

        `when`("one of several ANDed matchers fails") {
            val ok = DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4")
            val bad = DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "9.9.9.9")

            then("the whole assertion fails and names only the failing matcher") {
                val result = DnsRecordNormalizer.evaluate(listOf(ok, bad), records)
                result.matched.shouldBeFalse()
                result.failedMatchers.shouldContainExactly(bad)
            }
        }

        `when`("a CONTAINS matcher looks for a substring") {
            val matchers = listOf(
                DnsRecordMatcher(DnsRecordType.TXT, DnsMatchType.CONTAINS, "include:_spf.example.com"),
            )

            then("it passes when a record contains the value") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeTrue()
            }
        }

        `when`("a CONTAINS matcher looks for an absent substring") {
            val matchers = listOf(DnsRecordMatcher(DnsRecordType.TXT, DnsMatchType.CONTAINS, "spf2"))

            then("the assertion fails") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeFalse()
            }
        }

        `when`("a REGEX matcher is used with mixed case") {
            val matchers = listOf(DnsRecordMatcher(DnsRecordType.TXT, DnsMatchType.REGEX, "V=SPF1.*~ALL"))

            then("matching is case-insensitive and it passes") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeTrue()
            }
        }

        `when`("a REGEX matcher does not match any record") {
            val matchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.REGEX, "^10\\..*"))

            then("the assertion fails") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeFalse()
            }
        }

        `when`("matchers of different types are ANDed and all pass") {
            val matchers = listOf(
                DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4"),
                DnsRecordMatcher(DnsRecordType.MX, DnsMatchType.CONTAINS, "mail.example.com"),
            )

            then("the assertion passes") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeTrue()
            }
        }

        `when`("a matcher targets a record type with no records in the answer") {
            val matchers = listOf(DnsRecordMatcher(DnsRecordType.AAAA, DnsMatchType.CONTAINS, "::1"))

            then("the assertion fails") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeFalse()
            }
        }

        `when`("an EXACT matcher uses different casing than the record") {
            val matchers = listOf(DnsRecordMatcher(DnsRecordType.MX, DnsMatchType.EXACT, "10 MAIL.EXAMPLE.COM"))

            then("matching is case-insensitive and it passes") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeTrue()
            }
        }

        `when`("an EXACT matcher value carries surrounding whitespace") {
            val matchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "  1.2.3.4  "))

            then("the value is canonicalized and it matches") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeTrue()
            }
        }

        `when`("several matchers fail") {
            val bad1 = DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "9.9.9.9")
            val bad2 = DnsRecordMatcher(DnsRecordType.TXT, DnsMatchType.CONTAINS, "missing")
            val good = DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3.4")

            then("every failing matcher is reported, in order, and the passing one is not") {
                val result = DnsRecordNormalizer.evaluate(listOf(bad1, good, bad2), records)
                result.matched.shouldBeFalse()
                result.failedMatchers.shouldContainExactly(bad1, bad2)
            }
        }

        `when`("an EXACT matcher matches none of several records of its type") {
            val matchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.EXACT, "1.2.3"))

            then("the assertion fails because EXACT requires a full match") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeFalse()
            }
        }

        `when`("an anchored REGEX matcher matches a record") {
            val matchers = listOf(DnsRecordMatcher(DnsRecordType.A, DnsMatchType.REGEX, "^5\\.6\\.7\\.8$"))

            then("the assertion passes") {
                DnsRecordNormalizer.evaluate(matchers, records).matched.shouldBeTrue()
            }
        }
    }
})
