package com.kuvaszuptime.kuvasz.services

import arrow.core.Either
import com.kuvaszuptime.kuvasz.models.CertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.util.toOffsetDateTime
import java.net.URL
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

@Singleton
class SSLValidator {

    @Suppress("TooGenericExceptionCaught")
    fun validate(url: URL): Either<SSLValidationError, CertificateInfo> {
        return try {
            val conn = url.openConnection() as HttpsURLConnection
            conn.connect()

            getCertificateForHost(url, conn.serverCertificates)?.let { cert ->
                Either.right(CertificateInfo(validTo = cert.notAfter.toOffsetDateTime()))
            } ?: Either.left(SSLValidationError("There were no matching CN for the given host"))
        } catch (e: Throwable) {
            Either.left(SSLValidationError(e.message))
        }
    }

    private fun getCertificateForHost(url: URL, certs: Array<Certificate>): X509Certificate? =
        certs.filterIsInstance<X509Certificate>().firstOrNull { it.cnMatchesWithHost(url) }

    private fun X509Certificate.cnMatchesWithHost(url: URL): Boolean {
        val cn = subjectDN.name.split(",").first().trimEnd().removePrefix("CN=")

        return if (cn.startsWith("*.")) {
            val cnWithoutWildcard = cn.removePrefix("*.")
            val subdomain = url.host.removeSuffix(cnWithoutWildcard)
            val subdomainPattern = Regex("^(([A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?\\.)|(\\S{0}))\$")

            subdomain.matches(subdomainPattern)
        } else cn == url.host
    }
}
