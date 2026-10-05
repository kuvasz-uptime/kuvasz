package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyType
import com.kuvaszuptime.kuvasz.services.proxy.ConfiguredProxy
import com.kuvaszuptime.kuvasz.services.proxy.ProxyCredentials
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.TestConnectProxy
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.string.shouldContain
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.runtime.ApplicationConfiguration
import kotlinx.coroutines.reactive.awaitSingle
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.Optional
import java.util.OptionalInt

private const val USERNAME = "kuvasz"
private const val PASSWORD = "s3cr3t"

// The very same exchange the uptime checker does
private suspend fun HttpClient.check(url: String): HttpResponse<ByteBuffer<*>> = exchange(
    HttpRequest.GET(url),
    Argument.of(ByteBuffer::class.java),
    Argument.of(ByteBuffer::class.java),
).awaitSingle()

private fun HttpResponse<ByteBuffer<*>>.text(): String? = body()?.toString(Charsets.UTF_8)

class ProxiedHttpClientRegistryTest : BehaviorSpec({

    val target = ClientAndServer.startClientAndServer(0)
    target.`when`(request().withPath("/hello.*")).respond(response().withStatusCode(200).withBody("hi"))
    target.`when`(request().withPath("/redirect"))
        .respond(response().withStatusCode(302).withHeader("Location", "/hello"))
    target.`when`(request().withPath("/error")).respond(response().withStatusCode(500))
    val targetUrl = "http://localhost:${target.port}"

    val connectProxy = TestConnectProxy()
    val authConnectProxy = TestConnectProxy(USERNAME, PASSWORD)
    // MockServer only blocks forwarding to private networks, like localhost, when told to through its config
    val socksProxy = ClientAndServer.startClientAndServer(
        org.mockserver.configuration.Configuration.configuration().forwardProxyBlockPrivateNetworks(false),
        0,
    )
    val closedPort = ServerSocket(0).use { it.localPort }

    afterSpec {
        target.stop()
        socksProxy.stop()
        connectProxy.close()
        authConnectProxy.close()
    }

    given("a set of configured proxies") {

        val ctx = testAppContext(
            mapOf(
                "proxies" to listOf(
                    mapOf("name" to "connect", "url" to "http://localhost:${connectProxy.port}"),
                    mapOf(
                        "name" to "connect-auth",
                        "url" to "http://localhost:${authConnectProxy.port}",
                        "username" to USERNAME,
                        "password" to PASSWORD,
                    ),
                    mapOf(
                        "name" to "connect-wrong-auth",
                        "url" to "http://localhost:${authConnectProxy.port}",
                        "username" to USERNAME,
                        "password" to "wrong",
                    ),
                    mapOf("name" to "socks", "url" to "socks5://localhost:${socksProxy.port}"),
                    mapOf("name" to "unreachable", "url" to "http://localhost:$closedPort"),
                    mapOf("name" to "unresolvable", "url" to "http://proxy.invalid:3128"),
                )
            )
        )
        val registry = ctx.getBean<ProxiedHttpClientRegistry>()

        `when`("a target is checked through an HTTP proxy") {
            val response = registry.clientFor("connect").shouldNotBeNull().check("$targetUrl/hello-connect")

            then("the response of the target should arrive through a tunnel of the proxy") {
                response.status shouldBe HttpStatus.OK
                response.text() shouldBe "hi"
                connectProxy.tunnels shouldContain "localhost:${target.port}"
            }
        }

        `when`("a target is checked through an HTTP proxy that requires authentication") {
            val response = registry.clientFor("connect-auth").shouldNotBeNull().check("$targetUrl/hello-auth")

            then("the configured credentials should open the tunnel") {
                response.text() shouldBe "hi"
                authConnectProxy.tunnels shouldContain "localhost:${target.port}"
            }
        }

        `when`("a target is checked through an HTTP proxy with the wrong credentials") {
            val exception = shouldThrow<HttpClientException> {
                registry.clientFor("connect-wrong-auth").shouldNotBeNull().check("$targetUrl/hello-wrong-auth")
            }

            then("the check should fail with the status of the proxy") {
                exception.message shouldContain "407"
            }
        }

        `when`("a target is checked through a SOCKS5 proxy") {
            val response = registry.clientFor("socks").shouldNotBeNull().check("$targetUrl/hello-socks")

            then("the response of the target should arrive through the proxy") {
                response.text() shouldBe "hi"
                socksProxy.retrieveRecordedRequests(request().withPath("/hello-socks")).toList() shouldHaveSize 1
            }
        }

        `when`("a target is checked through a proxy that can't be reached") {
            then("the check should fail instead of falling back to a direct connection") {
                shouldThrow<HttpClientException> {
                    registry.clientFor("unreachable").shouldNotBeNull().check("$targetUrl/hello-unreachable")
                }
                target.retrieveRecordedRequests(request().withPath("/hello-unreachable")).toList() shouldHaveSize 0
            }
        }

        `when`("a target is checked through a proxy whose name can't be resolved") {
            then("the check should fail, instead of falling back to a direct connection") {
                shouldThrow<HttpClientException> {
                    registry.clientFor("unresolvable").shouldNotBeNull().check("$targetUrl/hello-unresolvable")
                }
                target.retrieveRecordedRequests(request().withPath("/hello-unresolvable")).toList() shouldHaveSize 0
            }
        }

        `when`("the target redirects") {
            val response = registry.clientFor("connect").shouldNotBeNull().check("$targetUrl/redirect")

            then("the redirect should not be followed, like with the direct client") {
                response.status shouldBe HttpStatus.FOUND
            }
        }

        `when`("the target fails") {
            val response = registry.clientFor("socks").shouldNotBeNull().check("$targetUrl/error")

            then("the error status should be returned instead of thrown, like with the direct client") {
                response.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
            }
        }

        `when`("the clients are looked up") {
            then("every proxy should have its own client") {
                registry.clientFor("connect") shouldNotBe registry.clientFor("socks")
            }

            then("a proxy should keep its client across lookups") {
                registry.clientFor("connect") shouldBeSameInstanceAs registry.clientFor("connect")
            }

            then("there should be no client for an unknown proxy") {
                registry.clientFor("nope").shouldBeNull()
            }
        }

        `when`("the registry is closed") {
            val client = registry.clientFor("connect").shouldNotBeNull()
            ctx.close()

            then("its clients should be closed too") {
                client.isRunning shouldBe false
            }
        }
    }

    given("no configured proxies") {

        val ctx = testAppContext()
        val registry = ctx.getBean<ProxiedHttpClientRegistry>()

        `when`("a client is looked up") {
            then("there should be none") {
                registry.clientFor("connect").shouldBeNull()
            }
        }
    }

    given("the configuration of a proxied client") {

        val ctx = testAppContext()
        val proxy = ConfiguredProxy(
            name = "socks",
            type = ProxyType.SOCKS5,
            host = "proxy.example.com",
            port = 1080,
            credentials = ProxyCredentials(USERNAME, PASSWORD),
        )
        val config = ProxiedHttpCheckerClientConfiguration(
            config = ctx.getBean<ApplicationConfiguration>(),
            appConfig = ctx.getBean<AppConfig>(),
            proxy = proxy,
            numOfThreads = 3,
        )

        `when`("it is built") {
            then("it should route through the proxy, without resolving its name upfront") {
                config.proxyType shouldBe Proxy.Type.SOCKS
                config.proxyAddress shouldBe Optional.of(InetSocketAddress.createUnresolved("proxy.example.com", 1080))
                config.proxyUsername shouldBe Optional.of(USERNAME)
                config.proxyPassword shouldBe Optional.of(PASSWORD)
                config.numOfThreads shouldBe OptionalInt.of(3)
            }

            then("it should keep every other setting of the direct client") {
                val direct = ctx.getBean<HttpCheckerClientConfiguration>()
                config.isFollowRedirects shouldBe direct.isFollowRedirects
                config.isExceptionOnErrorStatus shouldBe direct.isExceptionOnErrorStatus
                config.readTimeout shouldBe direct.readTimeout
                config.eventLoopGroup shouldBe direct.eventLoopGroup
            }
        }

        `when`("the proxy is an HTTP proxy without credentials") {
            val httpConfig = ProxiedHttpCheckerClientConfiguration(
                config = ctx.getBean<ApplicationConfiguration>(),
                appConfig = ctx.getBean<AppConfig>(),
                proxy = proxy.copy(type = ProxyType.HTTP, credentials = null),
                numOfThreads = 3,
            )

            then("it should be an HTTP proxy, without credentials") {
                httpConfig.proxyType shouldBe Proxy.Type.HTTP
                httpConfig.proxyUsername shouldBe Optional.empty()
                httpConfig.proxyPassword shouldBe Optional.empty()
            }
        }
    }
})
