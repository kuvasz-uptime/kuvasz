package com.kuvaszuptime.kuvasz.services.docker

import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostAuthMethod
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * A client certificate and its private key. They are only useful as a pair, so the type makes one without the other
 * unrepresentable.
 */
data class DockerClientCert(
    val cert: Path,
    val key: Path,
)

/**
 * The TLS material used to talk to a Docker daemon, modeled the way Docker's own client does it: the CA and the
 * client certificate are independent. A CA on its own verifies a self-signed daemon certificate without presenting a
 * client certificate, a client certificate on its own authenticates against a daemon whose certificate chains to a
 * public root, and both together are the usual mTLS setup. At least one of them is always present.
 */
data class DockerTlsMaterial(
    val ca: Path?,
    val clientCert: DockerClientCert?,
)

sealed interface DockerDaemonAddress {

    data class UnixSocket(val path: Path) : DockerDaemonAddress

    data class Tcp(
        val host: String,
        val port: Int,
        val secure: Boolean,
        val tls: DockerTlsMaterial?,
    ) : DockerDaemonAddress
}

data class DockerHost(
    val name: String,
    val url: String,
    val address: DockerDaemonAddress,
) {
    val tlsEnabled: Boolean = address is DockerDaemonAddress.Tcp && address.secure

    val authMethod: DockerHostAuthMethod = when (address) {
        is DockerDaemonAddress.UnixSocket -> DockerHostAuthMethod.UNIX_SOCKET
        is DockerDaemonAddress.Tcp -> when {
            !address.secure -> DockerHostAuthMethod.NONE
            address.tls?.clientCert != null -> DockerHostAuthMethod.MUTUAL_TLS
            else -> DockerHostAuthMethod.TLS
        }
    }
}

class DockerHostConfigException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal object DockerDaemonUrl {

    private const val DEFAULT_PLAIN_PORT = 2375
    private const val DEFAULT_TLS_PORT = 2376
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65_535

    private const val TLS_CA = "ca"
    private const val TLS_CERT = "cert"
    private const val TLS_KEY = "key"

    private const val UNIX_SCHEME = "unix"
    private const val TCP_SCHEME = "tcp"
    private const val HTTP_SCHEME = "http"
    private const val HTTPS_SCHEME = "https"

    private val SUPPORTED_SCHEMES = listOf(UNIX_SCHEME, TCP_SCHEME, HTTP_SCHEME, HTTPS_SCHEME).map { "$it://" }

    // java.net.URI leaves the host empty for a name that is not an RFC 2396 hostname, which rules out underscores,
    // although a Compose service name like docker_proxy is a perfectly resolvable host on a Docker network
    private val UNDERSCORED_AUTHORITY = Regex("([A-Za-z0-9._-]+)(?::([0-9]{1,5}))?")

    fun parse(url: String, tls: DockerTlsMaterial?): DockerDaemonAddress {
        val uri = url.toUri()

        return when (uri.scheme?.lowercase()) {
            UNIX_SCHEME -> parseUnixSocket(url, uri, tls)
            TCP_SCHEME -> parseTcp(uri, secure = tls != null, tls = tls)
            HTTP_SCHEME -> parsePlainHttp(url, uri, tls)
            HTTPS_SCHEME -> parseTcp(uri, secure = true, tls = tls)
            else -> throw unsupportedScheme(url, uri.scheme?.lowercase())
        }
    }

    private fun String.toUri(): URI = try {
        URI(trim())
    } catch (ex: URISyntaxException) {
        throw DockerHostConfigException("[$this] is not a valid URL: ${ex.reason}", ex)
    }

    private fun unsupportedScheme(url: String, scheme: String?): DockerHostConfigException = when (scheme) {
        null -> DockerHostConfigException("[$url] is missing a scheme. Expected one of: $SUPPORTED_SCHEMES")
        "ssh" -> DockerHostConfigException(
            "[$url] uses the unsupported scheme [ssh] (SSH is not supported). Expected one of: $SUPPORTED_SCHEMES"
        )

        else -> DockerHostConfigException(
            "[$url] uses the unsupported scheme [$scheme]. Expected one of: $SUPPORTED_SCHEMES"
        )
    }

