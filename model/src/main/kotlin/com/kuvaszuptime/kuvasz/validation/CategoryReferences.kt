package com.kuvaszuptime.kuvasz.validation

import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import jakarta.validation.ValidationException

/**
 * Normalizes the monitor categories referenced by a status page or a maintenance window: every reference is trimmed,
 * the blank ones are dropped, and the rest is deduplicated. Trimming is what makes a reference comparable to the
 * persisted category of a monitor, which goes through the very same normalization
 * (see [com.kuvaszuptime.kuvasz.models.monitor.normalizedCategory]). The comparison is case-sensitive, just like the
 * grouping on the status pages.
 *
 * Whether a referenced category is actually in use by a monitor is deliberately **not** checked: a category has no
 * identity of its own, it is just a predicate over the monitors carrying it at the moment. A reference that matches
 * nothing today covers no monitors, and starts covering them again as soon as a monitor is tagged with it. That is
 * the difference from the monitor references, where a non-existing one is silently dropped.
 *
 * @throws ValidationException if any of the references is longer than [Validation.MAX_CATEGORY_LENGTH].
 */
fun validateCategories(rawCategories: List<String>): Set<String> =
    rawCategories
        .mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() } }
        .onEach { category ->
            if (category.length > Validation.MAX_CATEGORY_LENGTH) {
                throw ValidationException(ValidationMessages.REFERENCED_CATEGORY_MAX_SIZE)
            }
        }
        .toSet()
