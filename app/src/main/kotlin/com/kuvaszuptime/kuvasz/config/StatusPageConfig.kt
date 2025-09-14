package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDefaults
import com.kuvaszuptime.kuvasz.models.statuspage.StatusPageCreator
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable

/**
 * default-status-page:
 *   enabled: true
 *   title: "Status - Kuvasz Uptime"
 */
@ConfigurationProperties(DefaultStatusPageConfig.CONFIG_PREFIX)
interface DefaultStatusPageConfig {

    @get:Bindable(defaultValue = StatusPageDefaults.DEFAULT_PAGE_ENABLED.toString())
    val enabled: Boolean

    @get:Bindable(defaultValue = StatusPageDefaults.TITLE)
    val title: String

    companion object {
        private const val CONFIG_PREFIX = "default-status-page"
    }
}

/**
 * status-pages:
 *   - title: "Example Status Page"
 *     slug: "example-status"
 *     enabled: true
 *     monitors:
 *       - "http:Test monitor 1"
 *       - "http:Test monitor 2"
 */
@EachProperty(StatusPageConfig.CONFIG_PREFIX, list = true)
@Introspected
interface StatusPageConfig : StatusPageCreator {

    @get:Bindable(defaultValue = StatusPageDefaults.TITLE)
    override val title: String

    override val slug: String

    override val monitors: List<String>?

    @get:Bindable(defaultValue = StatusPageDefaults.CUSTOM_PAGE_ENABLED.toString())
    override val enabled: Boolean

    companion object {
        const val CONFIG_PREFIX = "status-pages"
    }
}
