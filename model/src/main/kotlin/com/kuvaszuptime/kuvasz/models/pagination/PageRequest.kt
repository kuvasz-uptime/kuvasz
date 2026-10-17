package com.kuvaszuptime.kuvasz.models.pagination

data class PageRequest(val page: Int, val size: Int) {

    val offset: Long = (page - 1).toLong() * size

    companion object {
        const val DEFAULT_PAGE = 1
        const val DEFAULT_SIZE = 25

        const val DEFAULT_PAGE_PARAM = "$DEFAULT_PAGE"
        const val DEFAULT_SIZE_PARAM = "$DEFAULT_SIZE"
    }
}