    private fun parseUnixSocket(url: String, uri: URI, tls: DockerTlsMaterial?): DockerDaemonAddress.UnixSocket {
        unixSocketProblem(url, uri, tls)?.let { throw DockerHostConfigException(it) }
        // Anything that is not an absolute path has already been rejected: a relative one either lands in the
        // authority (unix://var/run/...) or makes the URI opaque (unix:var/run/...), which leaves the path null.
        return DockerDaemonAddress.UnixSocket(Path.of(uri.path))
    }

    private fun unixSocketProblem(url: String, uri: URI, tls: DockerTlsMaterial?): String? = when {
        tls != null ->
            "TLS material cannot be used with the unix socket URL [$url], because the connection is local."

        !uri.authority.isNullOrBlank() ->
            "[$url] looks like it is missing a slash: a unix socket URL has an empty authority, " +
                "e.g. unix:///var/run/docker.sock"

        uri.path.isNullOrBlank() ->
            "[$url] does not contain a socket path. Expected something like unix:///var/run/docker.sock"

        else -> null
    }

    private fun parsePlainHttp(url: String, uri: URI, tls: DockerTlsMaterial?): DockerDaemonAddress.Tcp {
        if (tls != null) {
            throw DockerHostConfigException(
                "TLS material was configured, but the URL [$url] uses the plaintext 'http' scheme. " +
                    "Use 'tcp' or 'https' instead."
            )
        }
        return parseTcp(uri, secure = false, tls = null)
    }

    private fun parseTcp(uri: URI, secure: Boolean, tls: DockerTlsMaterial?): DockerDaemonAddress.Tcp {
        val (host, explicitPort) = uri.hostAndPort()
        if (host.isNullOrBlank()) {
            throw DockerHostConfigException("[$uri] does not contain a host name.")
        }
        val port = explicitPort.takeIf { it != -1 } ?: if (secure) DEFAULT_TLS_PORT else DEFAULT_PLAIN_PORT
        // Neither java.net.URI nor the lenient authority check range-checks the port, which would otherwise only
        // surface at the first check
        if (port !in MIN_PORT..MAX_PORT) {
            throw DockerHostConfigException(
                "[$uri] has an invalid port [$port]. Expected one between $MIN_PORT and $MAX_PORT."
            )
        }
        return DockerDaemonAddress.Tcp(host = host, port = port, secure = secure, tls = tls)
    }

    private fun URI.hostAndPort(): Pair<String?, Int> {
        if (host != null) return host to port
        return authority?.let(UNDERSCORED_AUTHORITY::matchEntire)
            ?.destructured
            ?.let { (name, portDigits) -> name to (portDigits.toIntOrNull() ?: -1) }
            ?: (null to -1)
    }

    /**
     * Turns the raw, configured TLS paths into [DockerTlsMaterial], checking that every file is actually readable.
     * Returns null when the whole block is absent.
     *
     * @throws DockerHostConfigException if the client certificate and its key are not configured together, or any
     * file is missing/unreadable.
     */
    fun resolveTlsMaterial(ca: String?, cert: String?, key: String?): DockerTlsMaterial? {
        val caPath = ca?.takeIf { it.isNotBlank() }
        val certPath = cert?.takeIf { it.isNotBlank() }
        val keyPath = key?.takeIf { it.isNotBlank() }

        if (caPath == null && certPath == null && keyPath == null) return null

        val halfPair = when {
            certPath != null && keyPath == null -> TLS_CERT to TLS_KEY
            keyPath != null && certPath == null -> TLS_KEY to TLS_CERT
            else -> null
        }
        if (halfPair != null) {
            val (configured, missing) = halfPair
            throw DockerHostConfigException(
                "The TLS '$configured' is configured without '$missing'. A client certificate and its key have to " +
                    "be configured together."
            )
        }

        val clientCert = if (certPath != null && keyPath != null) {
            DockerClientCert(cert = certPath.toReadablePath(TLS_CERT), key = keyPath.toReadablePath(TLS_KEY))
        } else {
            null
        }

        return DockerTlsMaterial(ca = caPath?.toReadablePath(TLS_CA), clientCert = clientCert)
    }

    private fun String.toReadablePath(property: String): Path {
        val path = try {
            Path.of(this)
        } catch (ex: InvalidPathException) {
            throw DockerHostConfigException("The TLS '$property' path [$this] is not valid: ${ex.reason}", ex)
        }
        if (!Files.isReadable(path)) {
            throw DockerHostConfigException("The TLS '$property' file [$path] does not exist or is not readable.")
        }
        return path
    }
}
