package com.kuvaszuptime.kuvasz.services.proxy

import com.kuvaszuptime.kuvasz.models.dto.ProxyValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyDto
import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyType
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.context.exceptions.BeanInstantiationException

class ProxyRegistryTest : BehaviorSpec({

    given("a valid proxies configuration") {

        val ctx = testAppContext(
            mapOf(
                "proxies" to listOf(
                    mapOf("name" to " office-network ", "url" to "socks5://127.0.0.1:1080"),
                    mapOf(
                        "name" to "corporate-egress",
                        "url" to "http://10.0.0.10:3128",
                        "username" to "kuvasz",
                        "password" to "s3cr3t",
                    ),
                )
            )
        )
        val registry = ctx.getBean<ProxyRegistry>()

        `when`("the proxies are resolved") {

            then("the authenticated HTTP proxy should carry its credentials") {
                registry["corporate-egress"] shouldBe ConfiguredProxy(
                    name = "corporate-egress",
                    type = ProxyType.HTTP,
                    host = "10.0.0.10",
                    port = 3128,
                    credentials = ProxyCredentials(username = "kuvasz", password = "s3cr3t"),
                )
            }

            then("the anonymous SOCKS5 proxy should be registered under its trimmed name") {
                registry["office-network"] shouldBe ConfiguredProxy(
                    name = "office-network",
                    type = ProxyType.SOCKS5,
                    host = "127.0.0.1",
                    port = 1080,
                    credentials = null,
                )
            }

            then("an unknown proxy should not be found") {
                registry["nope"].shouldBeNull()
            }

            then("the DTOs should expose whether a proxy is authenticated, but never the credentials, sorted by name") {
                registry.getProxyDtos() shouldContainExactly listOf(
                    ProxyDto(
                        name = "corporate-egress",
                        type = ProxyType.HTTP,
                        host = "10.0.0.10",
                        port = 3128,
                        authenticated = true,
                    ),
                    ProxyDto(
                        name = "office-network",
                        type = ProxyType.SOCKS5,
                        host = "127.0.0.1",
                        port = 1080,
                        authenticated = false,
                    ),
                )
            }
        }
    }

    given("no proxies configuration at all") {

        val ctx = testAppContext()

        `when`("the registry is looked up") {
            val registry = ctx.getBean<ProxyRegistry>()

            then("it should exist, but hold no proxies at all") {
                registry.configuredProxies.shouldBeEmpty()
                registry.getProxyDtos().shouldBeEmpty()
                registry["corporate-egress"].shouldBeNull()
            }
        }
    }

    given("an invalid proxies configuration") {

        `when`("two proxies share the same name") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(
                    mapOf(
                        "proxies" to listOf(
                            mapOf("name" to "egress", "url" to "http://10.0.0.10:3128"),
                            mapOf("name" to "egress ", "url" to "socks5://10.0.0.11:1080"),
                        )
                    )
                )
            }

            then("the application should not start") {
                exception.message shouldContain "Duplicate proxy configuration found for [egress]"
            }
        }

        `when`("a proxy name is blank") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(mapOf("proxies" to listOf(mapOf("name" to "", "url" to "http://10.0.0.10:3128"))))
            }

            then("the application should not start") {
                exception.message shouldContain ProxyValidationMessages.NAME_NOT_BLANK
            }
        }

        `when`("a proxy URL is blank") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(mapOf("proxies" to listOf(mapOf("name" to "egress", "url" to ""))))
            }

            then("the application should not start") {
                exception.message shouldContain ProxyValidationMessages.URL_NOT_BLANK
            }
        }

        `when`("a proxy URL uses the https scheme") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(mapOf("proxies" to listOf(mapOf("name" to "egress", "url" to "https://10.0.0.10:3128"))))
            }

            then("the failure should name the offending proxy and explain why") {
                exception.message shouldContain "Invalid configuration for proxy [egress]"
                exception.message shouldContain "TLS connections to the proxy itself are not supported"
            }
        }

        `when`("only the password of a proxy is configured") {
            val exception = shouldThrow<BeanInstantiationException> {
                testAppContext(
                    mapOf(
                        "proxies" to listOf(
                            mapOf("name" to "egress", "url" to "http://10.0.0.10:3128", "password" to "s3cr3t"),
                        )
                    )
                )
            }

            then("the failure should name the offending proxy, without leaking the password") {
                exception.message shouldContain "Invalid configuration for proxy [egress]"
                exception.message shouldContain "They have to be configured together"
                exception.message shouldNotContain "s3cr3t"
            }
        }
    }
})
