package com.kuvaszuptime.kuvasz.util

import io.micronaut.http.HttpResponse
import java.net.URI
import kotlin.jvm.optionals.getOrNull

fun String.toUri(): URI = URI(this)

// java.net.URI leaves the host empty for a name that is not an RFC 2396 hostname, which rules out underscores,
// although a Compose service name like docker_proxy is a perfectly resolvable host on a Docker network
private val UNDERSCORED_AUTHORITY = Regex("([A-Za-z0-9._-]+)(?::([0-9]{1,5}))?")

/**
 * Returns the host and the port (-1 if absent) of a server URL, also accepting host names with underscores.
 */
fun URI.lenientHostAndPort(): Pair<String?, Int> {
    if (host != null) return host to port
    return authority?.let(UNDERSCORED_AUTHORITY::matchEntire)
        ?.destructured
        ?.let { (name, portDigits) -> name to (portDigits.toIntOrNull() ?: -1) }
        ?: (null to -1)
}

private const val DEFAULT_HTTP_PORT = 80
private const val DEFAULT_HTTPS_PORT = 443

/**
 * Returns the origin (scheme, host and port) of the URI in a normalized form, where a missing port means the default
 * port of the scheme, or null if the URI has no scheme or authority.
 */
fun URI.origin(): String? {
    // URI.host is null for hosts that can't be parsed (e.g. the ones containing an underscore), so the raw authority
    // is used for them instead, otherwise two different unparsable hosts would be treated as the same origin
    val originAuthority = host?.let { "$it:${effectivePort()}" } ?: authority
    return if (scheme != null && originAuthority != null) {
        "${scheme.lowercase()}://${originAuthority.lowercase()}"
    } else {
        null
    }
}

fun URI.hasSameOriginAs(other: URI): Boolean = origin()?.let { it == other.origin() } ?: false

private fun URI.effectivePort(): Int = when {
    port != -1 -> port
    scheme.equals("https", ignoreCase = true) -> DEFAULT_HTTPS_PORT
    else -> DEFAULT_HTTP_PORT
}

inline fun <reified T : Any> HttpResponse<*>.getBodyAs(): T? = getBody(T::class.java).getOrNull()

@Suppress("MagicNumber")
fun HttpResponse<*>.isSuccess(): Boolean = this.status.code in 200..299

@Suppress("MagicNumber")
fun HttpResponse<*>.isServerRelatedError(): Boolean = this.status.code >= 500
