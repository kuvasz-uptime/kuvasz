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

inline fun <reified T : Any> HttpResponse<*>.getBodyAs(): T? = getBody(T::class.java).getOrNull()

@Suppress("MagicNumber")
fun HttpResponse<*>.isSuccess(): Boolean = this.status.code in 200..299

@Suppress("MagicNumber")
fun HttpResponse<*>.isServerRelatedError(): Boolean = this.status.code >= 500
