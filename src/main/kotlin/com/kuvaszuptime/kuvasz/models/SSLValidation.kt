package com.kuvaszuptime.kuvasz.models

import java.time.OffsetDateTime

data class SSLValidationError(
    val message: String?
)

data class CertificateInfo(
    val validFrom: OffsetDateTime,
    val validTo: OffsetDateTime
)
