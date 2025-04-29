package com.kuvaszuptime.kuvasz.services

import arrow.core.Either
import com.kuvaszuptime.kuvasz.models.CertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.util.toOffsetDateTime
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.cert.CertPath
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@Singleton
class SSLValidator {

    private val logger = LoggerFactory.getLogger(SSLValidator::class.java)

    // Load default trust anchors from JVM's default trust store (e.g., cacerts)
    private val trustManagerFactory = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .also { it.init(null as KeyStore?) } // null initializes with the default JVM trust store
    private val certPathValidator = CertPathValidator.getInstance(CERT_PATH_VALIDATOR_TYPE)
    private val certFactory = CertificateFactory.getInstance(CERT_TYPE)
    private val trustAnchors = mutableSetOf<TrustAnchor>()
    private lateinit var pkixParams: PKIXParameters

    init {
        trustManagerFactory.trustManagers.forEach { trustManager ->
            if (trustManager is X509TrustManager) {
                // Extract trusted CAs from the default TrustManager
                trustManager.acceptedIssuers.forEach { issuer ->
                    trustAnchors.add(TrustAnchor(issuer, null))
                }
            }
        }
        check(trustAnchors.isNotEmpty()) {
            logger.error("No trust anchors found in default JVM trust store for SSL validation")
            "Configuration error: No trusted CAs found in the system"
        }

        pkixParams = PKIXParameters(trustAnchors)
        // Disable CRL/OCSP checks for simplicity
        pkixParams.isRevocationEnabled = false
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000
        private const val CERT_TYPE = "X.509"
        private const val CERT_PATH_VALIDATOR_TYPE = "PKIX"
    }

    /**
     * Validates the SSL certificate chain of the given HTTPS URL using the default JVM trust store.
     * It first attempts connection using HttpsURLConnection (which performs default checks).
     * If successful, it then explicitly validates the certificate chain using CertPathValidator.
     *
     * @param url The HTTPS URL to validate.
     * @return Either an SSLValidationError or CertificateInfo containing details of the server's certificate
     * if the connection and chain validation succeed.
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    fun validate(url: URL): Either<SSLValidationError, CertificateInfo> {
        if (url.protocol.lowercase() != "https") {
            return Either.Left(SSLValidationError("URL protocol must be HTTPS, but was: ${url.protocol}"))
        }

        var conn: HttpsURLConnection? = null
        try {
            // Establish Connection & Initial Handshake
            conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS

            logger.debug("Attempting SSL connection to: {}", url)
            // connect() performs default handshake, including basic chain and hostname checks
            conn.connect()
            logger.debug("Initial SSL connection successful, proceeding with explicit chain validation for: {}", url)

            // Get Certificates Presented by Server
            val serverCertsRaw = conn.serverCertificates
            if (serverCertsRaw.isNullOrEmpty()) {
                logger.debug("No server certificates received from: {}", url)
                return Either.Left(SSLValidationError("Server did not return any certificates"))
            }

            val serverCertsX509 = serverCertsRaw.mapNotNull { it as? X509Certificate }
            // Ensure all certificates were of the expected type
            if (serverCertsX509.size != serverCertsRaw.size) {
                logger.error("Received non-X509 certificates in the chain from {}", url)
                return Either.Left(SSLValidationError("Certificate chain contains non-X509 certificates"))
            }

            return serverCertsX509.validate(url)
        } catch (e: SSLHandshakeException) {
            // This indicates a failure during the initial TLS handshake, which could be due to various reasons
            // including certificate issues (expiry, untrusted, name mismatch) detected by the default
            // TrustManager/HostnameVerifier.
            val errorMessage = e.message ?: "Unknown handshake error"
            logger.debug("Initial SSL Handshake failed for {}: {}", url, errorMessage)
            val causeMessage = e.cause?.message?.let { " (Cause: $it)" }.orEmpty()
            return Either.Left(SSLValidationError("SSL Handshake failed: $errorMessage$causeMessage"))
        } catch (e: UnknownHostException) {
            logger.debug("Unknown host for {}: {}", url, e.message)
            return Either.Left(SSLValidationError("Unknown host: ${e.message}"))
        } catch (e: SocketTimeoutException) {
            logger.debug("Timeout connecting to or reading from {}: {}", url, e.message)
            return Either.Left(SSLValidationError("Connection timed out (${e.message})"))
        } catch (e: Throwable) {
            val errorMessage = e.message ?: "Unknown error"
            logger.error("Unexpected error during SSL validation for {}: {}", url, errorMessage)
            return Either.Left(SSLValidationError("An unexpected error occurred: $errorMessage"))
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Performs explicit chain validation using CertPathValidator
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun List<X509Certificate>.validate(url: URL): Either<SSLValidationError, CertificateInfo> {
        try {
            val certPath: CertPath = certFactory.generateCertPath(this)
            logger.debug(
                "Validating certificate chain for {} using {}",
                url,
                CERT_PATH_VALIDATOR_TYPE,
            )

            // *** The core validation step ***
            // Throws CertPathValidatorException if the chain is invalid
            certPathValidator.validate(certPath, pkixParams)
            logger.debug("Certificate chain validation successful for {}", url)
        } catch (e: CertPathValidatorException) {
            // Chain validation failed
            logger.debug("Certificate chain validation failed for {}: {}", url, e.message)
            val reason = e.reason
            val errorIndex = e.index // Index of the failing certificate (-1 if not specific)
            val failedCertSubject = if (errorIndex >= 0 && errorIndex < this.size) {
                "Subject='${this[errorIndex].subjectX500Principal.name}'"
            } else {
                "N/A"
            }
            return Either.Left(
                SSLValidationError(
                    "Certificate chain validation failed: ${reason ?: "Unknown reason"} " +
                        "(Certificate index: $errorIndex, $failedCertSubject). Details: ${e.message}"
                )
            )
        } catch (e: NoSuchAlgorithmException) {
            logger.error("Chain validation setup failed for {}: Algorithm not found ({})", url, e.message, e)
            return Either.Left(SSLValidationError("Certificate validation setup failed (Algorithm): ${e.message}"))
        } catch (e: InvalidAlgorithmParameterException) {
            logger.error("Chain validation setup failed for {}: Invalid parameters ({})", url, e.message, e)
            return Either.Left(SSLValidationError("Certificate validation setup failed (Parameters): ${e.message}"))
        } catch (e: CertificateException) { // Catch CertificateFactory or CertPath generation issues
            logger.error(
                "Chain validation setup failed for {}: Certificate processing error ({})",
                url,
                e.message,
                e
            )
            return Either.Left(SSLValidationError("Certificate processing error: ${e.message}"))
        } catch (e: Exception) { // Catch other potential errors during validation setup (e.g., KeyStoreException)
            logger.error("Unexpected error during chain validation setup for {}: {}", url, e.message, e)
            return Either.Left(SSLValidationError("Unexpected error during certificate processing: ${e.message}"))
        }

        // Chain Validation Successful: Extract Info from End-Entity Certificate
        // The server's own certificate is the first one in the validated chain.
        val endEntityCert = this.first()

        return Either.Right(
            CertificateInfo(
                validTo = endEntityCert.notAfter.toOffsetDateTime(),
            )
        )
    }
}
