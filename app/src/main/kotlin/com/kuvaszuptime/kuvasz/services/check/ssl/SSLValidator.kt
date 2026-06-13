package com.kuvaszuptime.kuvasz.services.check.ssl

import com.kuvaszuptime.kuvasz.models.monitor.ssl.CertificateInfo
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationError
import com.kuvaszuptime.kuvasz.models.monitor.ssl.SSLValidationResult
import com.kuvaszuptime.kuvasz.util.toOffsetDateTime
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.cert.X509Certificate
import javax.net.SocketFactory
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

@Singleton
class SSLValidator {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val socketFactory: SocketFactory = SSLSocketFactory.getDefault()
    private val sslParameters = SSLParameters().apply {
        // Enable host verification
        endpointIdentificationAlgorithm = "HTTPS"
    }

    companion object {
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val DEFAULT_SSL_PORT = 443
    }

    /**
     * Validates the SSL certificate chain of the given HTTPS URL
     *
     * @param url The HTTPS URL to validate.
     * @return An [SSLValidationResult.Invalid] with an SSLValidationError or an [SSLValidationResult.Valid] with a
     * CertificateInfo containing details of the server's certificate if the connection and chain validation succeed.
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    fun validateHttps(url: URI): SSLValidationResult {
        if (url.scheme.lowercase() != "https") {
            return SSLValidationResult.Invalid(
                SSLValidationError("URL protocol must be HTTPS, but was: ${url.scheme}")
            )
        }
        val start = System.currentTimeMillis()

        val result = try {
            createSocket(url).use { sslSocket ->
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

    private fun createSocket(url: URI): SSLSocket {
        val socket = socketFactory.createSocket(url.host, url.port.takeIf { it != -1 } ?: DEFAULT_SSL_PORT) as SSLSocket
        socket.sslParameters = sslParameters
        socket.soTimeout = SOCKET_TIMEOUT_MS
        return socket
    }
}
