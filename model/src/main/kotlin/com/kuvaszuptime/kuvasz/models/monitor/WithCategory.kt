package com.kuvaszuptime.kuvasz.models.monitor

/**
 * Implemented by everything that carries a monitor category: the creators (API create DTOs, YAML configs, import
 * adapters), the update DTOs and the monitor records themselves, so that all of them normalize it the same way.
 */
interface WithCategory {
    val category: String?
}

/**
 * The persisted form of a category: a trimmed value, or null if it's blank.
 */
val WithCategory.normalizedCategory: String?
    get() = category?.trim()?.ifBlank { null }
