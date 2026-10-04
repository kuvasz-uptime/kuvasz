package com.kuvaszuptime.kuvasz.services.proxy

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.resetDatabase
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.services.check.http.HttpUptimeChecker
import com.kuvaszuptime.kuvasz.services.check.ssl.SSLChecker
import com.kuvaszuptime.kuvasz.testAppContext
import com.kuvaszuptime.kuvasz.testutils.KGenericContainer
import com.kuvaszuptime.kuvasz.testutils.TestCertificateAuthority
import com.kuvaszuptime.kuvasz.testutils.getBean
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jooq.DSLContext
import org.mockserver.client.MockServerClient
import org.mockserver.model.Delay
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.MountableFile
import tools.jackson.databind.node.JsonNodeFactory
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.TimeUnit

private const val MOCKSERVER_IMAGE = "mockserver/mockserver:8.0.0"
private const val NGINX_IMAGE = "nginx:1.30.4-alpine"
private const val SQUID_IMAGE = "ubuntu/squid:6.6-24.04_edge"
private const val THREE_PROXY_IMAGE = "ghcr.io/tarampampam/3proxy:2.3.0"

// Neither name resolves outside the Docker network of the spec, so these targets can only be reached through a proxy
private const val HTTP_TARGET = "internal-http"
private const val HTTPS_TARGET = "internal-https"
private const val HTTP_TARGET_PORT = 1080
private const val HTTPS_PORT = 443
private const val EXPIRED_HTTPS_PORT = 8443
private const val WRONG_HOST_HTTPS_PORT = 9443
private const val SQUID_PORT = 3128
private const val THREE_PROXY_HTTP_PORT = 3128
private const val THREE_PROXY_SOCKS_PORT = 1080
private const val PROXY_USERNAME = "kuvasz"
private const val PROXY_PASSWORD = "s3cr3t"

// The stock config, except that CONNECT is allowed to any port, so plain HTTP targets can be tunneled too
private const val SQUID_ANY_PORT_CONF = """
acl localnet src 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 fc00::/7 fe80::/10
http_access allow localhost
http_access allow localnet
http_access deny all
http_port $SQUID_PORT
coredump_dir /var/spool/squid
"""

private const val NGINX_CONF = """
events {}
http {
    access_log /var/log/nginx/access.log;
    default_type text/plain;
    server {
        listen $HTTPS_PORT ssl;
        http2 on;
        ssl_certificate /certs/valid.crt;
        ssl_certificate_key /certs/valid.key;
        location / { return 200 "secure"; }
    }
    server {
        listen $EXPIRED_HTTPS_PORT ssl;
        ssl_certificate /certs/expired.crt;
        ssl_certificate_key /certs/expired.key;
        location / { return 200 "expired"; }
    }
    server {
        listen $WRONG_HOST_HTTPS_PORT ssl;
        ssl_certificate /certs/wrong-host.crt;
        ssl_certificate_key /certs/wrong-host.key;
        location / { return 200 "wrong host"; }
    }
}
"""

/**
 * Checks HTTP monitors through real proxies - Squid for HTTP CONNECT with its stock config, and 3proxy for HTTP CONNECT
 * and SOCKS5, both anonymous and authenticated - against targets that only resolve inside the Docker network.
 *
 * Which proxy carried a request is told by the target, from the address the request came from: each proxy has its own
 * address on the network, and the proxies themselves only log a tunnel once it is closed, while the clients keep them
 * open between the checks.
 */
