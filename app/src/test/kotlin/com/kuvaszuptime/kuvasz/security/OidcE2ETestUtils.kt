package com.kuvaszuptime.kuvasz.security

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggerContextListener
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Drives the full OIDC authorization-code flow: hits the OIDC login endpoint, submits the Keycloak login form with the
 * given credentials, and returns the [TestBrowser] holding whatever cookies ended up being set (a JWT on success,
 * none on a rejected login).
 */
internal fun oidcLogin(baseUrl: String, username: String, password: String): TestBrowser {
    val browser = TestBrowser()
    val loginPage = browser.get("$baseUrl/oauth/login/oidc")
    val formAction = extractLoginFormAction(loginPage.body())
    val formBody = "username=${username.urlEncoded()}&password=${password.urlEncoded()}&credentialId="
    browser.postForm(formAction, formBody)
    return browser
}

/**
 * A minimal stateful HTTP "browser" that keeps a single cookie jar and follows redirects manually, replaying every
 * stored cookie on every request regardless of its path/domain attributes. Good enough for a same-host E2E flow.
 */
internal class TestBrowser {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
    private val cookies = mutableMapOf<String, String>()

    fun cookie(name: String): String? = cookies[name]

    fun get(url: String): HttpResponse<String> =
        send(requestBuilder(url).GET().build())

    fun postForm(url: String, body: String): HttpResponse<String> =
        send(
            requestBuilder(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        )

    private fun requestBuilder(url: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(url)).apply {
            if (cookies.isNotEmpty()) {
                header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
        }

    private fun send(request: HttpRequest): HttpResponse<String> {
        var response = client.send(request, HttpResponse.BodyHandlers.ofString())
        harvestCookies(response)

        var hops = 0
        while (response.statusCode() in 300..399 && hops < MAX_REDIRECTS) {
            val location = response.headers().firstValue("location").orElseThrow {
                IllegalStateException("Redirect response without a Location header")
            }
            val next = response.uri().resolve(location)
            response = client.send(requestBuilder(next.toString()).GET().build(), HttpResponse.BodyHandlers.ofString())
            harvestCookies(response)
            hops++
        }
        return response
    }

    private fun harvestCookies(response: HttpResponse<*>) {
        response.headers().allValues("set-cookie").forEach { setCookie ->
            val pair = setCookie.substringBefore(";")
            val name = pair.substringBefore("=").trim()
            val value = pair.substringAfter("=", "")
            if (value.isEmpty()) cookies.remove(name) else cookies[name] = value
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 10
    }
}

internal val objectMapper = jacksonObjectMapper()

internal fun String.urlEncoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)

internal fun rolesFromJwt(jwt: String): List<String> {
    val payload = jwt.split(".")[1]
    val decoded = String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8)
    val claims = objectMapper.readValue<Map<String, Any>>(decoded)
    return (claims["roles"] as? List<*>).orEmpty().filterIsInstance<String>()
}

private val LOGIN_FORM_ACTION_REGEX = Regex("""<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"""")

internal fun extractLoginFormAction(html: String): String {
    val rawAction = LOGIN_FORM_ACTION_REGEX.find(html)?.groupValues?.get(1)
        ?: error("Could not find the Keycloak login form action in the returned HTML")
    return rawAction.replace("&amp;", "&")
}

/**
 * Runs [block] while capturing the log events of the loggers of the given [sources]. Micronaut resets the logback
 * context during the startup (to apply the `logger.levels` config), which detaches and stops the appenders added on the
 * fly, so the appender is restarted and re-attached after every reset.
 */
internal fun <T> captureLogs(vararg sources: Class<*>, block: () -> T): Pair<T, List<ILoggingEvent>> {
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    val loggers = sources.map { LoggerFactory.getLogger(it) as Logger }
    val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    val reattacher = object : LoggerContextListener {
        override fun isResetResistant() = true
        override fun onReset(context: LoggerContext) {
            appender.start()
            loggers.forEach { it.addAppender(appender) }
        }
        override fun onStart(context: LoggerContext) = Unit
        override fun onStop(context: LoggerContext) = Unit
        override fun onLevelChange(logger: Logger, level: Level) = Unit
    }

    loggers.forEach { it.addAppender(appender) }
    loggerContext.addListener(reattacher)
    return try {
        block() to appender.list.toList()
    } finally {
        loggerContext.removeListener(reattacher)
        loggers.forEach { it.detachAppender(appender) }
    }
}
