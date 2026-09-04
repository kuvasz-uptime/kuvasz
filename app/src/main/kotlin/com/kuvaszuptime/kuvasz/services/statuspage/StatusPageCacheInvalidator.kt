package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions.Companion.DEFAULT_PAGE_CACHE_NAME
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions.Companion.STATUS_PAGES_CACHE_NAME
import io.micronaut.cache.annotation.CacheInvalidate
import jakarta.inject.Singleton

@Singleton
class StatusPageCacheInvalidator {

    @CacheInvalidate(DEFAULT_PAGE_CACHE_NAME, all = true)
    @CacheInvalidate(STATUS_PAGES_CACHE_NAME, all = true)
    fun invalidateAllCaches() = Unit
}
