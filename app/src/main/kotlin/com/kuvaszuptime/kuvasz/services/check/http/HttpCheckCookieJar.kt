package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.models.checks.RawHttpResponse
import com.kuvaszuptime.kuvasz.util.origin
import io.micronaut.http.HttpHeaders
import io.netty.handler.codec.http.cookie.ClientCookieDecoder
import io.netty.handler.codec.http.cookie.Cookie
import java.net.URI

/**
 * Keeps the cookies set by the responses of a single HTTP check, so the subsequent hops of a redirect chain can send
 * them back, like a browser would. Some sites (typically behind a CDN) only serve a resource to a client that sends
 * back the cookie they set with a redirect, often pointing to the very same URL.
 *
 * It is deliberately stricter than a browser: the cookies are only sent back to the origin (scheme, host and port)
 * that set them, regardless of their Domain attribute, the same way as the custom request headers are kept on the
 * origin of the monitored URL. So a cookie can't leak to another site, to another service on a different port of the
 * same host, or in cleartext to the http variant of an https site, and a cookie set over http can't override one on
 * https either. A new jar belongs to every check, nothing is kept between the checks.
 */
class HttpCheckCookieJar {

    private val cookies = mutableMapOf<CookieKey, StoredCookie>()

    private val sentCookies = mutableMapOf<URI, Set<String>>()

    // Whether a redirect to an already visited URL has been followed in this check because of the cookies
    var isRevisitUsed: Boolean = false
        private set

    /**
     * Stores the cookies set by the [response] of the request that was sent to [requestUri], and removes the ones that
     * the response expires.
     *
     * The Set-Cookie headers are parsed leniently, because Micronaut's own (strict) parsing silently drops the cookies
     * that don't follow RFC 6265 to the letter (e.g. a JSON value), and it also keeps only one of the cookies that
     * have the same name.
     */
    fun storeFrom(response: RawHttpResponse, requestUri: URI) {
        val origin = requestUri.origin() ?: return
        response.headers.getAll(HttpHeaders.SET_COOKIE)
            .mapNotNull { ClientCookieDecoder.LAX.decode(it) }
            .filter { cookie -> cookie.isAcceptableFrom(requestUri) }
            .forEach { cookie ->
                val path = cookie.path()?.takeIf { it.startsWith("/") } ?: requestUri.defaultCookiePath
                val key = CookieKey(origin, cookie.name(), path)
                if (cookie.isExpired) {
                    cookies.remove(key)
                } else {
                    cookies[key] = StoredCookie(origin, path, cookie)
                }
            }
    }

    fun cookiesFor(uri: URI): List<Cookie> {
        val origin = uri.origin()
        return cookies.values
            .filter { it.origin == origin && uri.requestPath.pathMatches(it.path) }
            .sortedByDescending { it.path.length }
            .map { it.cookie }
    }

    fun recordRequestTo(uri: URI, cookieHeader: String?) {
        sentCookies[uri] = cookieHeader.toCookiePairs()
    }

    fun wasSentTo(uri: URI, cookieHeader: String?): Boolean =
        sentCookies[uri].orEmpty() == cookieHeader.toCookiePairs()

    fun useRevisit() {
        isRevisitUsed = true
    }

    private fun String?.toCookiePairs(): Set<String> =
        orEmpty().split(";").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private data class CookieKey(val origin: String, val name: String, val path: String)

    // The origin check covers the Secure attribute too: a Secure cookie is only accepted from an https origin, so it
    // is never sent over http
    private class StoredCookie(val origin: String, val path: String, val cookie: Cookie)

    /**
     * Like in browsers, an insecure origin can't set a Secure cookie, and the cookies with the __Secure- and __Host-
     * name prefixes are only accepted if they meet the requirements of their prefix (RFC 6265bis, section 4.1.3).
     */
    private fun Cookie.isAcceptableFrom(requestUri: URI): Boolean {
        val isSecureFromHttps = isSecure && requestUri.scheme.equals("https", ignoreCase = true)
        return when {
            name().startsWith(SECURE_PREFIX, ignoreCase = true) -> isSecureFromHttps
            name().startsWith(HOST_PREFIX, ignoreCase = true) ->
                isSecureFromHttps && domain() == null && path() == "/"
            else -> !isSecure || isSecureFromHttps
        }
    }

    private val Cookie.isExpired: Boolean
        get() = maxAge() != Cookie.UNDEFINED_MAX_AGE && maxAge() <= 0

    // The default path of a cookie without a valid Path attribute, as described in RFC 6265 (section 5.1.4): the
    // directory of the request path
    private val URI.defaultCookiePath: String
        get() = requestPath.substringBeforeLast("/").ifEmpty { "/" }

    private val URI.requestPath: String
        get() = rawPath.ifEmpty { "/" }

    // RFC 6265 (section 5.1.4): the cookie path is either the same as the request path, or a prefix of it that ends at
    // a "/" boundary
    private fun String.pathMatches(cookiePath: String): Boolean =
        this == cookiePath || (startsWith(cookiePath) && (cookiePath.endsWith("/") || this[cookiePath.length] == '/'))

    companion object {
        private const val SECURE_PREFIX = "__Secure-"
        private const val HOST_PREFIX = "__Host-"
    }
}
