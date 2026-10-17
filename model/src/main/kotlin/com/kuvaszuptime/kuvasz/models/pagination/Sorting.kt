package com.kuvaszuptime.kuvasz.models.pagination

import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder

data class Sorting<F : Enum<F>>(val field: F, val order: SortOrder) {

    companion object {
        fun <F : Enum<F>> of(field: F?, order: SortOrder?, default: Sorting<F>): Sorting<F> =
            if (field == null && order == null) {
                default
            } else {
                Sorting(field = field ?: default.field, order = order ?: SortOrder.ASC)
            }
    }
}
