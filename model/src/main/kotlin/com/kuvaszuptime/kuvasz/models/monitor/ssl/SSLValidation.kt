package com.kuvaszuptime.kuvasz.models.monitor.ssl

import java.time.OffsetDateTime

data class SSLValidationError(
    val message: String?
)

data class CertificateInfo(
    val validTo: OffsetDateTime
)
