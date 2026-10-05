package com.kuvaszuptime.kuvasz.services.proxy

import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class ProxyUrlTest : BehaviorSpec({

    given("a supported proxy URL") {

        `when`("it uses the http scheme") {
            val parsed = ProxyUrl.parse("http://10.0.0.10:3128")

            then("it should be parsed into an HTTP proxy") {
                parsed shouldBe Triple(ProxyType.HTTP, "10.0.0.10", 3128)
            }
        }

        `when`("it uses the socks5 scheme in upper case, surrounded by whitespace, with a trailing slash") {
            val parsed = ProxyUrl.parse("  SOCKS5://proxy.example.com:1080/  ")

            then("it should be parsed into a SOCKS5 proxy") {
                parsed shouldBe Triple(ProxyType.SOCKS5, "proxy.example.com", 1080)
            }
        }

        `when`("its host name has an underscore, like a Compose service name") {
            val parsed = ProxyUrl.parse("http://squid_proxy:3128")

            then("it should still be parsed, although java.net.URI does not consider it a host name") {
                parsed shouldBe Triple(ProxyType.HTTP, "squid_proxy", 3128)
            }
        }

        `when`("its host is an IPv6 address") {
            val parsed = ProxyUrl.parse("socks5://[::1]:1080")

            then("it should be parsed") {
                parsed shouldBe Triple(ProxyType.SOCKS5, "[::1]", 1080)
            }
        }
    }

    given("an unsupported proxy URL") {

        `when`("it uses the https scheme") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("https://10.0.0.10:3128") }

            then("it should explain that TLS to the proxy is not supported, but HTTPS targets are") {
                exception.message shouldContain "uses the unsupported scheme [https]"
                exception.message shouldContain "TLS connections to the proxy itself are not supported"
                exception.message shouldContain "Expected one of: [http://, socks5://]"
            }
        }

        `when`("it uses an unknown scheme") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("socks4://10.0.0.10:1080") }

            then("it should list the supported ones") {
                exception.message shouldContain "uses the unsupported scheme [socks4]"
                exception.message shouldContain "Expected one of: [http://, socks5://]"
            }
        }

        `when`("it has no scheme at all") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("10.0.0.10:3128") }

            then("it should be rejected, because a scheme cannot start with a digit") {
                exception.message shouldContain "[10.0.0.10:3128] is not a valid URL"
            }
        }

        `when`("it has no scheme and no port") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("proxy.example.com") }

            then("it should ask for a scheme") {
                exception.message shouldContain "is missing a scheme"
            }
        }

        `when`("it is not a valid URL") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("http://10.0.0.10:3128/a b") }

            then("it should be rejected with the reason") {
                exception.message shouldContain "is not a valid URL"
            }
        }

        `when`("it contains credentials") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("http://user:pass@10.0.0.10:3128") }

            then("it should point to the dedicated properties, without echoing the password back") {
                exception.message shouldContain "Please use the 'username' and 'password' properties instead"
            }
        }

        `when`("it contains credentials and an underscored host name") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("http://user:pass@squid_proxy:3128") }

            then("the credentials should be reported rather than a missing host") {
                exception.message shouldContain "Please use the 'username' and 'password' properties instead"
            }
        }

        `when`("it contains a path") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("http://10.0.0.10:3128/proxy") }

            then("it should be rejected") {
                exception.message shouldContain "should only consist of a scheme, a host and a port"
            }
        }

        `when`("it contains a query") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("http://10.0.0.10:3128?a=b") }

            then("it should be rejected") {
                exception.message shouldContain "should only consist of a scheme, a host and a port"
            }
        }

        `when`("it contains a fragment") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("http://10.0.0.10:3128#a") }

            then("it should be rejected") {
                exception.message shouldContain "should only consist of a scheme, a host and a port"
            }
        }

        `when`("it has no host") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("http://:3128") }

            then("it should be rejected") {
                exception.message shouldContain "does not contain a host name"
            }
        }

        `when`("its port is not numeric, so the authority cannot be parsed into a host") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("http://10.0.0.10:not-a-port") }

            then("it should be rejected") {
                exception.message shouldContain "does not contain a host name"
            }
        }

        `when`("it has no port") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("socks5://10.0.0.10") }

            then("it should be rejected instead of guessing one") {
                exception.message shouldContain "does not contain a port"
            }
        }

        `when`("its port is out of range") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("http://10.0.0.10:99999") }

            then("it should be rejected at startup rather than failing every check") {
                exception.message shouldContain "has an invalid port [99999]"
                exception.message shouldContain "Expected one between 1 and 65535"
            }
        }

        `when`("its underscored host name comes with port 0") {
            val exception = shouldThrow<ProxyConfigException> { ProxyUrl.parse("http://squid_proxy:0") }

            then("the lenient parsing should be range-checked too") {
                exception.message shouldContain "has an invalid port [0]"
            }
        }
    }

    given("the configured credentials of a proxy") {

        `when`("neither the username nor the password is set") {
            then("there should be no credentials") {
                ProxyUrl.resolveCredentials(ProxyType.HTTP, username = null, password = null).shouldBeNull()
            }
        }

        `when`("both of them are blank") {
            then("there should be no credentials") {
                ProxyUrl.resolveCredentials(ProxyType.HTTP, username = " ", password = "").shouldBeNull()
            }
        }

        `when`("both of them are set") {
            val credentials = ProxyUrl.resolveCredentials(ProxyType.HTTP, username = "kuvasz", password = " s3cr3t ")

            then("they should be paired up, keeping the password as is") {
                credentials shouldBe ProxyCredentials(username = "kuvasz", password = " s3cr3t ")
            }

            then("the password should never be printed") {
                credentials.toString() shouldNotContain "s3cr3t"
                credentials.toString() shouldContain "kuvasz"
            }
        }

        `when`("they are too long for a SOCKS5 proxy") {
            val exception = shouldThrow<ProxyConfigException> {
                ProxyUrl.resolveCredentials(ProxyType.SOCKS5, username = "kuvasz", password = "a".repeat(256))
            }

            then("they should be rejected, because RFC 1929 limits both of them to 255 bytes") {
                exception.message shouldContain "can be at most 255 bytes long"
            }
        }

        `when`("they contain non-ASCII characters for a SOCKS5 proxy") {
            val passwordException = shouldThrow<ProxyConfigException> {
                ProxyUrl.resolveCredentials(ProxyType.SOCKS5, username = "kuvasz", password = "jelszó")
            }
            val usernameException = shouldThrow<ProxyConfigException> {
                ProxyUrl.resolveCredentials(ProxyType.SOCKS5, username = "kuvaszü", password = "s3cr3t")
            }

            then("they should be rejected, because the SOCKS5 client of the HTTP checks can only send ASCII") {
                passwordException.message shouldContain "can only contain ASCII characters"
                usernameException.message shouldContain "can only contain ASCII characters"
            }
        }

        `when`("they contain non-ASCII characters for an HTTP proxy") {
            val credentials = ProxyUrl.resolveCredentials(ProxyType.HTTP, username = "kuvasz", password = "jelszó")

            then("they should be accepted, because Basic authentication is sent as UTF-8 by every check") {
                credentials shouldBe ProxyCredentials(username = "kuvasz", password = "jelszó")
            }
        }

        `when`("they are just as long as a SOCKS5 proxy allows") {
            val credentials = ProxyUrl.resolveCredentials(ProxyType.SOCKS5, username = "a".repeat(255), password = "b")

            then("they should be accepted") {
                credentials shouldBe ProxyCredentials(username = "a".repeat(255), password = "b")
            }
        }

        `when`("they are longer than 255 bytes for an HTTP proxy") {
            val credentials = ProxyUrl.resolveCredentials(ProxyType.HTTP, username = "a".repeat(300), password = "b")

            then("they should be accepted, because HTTP has no such limit") {
                credentials shouldBe ProxyCredentials(username = "a".repeat(300), password = "b")
            }
        }

        `when`("only the username is set") {
            val exception = shouldThrow<ProxyConfigException> {
                ProxyUrl.resolveCredentials(ProxyType.HTTP, username = "kuvasz", password = null)
            }

            then("it should be rejected") {
                exception.message shouldContain "They have to be configured together"
            }
        }

        `when`("only the password is set") {
            val exception = shouldThrow<ProxyConfigException> {
                ProxyUrl.resolveCredentials(ProxyType.HTTP, username = null, password = "s3cr3t")
            }

            then("it should be rejected") {
                exception.message shouldContain "They have to be configured together"
            }
        }
    }
})
