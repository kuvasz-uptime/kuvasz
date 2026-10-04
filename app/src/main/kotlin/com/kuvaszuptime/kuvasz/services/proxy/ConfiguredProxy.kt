package com.kuvaszuptime.kuvasz.services.proxy

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyType
import com.kuvaszuptime.kuvasz.util.lenientHostAndPort
import java.net.URI
import java.net.URISyntaxException

data class ProxyCredentials(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "ProxyCredentials(username=$username, password=****)"
}

data class ConfiguredProxy(
    val name: String,
    val type: ProxyType,
    val host: String,
    val port: Int,
    val credentials: ProxyCredentials?,
)

class ProxyConfigException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ProxyNotConfiguredException(name: String) : RuntimeException(Messages.proxyNotConfigured(name))

internal object ProxyUrl {

    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535
    private const val MAX_SOCKS5_FIELD_BYTES = 255
    private const val MAX_ASCII_CODE = 0x7F

    private const val HTTP_SCHEME = "http"
    private const val HTTPS_SCHEME = "https"
    private const val SOCKS5_SCHEME = "socks5"

    private val SCHEMES = mapOf(HTTP_SCHEME to ProxyType.HTTP, SOCKS5_SCHEME to ProxyType.SOCKS5)
    private val SUPPORTED_SCHEMES = SCHEMES.keys.map { "$it://" }

    fun parse(url: String): Triple<ProxyType, String, Int> {
        val uri = url.toUri()
        val type = uri.scheme?.lowercase()?.let(SCHEMES::get) ?: throw unsupportedScheme(url, uri.scheme?.lowercase())
        val (host, port) = uri.lenientHostAndPort()
        addressProblem(url, uri, host, port)?.let { throw ProxyConfigException(it) }
        // A missing host is reported by addressProblem() already
        return Triple(type, checkNotNull(host), port)
    }

    private fun addressProblem(url: String, uri: URI, host: String?, port: Int): String? {
        val hasPath = !uri.rawPath.isNullOrEmpty() && uri.rawPath != "/"
        return when {
            // Credentials in the URL would end up everywhere the URL is shown or logged
            uri.rawAuthority?.contains('@') == true ->
                "[$url] contains credentials. Please use the 'username' and 'password' properties instead."

            hasPath || uri.rawQuery != null || uri.rawFragment != null ->
                "[$url] should only consist of a scheme, a host and a port."

            host.isNullOrBlank() -> "[$url] does not contain a host name."

            // There is no port every proxy listens on by default, so guessing one would only defer the failure
            port == -1 -> "[$url] does not contain a port, e.g. http://10.0.0.10:3128"

            port !in MIN_PORT..MAX_PORT ->
                "[$url] has an invalid port [$port]. Expected one between $MIN_PORT and $MAX_PORT."

            else -> null
        }
    }

    private fun String.toUri(): URI = try {
        URI(trim())
    } catch (ex: URISyntaxException) {
        throw ProxyConfigException("[$this] is not a valid URL: ${ex.reason}", ex)
    }

    private fun unsupportedScheme(url: String, scheme: String?): ProxyConfigException = when (scheme) {
        null -> ProxyConfigException("[$url] is missing a scheme. Expected one of: $SUPPORTED_SCHEMES")
        HTTPS_SCHEME -> ProxyConfigException(
            "[$url] uses the unsupported scheme [$HTTPS_SCHEME] " +
                "(TLS connections to the proxy itself are not supported, " +
                "but HTTPS targets can be monitored through an http:// proxy). Expected one of: $SUPPORTED_SCHEMES"
        )

        else -> ProxyConfigException(
            "[$url] uses the unsupported scheme [$scheme]. Expected one of: $SUPPORTED_SCHEMES"
        )
    }

    fun resolveCredentials(type: ProxyType, username: String?, password: String?): ProxyCredentials? {
        val user = username?.takeIf { it.isNotBlank() }
        val pass = password?.takeIf { it.isNotEmpty() }
        val socks5Fields = if (type == ProxyType.SOCKS5) listOfNotNull(user, pass) else emptyList()
        // The SOCKS5 handler of Netty, which the HTTP checks go through, writes them as ASCII, so anything else would
        // be sent differently by the HTTP and the TCP checks
        val notAsciiForSocks5 = socks5Fields.any { field -> field.any { it.code > MAX_ASCII_CODE } }
        // RFC 1929 sends both of them with a single byte of length
        val tooLongForSocks5 = socks5Fields.any { it.length > MAX_SOCKS5_FIELD_BYTES }
        val onlyOneOfThem = (user != null) xor (pass != null)
        val problem = when {
            onlyOneOfThem ->
                "Only one of 'username' and 'password' is configured. They have to be configured together."

            notAsciiForSocks5 ->
                "The 'username' and the 'password' of a SOCKS5 proxy can only contain ASCII characters."

            tooLongForSocks5 ->
                "The 'username' and the 'password' of a SOCKS5 proxy can be at most $MAX_SOCKS5_FIELD_BYTES bytes long."

            else -> null
        }
        problem?.let { throw ProxyConfigException(it) }

        return if (user != null && pass != null) ProxyCredentials(username = user, password = pass) else null
    }
}
