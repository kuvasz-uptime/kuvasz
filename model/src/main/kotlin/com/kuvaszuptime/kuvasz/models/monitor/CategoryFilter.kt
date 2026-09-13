package com.kuvaszuptime.kuvasz.models.monitor

/**
 * The category filter of a monitor list, mirroring the `category` query parameter of the list pages: an absent
 * parameter means no filtering, an empty one the monitors without a category, and anything else that one category.
 *
 * It is a type rather than a nullable string, because "no filter" and "the ones with no category" are two different
 * things that a single `String?` cannot tell apart.
 */
sealed interface CategoryFilter {

    data object Uncategorized : CategoryFilter

    data class InCategory(val category: String) : CategoryFilter

    companion object {
        fun fromQueryParam(rawCategory: String?): CategoryFilter? = rawCategory?.let { category ->
            category.trim().takeIf { it.isNotEmpty() }?.let { InCategory(it) } ?: Uncategorized
        }
    }
}
