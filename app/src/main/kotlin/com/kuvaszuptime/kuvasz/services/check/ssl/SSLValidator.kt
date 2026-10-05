package com.kuvaszuptime.kuvasz.services.check.ssl

import com.kuvaszuptime.kuvasz.models.monitor.ssl.CertificateInfo
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationResult
import com.kuvaszuptime.kuvasz.util.closeQuietly
import com.kuvaszuptime.kuvasz.util.loggerFor
import com.kuvaszuptime.kuvasz.util.toOffsetDateTime
import com.kuvaszuptime.kuvasz.services.proxy.ConfiguredProxy
import com.kuvaszuptime.kuvasz.services.proxy.ProxyTunnel
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException
import java.net.URI
import java.security.cert.X509Certificate
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

@Singleton
class SSLValidator internal constructor(
    private val proxyTunnel: ProxyTunnel,
    private val socketFactory: SSLSocketFactory,
) {

    @Inject
    constructor(proxyTunnel: ProxyTunnel) : this(proxyTunnel, SSLSocketFactory.getDefault() as SSLSocketFactory)

    private val sslParameters = SSLParameters().apply {
        // Enable host verification
        endpointIdentificationAlgorithm = "HTTPS"
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val DEFAULT_SSL_PORT = 443
        private val logger = loggerFor<SSLValidator>()
    }

    /**
     * Validates the SSL certificate chain of the given HTTPS URL
     *
     * @param url The HTTPS URL to validate.
     * @param proxy The proxy to reach the URL through, or null for a direct connection.
     * @return An [SSLValidationResult.Invalid] with an SSLValidationError or an [SSLValidationResult.Valid] with a
     * CertificateInfo containing details of the server's certificate if the connection and chain validation succeed.
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    fun validateHttps(url: URI, proxy: ConfiguredProxy? = null): SSLValidationResult {
        if (url.scheme.lowercase() != "https") {
            return SSLValidationResult.Invalid(
                SSLValidationError("URL protocol must be HTTPS, but was: ${url.scheme}")
            )
        }
        val start = System.currentTimeMillis()

        val result = try {
            createSocket(url, proxy).use { sslSocket ->
                sslSocket.startHandshake()
                val session = sslSocket.session
                sslSocket.close()
                val certificates = session.peerCertificates.filterIsInstance<X509Certificate>()
                certificates.firstOrNull()?.let { peerCert ->
                    SSLValidationResult.Valid(CertificateInfo(validTo = peerCert.notAfter.toOffsetDateTime()))
                } ?: SSLValidationResult.Invalid(SSLValidationError("No peer certificate was found"))
            }
        } catch (ex: Exception) {
            SSLValidationResult.Invalid(
                SSLValidationError("Error connecting or retrieving certificates for ${url.host}, reason: ${ex.message}")
            )
        }
        logger.debug("Finished SSL check for ${url.host} in ${System.currentTimeMillis() - start} ms")
        return result
    }

    private fun createSocket(url: URI, proxy: ConfiguredProxy?): SSLSocket {
        val port = url.port.takeIf { it != -1 } ?: DEFAULT_SSL_PORT
        val socket = if (proxy == null) {
            socketFactory.createSocket(url.host, port)
        } else {
            val tunnel = proxyTunnel.open(proxy, url.host, port, SOCKET_TIMEOUT_MS)
            try {
                // Layering TLS over the tunnel keeps the target's name for SNI and for the hostname verification
                socketFactory.createSocket(tunnel, url.host, port, true)
            } catch (ex: IOException) {
                tunnel.closeQuietly()
                throw ex
            }
        } as SSLSocket
        socket.sslParameters = sslParameters
        socket.soTimeout = SOCKET_TIMEOUT_MS
        return socket
    }
}
