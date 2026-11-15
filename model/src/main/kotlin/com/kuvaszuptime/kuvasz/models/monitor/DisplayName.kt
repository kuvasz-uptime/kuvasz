package com.kuvaszuptime.kuvasz.models.monitor

fun normalizeDisplayName(displayName: String?): String? = displayName?.trim()?.ifBlank { null }
