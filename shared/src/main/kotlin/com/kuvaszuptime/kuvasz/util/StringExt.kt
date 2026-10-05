package com.kuvaszuptime.kuvasz.util

fun String?.nullIfBlank(): String? = this?.trim()?.ifBlank { null }