class ProxyE2ETest : BehaviorSpec({

    val ca = TestCertificateAuthority.generate(
        Files.createTempDirectory("proxy-e2e").also { it.toFile().deleteOnExit() }
    )
    val network = Network.newNetwork()

    val httpTarget = KGenericContainer(MOCKSERVER_IMAGE)
        .withNetwork(network)
        .withNetworkAliases(HTTP_TARGET)
        .withExposedPorts(HTTP_TARGET_PORT)
        .waitingFor(Wait.forLogMessage(".*started on port: $HTTP_TARGET_PORT.*", 1))
    val httpsTarget = KGenericContainer(NGINX_IMAGE)
        .withNetwork(network)
        .withNetworkAliases(HTTPS_TARGET)
        .withCopyToContainer(Transferable.of(NGINX_CONF), "/etc/nginx/nginx.conf")
        .apply {
            listOf(
                // Long enough not to trigger the expiry warning of the SSL checks
                "valid" to ca.issue("valid", HTTPS_TARGET, validityDays = 60),
                "expired" to ca.issue("expired", HTTPS_TARGET, startDate = "-3d", validityDays = 1),
                "wrong-host" to ca.issue("wrong-host", "somewhere-else"),
            ).forEach { (name, certificate) ->
                withCopyToContainer(MountableFile.forHostPath(certificate.chainPem), "/certs/$name.crt")
                withCopyToContainer(MountableFile.forHostPath(certificate.keyPem), "/certs/$name.key")
            }
        }
        .withExposedPorts(HTTPS_PORT)
        .waitingFor(Wait.forListeningPort())
    val squid = KGenericContainer(SQUID_IMAGE)
        .withNetwork(network)
        .withExposedPorts(SQUID_PORT)
        .waitingFor(Wait.forListeningPort())
    val anyPortSquid = KGenericContainer(SQUID_IMAGE)
        .withNetwork(network)
        .withCopyToContainer(Transferable.of(SQUID_ANY_PORT_CONF), "/etc/squid/squid.conf")
        .withExposedPorts(SQUID_PORT)
        .waitingFor(Wait.forListeningPort())

    fun threeProxy(authenticated: Boolean) = KGenericContainer(THREE_PROXY_IMAGE)
        .withNetwork(network)
        // Its own default resolvers are public ones, which know nothing about the names of the network
        .withEnv("PRIMARY_RESOLVER", "127.0.0.11")
        .apply {
            if (authenticated) {
                withEnv("PROXY_LOGIN", PROXY_USERNAME)
                withEnv("PROXY_PASSWORD", PROXY_PASSWORD)
            }
        }
        .withExposedPorts(THREE_PROXY_HTTP_PORT, THREE_PROXY_SOCKS_PORT)
        .waitingFor(Wait.forListeningPort())

    val openProxy = threeProxy(authenticated = false)
    val authProxy = threeProxy(authenticated = true)
    val containers = listOf(httpTarget, httpsTarget, squid, anyPortSquid, openProxy, authProxy)
    Startables.deepStart(containers).get(5, TimeUnit.MINUTES)

    val closedPort = ServerSocket(0).use { it.localPort }
    val proxyContainers = mapOf(
        "squid" to squid,
        "squid-any-port" to anyPortSquid,
        "open-http" to openProxy,
        "open-socks" to openProxy,
        "auth-http" to authProxy,
        "auth-socks" to authProxy,
    )

    fun GenericContainer<*>.url(scheme: String, port: Int) = "$scheme://$host:${getMappedPort(port)}"
    fun GenericContainer<*>.networkAddress(): String =
        containerInfo.networkSettings.networks.values.single().ipAddress.shouldNotBeNull()

    fun proxy(name: String, url: String, password: String? = null) = buildMap {
        put("name", name)
        put("url", url)
        password?.let { proxyPassword ->
            put("username", PROXY_USERNAME)
            put("password", proxyPassword)
        }
    }

    val ctx = testAppContext(
        mapOf(
            "proxies" to listOf(
                proxy("squid", squid.url("http", SQUID_PORT)),
                proxy("squid-any-port", anyPortSquid.url("http", SQUID_PORT)),
                proxy("open-http", openProxy.url("http", THREE_PROXY_HTTP_PORT)),
                proxy("open-socks", openProxy.url("socks5", THREE_PROXY_SOCKS_PORT)),
                proxy("auth-http", authProxy.url("http", THREE_PROXY_HTTP_PORT), PROXY_PASSWORD),
                proxy("auth-socks", authProxy.url("socks5", THREE_PROXY_SOCKS_PORT), PROXY_PASSWORD),
                proxy("auth-http-wrong-password", authProxy.url("http", THREE_PROXY_HTTP_PORT), "wrong"),
                proxy("auth-socks-wrong-password", authProxy.url("socks5", THREE_PROXY_SOCKS_PORT), "wrong"),
                proxy("unreachable", "http://localhost:$closedPort"),
            ),
            PROXY_E2E_TRUST_STORE to ca.trustStore.toString(),
            PROXY_E2E_TRUST_STORE_PASSWORD to ca.trustStorePassword,
        ),
        PROXY_E2E_ENV,
    )
    val monitorRepository = ctx.getBean<HttpMonitorRepository>()
    val monitorActions = ctx.getBean<HttpMonitorActions>()
    val uptimeChecker = ctx.getBean<HttpUptimeChecker>()
    val sslChecker = ctx.getBean<SSLChecker>()
    val mockServer = MockServerClient(httpTarget.host, httpTarget.getMappedPort(HTTP_TARGET_PORT))

    afterSpec {
        // The context of the spec is closed by now
        val ephemeralAppContext = testAppContext()
        ephemeralAppContext.getBean<DSLContext>().resetDatabase()
        ephemeralAppContext.stop()
        mockServer.close()
        containers.forEach { container -> container.stop() }
        network.close()
    }

    var monitorCount = 0
    fun monitor(url: String, proxy: String?, followRedirects: Boolean = false): HttpMonitorRecord = createHttpMonitor(
        monitorRepository,
        monitorName = "proxied-${++monitorCount}",
        url = url,
        proxy = proxy,
        followRedirects = followRedirects,
    )

    suspend fun HttpMonitorRecord.checkUptime(): HttpMonitorDetailsDto {
        uptimeChecker.check(this)
        return monitorActions.getMonitorDetails(id)
    }

    fun HttpMonitorRecord.checkSsl(): HttpMonitorDetailsDto {
        sslChecker.check(this)
        return monitorActions.getMonitorDetails(id)
    }

    fun HttpMonitorDetailsDto.shouldBeUp() = withClue(uptimeError) { uptimeStatus shouldBe UptimeStatus.UP }

    fun HttpMonitorDetailsDto.shouldBeDownWith(error: String) {
        uptimeStatus shouldBe UptimeStatus.DOWN
        uptimeError.shouldNotBeNull() shouldContain error
    }

    // Where the requests to a path of the HTTP target came from
    fun requestSourcesOf(path: String): List<String> =
        mockServer.retrieveRecordedRequestsAndResponses(request().withPath(path)).map { exchange ->
            (exchange.httpRequest as HttpRequest).remoteAddress.substringBefore(':')
        }

    // The access log lines of the HTTPS target, which tell where a request came from and the HTTP version it used
    fun httpsRequests(): List<String> = httpsTarget.logs.lines().filter { "\"GET " in it }

    fun httpsRequestsSince(logOffset: Int): List<String> = httpsRequests().drop(logOffset)

    fun httpsLogOffset() = httpsRequests().size

    mockServer.`when`(request().withPath("/hello.*")).respond(response().withStatusCode(200).withBody("hi"))

    given("a plaintext HTTP target") {

        `when`("it is checked through an HTTP proxy") {
            val details = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-open-http", "open-http").checkUptime()

            then("it should be UP, reached by the proxy") {
                details.shouldBeUp()
                requestSourcesOf("/hello-open-http") shouldBe listOf(openProxy.networkAddress())
            }
        }

        `when`("it is checked through a SOCKS5 proxy") {
            val details = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-open-socks", "open-socks").checkUptime()

            then("it should be UP, reached by the proxy") {
                details.shouldBeUp()
                requestSourcesOf("/hello-open-socks") shouldBe listOf(openProxy.networkAddress())
            }
        }

        // Squid's stock config only lets CONNECT through to the SSL ports, and every plaintext request is tunneled too
        `when`("it is checked through an HTTP proxy that only tunnels to the SSL ports") {
            val details = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-squid", "squid").checkUptime()

            then("it should be DOWN with the status of the proxy") {
                details.shouldBeDownWith("403")
                requestSourcesOf("/hello-squid").shouldBeEmpty()
            }
        }

        `when`("it is checked through an HTTP proxy that is configured to tunnel to any port") {
            val details = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-squid-any-port", "squid-any-port")
                .checkUptime()

            then("it should be UP, reached by the proxy") {
                details.shouldBeUp()
                requestSourcesOf("/hello-squid-any-port") shouldBe listOf(anyPortSquid.networkAddress())
            }
        }

        `when`("it is checked over a direct connection") {
            val details = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-direct", proxy = null).checkUptime()

            then("it should be DOWN, as its name only resolves behind the proxies") {
                details.uptimeStatus shouldBe UptimeStatus.DOWN
                requestSourcesOf("/hello-direct").shouldBeEmpty()
            }
        }

        `when`("it is checked through a proxy that can't be reached") {
            val details = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-unreachable", "unreachable")
                .checkUptime()

            then("it should be DOWN, instead of falling back to a direct connection") {
                details.uptimeStatus shouldBe UptimeStatus.DOWN
                details.uptimeError.shouldNotBeNull()
                requestSourcesOf("/hello-unreachable").shouldBeEmpty()
            }
        }

        `when`("it is checked through a proxy that is not configured") {
            val monitor = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-dangling", "gone")
            val details = monitor.checkUptime()

            then("it should be DOWN, and its SSL check should be skipped") {
                details.shouldBeDownWith("The proxy \"gone\" is not configured")
                monitor.checkSsl().sslStatus.shouldBeNull()
                requestSourcesOf("/hello-dangling").shouldBeEmpty()
            }
        }
    }

    given("a proxy that requires authentication") {

        listOf("auth-http", "auth-socks").forEach { proxy ->

            `when`("a target is checked through $proxy with the right credentials") {
                val details = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-$proxy", proxy).checkUptime()

                then("it should be UP, reached by the proxy") {
                    details.shouldBeUp()
                    requestSourcesOf("/hello-$proxy") shouldBe listOf(authProxy.networkAddress())
                }
            }

            `when`("a target is checked through $proxy with the wrong password") {
                val monitor = monitor("https://$HTTPS_TARGET/", "$proxy-wrong-password")
                val details = monitor.checkUptime()

                then("both of its checks should fail") {
                    details.uptimeStatus shouldBe UptimeStatus.DOWN
                    with(monitor.checkSsl()) {
                        sslStatus shouldBe SslStatus.INVALID
                        sslError.shouldNotBeNull()
                    }
                }
            }
        }

        `when`("a target is checked through an HTTP proxy with the wrong password") {
            val monitor = monitor("https://$HTTPS_TARGET/", "auth-http-wrong-password")

            then("the failures should carry the status of the proxy") {
                monitor.checkUptime().shouldBeDownWith("407")
                monitor.checkSsl().sslError.shouldNotBeNull() shouldContain "407"
            }
        }
    }

    given("an HTTPS target") {

        listOf("squid", "squid-any-port", "open-http", "open-socks", "auth-http", "auth-socks").forEach { proxy ->

            `when`("it is checked through $proxy") {
                val monitor = monitor("https://$HTTPS_TARGET/", proxy)
                val logOffset = httpsLogOffset()
                val uptime = monitor.checkUptime()
                val ssl = monitor.checkSsl()

                then("both of its checks should pass through the proxy, with TLS from end to end") {
                    uptime.shouldBeUp()
                    ssl.sslStatus shouldBe SslStatus.VALID
                    ssl.sslValidUntil.shouldNotBeNull()
                    val sourceAddress = proxyContainers.getValue(proxy).networkAddress()
                    httpsRequestsSince(logOffset).shouldNotBeEmpty().forEach { line ->
                        line shouldContain sourceAddress
                        // ALPN works through the tunnel, as the client offers HTTP/2
                        line shouldContain "HTTP/2.0"
                    }
                }
            }
        }

        `when`("its certificate has expired") {
            val ssl = monitor("https://$HTTPS_TARGET:$EXPIRED_HTTPS_PORT/", "open-socks").checkSsl()

            then("the SSL check should blame the certificate, not the proxy") {
                ssl.sslStatus shouldBe SslStatus.INVALID
                ssl.sslError.shouldNotBeNull() shouldContain "validity check failed"
            }
        }

        `when`("its certificate was issued for another host") {
            val ssl = monitor("https://$HTTPS_TARGET:$WRONG_HOST_HTTPS_PORT/", "open-http").checkSsl()

            then("the SSL check should verify the name of the target, not the one of the proxy") {
                ssl.sslStatus shouldBe SslStatus.INVALID
                ssl.sslError.shouldNotBeNull() shouldContain "No subject alternative DNS name matching $HTTPS_TARGET"
            }
        }
    }

    given("a target that redirects to another one") {
        mockServer.`when`(request().withPath("/redirect-to-https"))
            .respond(response().withStatusCode(302).withHeader("Location", "https://$HTTPS_TARGET/"))

        `when`("it is checked through a proxy") {
            val logOffset = httpsLogOffset()
            val details = monitor(
                "http://$HTTP_TARGET:$HTTP_TARGET_PORT/redirect-to-https",
                "open-http",
                followRedirects = true,
            ).checkUptime()

            then("every hop should go through the proxy") {
                details.shouldBeUp()
                requestSourcesOf("/redirect-to-https") shouldBe listOf(openProxy.networkAddress())
                httpsRequestsSince(logOffset).shouldNotBeEmpty().forEach { it shouldContain openProxy.networkAddress() }
            }
        }
    }

    given("a target that does not meet the expectations of its monitor") {
        mockServer.`when`(request().withPath("/not-found")).respond(response().withStatusCode(404))
        mockServer.`when`(request().withPath("/no-keyword")).respond(response().withStatusCode(200).withBody("hi"))
        mockServer.`when`(request().withPath("/slow"))
            .respond(response().withStatusCode(200).withDelay(Delay.milliseconds(300)))

        `when`("it is checked through a proxy") {
            val notFound = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/not-found", "open-http").checkUptime()
            val noKeyword = createHttpMonitor(
                monitorRepository,
                monitorName = "proxied-no-keyword",
                url = "http://$HTTP_TARGET:$HTTP_TARGET_PORT/no-keyword",
                proxy = "open-socks",
                expectedKeyword = "something else",
            ).checkUptime()
            val slow = createHttpMonitor(
                monitorRepository,
                monitorName = "proxied-slow",
                url = "http://$HTTP_TARGET:$HTTP_TARGET_PORT/slow",
                proxy = "auth-http",
                responseTimeThresholdMillis = 100,
            ).checkUptime()

            then("it should be evaluated just like a direct check") {
                notFound.shouldBeDownWith("Response status code [404] was unexpected")
                noKeyword.shouldBeDownWith("something else")
                slow.uptimeStatus shouldBe UptimeStatus.DOWN
                slow.uptimeError.shouldNotBeNull()
            }
        }
    }

    given("monitors of the very same target, on different routes") {

        `when`("they are checked in turns") {
            val viaOpenProxy = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-isolation", "open-http")
            val viaAuthProxy = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-isolation", "auth-socks")
            val direct = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-isolation", proxy = null)
            val results = (1..3).flatMap {
                listOf(viaOpenProxy.checkUptime(), viaAuthProxy.checkUptime(), direct.checkUptime())
            }

            then("no connection should be reused across the routes") {
                results.filter { it.id == viaOpenProxy.id }.forEach { it.shouldBeUp() }
                results.filter { it.id == viaAuthProxy.id }.forEach { it.shouldBeUp() }
                // A connection pooled by another route would let it through
                results.filter { it.id == direct.id }.forEach { it.uptimeStatus shouldBe UptimeStatus.DOWN }
                requestSourcesOf("/hello-isolation").groupingBy { it }.eachCount() shouldBe mapOf(
                    openProxy.networkAddress() to 3,
                    authProxy.networkAddress() to 3,
                )
            }
        }
    }

    given("a monitor that is moved to another route") {

        `when`("its proxy is changed and then cleared") {
            val monitor = monitor("http://$HTTP_TARGET:$HTTP_TARGET_PORT/hello-reassigned", "open-http")
            val first = monitor.checkUptime()

            fun moveTo(proxy: String?) = monitorActions.updateMonitor(
                monitor.id,
                JsonNodeFactory.instance.objectNode().put(HttpMonitorUpdateDto::proxy.name, proxy),
            )

            val second = moveTo("auth-socks").checkUptime()
            val third = moveTo(null).checkUptime()

            then("each check should take the route it is currently configured with") {
                first.shouldBeUp()
                second.shouldBeUp()
                third.uptimeStatus shouldBe UptimeStatus.DOWN
                requestSourcesOf("/hello-reassigned") shouldBe listOf(
                    openProxy.networkAddress(),
                    authProxy.networkAddress(),
                )
            }
        }
    }
})
