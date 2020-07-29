package com.akobor.kuvasz.util

import java.time.OffsetDateTime
import java.time.ZoneId

fun getCurrentTimestamp(): OffsetDateTime = OffsetDateTime.now(ZoneId.of("UTC"))
