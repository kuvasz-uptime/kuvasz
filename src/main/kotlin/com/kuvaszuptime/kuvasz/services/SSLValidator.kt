package com.kuvaszuptime.kuvasz.services

import arrow.core.Either
import com.kuvaszuptime.kuvasz.models.CertificateInfo
import com.kuvaszuptime.kuvasz.models.SSLValidationError
import com.kuvaszuptime.kuvasz.util.toOffsetDateTime
import java.net.URL
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

            conn.serverCertificates.filterIsInstance<X509Certificate>().firstOrNull()?.let { cert ->
                Either.right(CertificateInfo(validTo = cert.notAfter.toOffsetDateTime()))
            } ?: Either.left(SSLValidationError("There were no matching CN for the given host"))
        } catch (e: Throwable) {
            Either.left(SSLValidationError(e.message))
        }
    }
}
