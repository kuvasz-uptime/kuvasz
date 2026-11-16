package com.kuvaszuptime.kuvasz.models.monitor

interface WithDisplayName {
    val name: String
    val displayName: String?
}

val WithDisplayName.effectiveName: String
    get() = displayName ?: name
