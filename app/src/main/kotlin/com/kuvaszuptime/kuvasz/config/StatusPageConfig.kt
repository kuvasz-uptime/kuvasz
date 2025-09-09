package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.statuspage.StatusPageCreator
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable

private const val STATUS_PAGES_CONFIG_PREFIX = "status-pages"

/**
 * status-pages:
 *   default:
 *     enabled: true
 *     title: "Kuvasz Status"
 *   configs:
 *     - title: "Example Status Page"
 *       slug: "example-status"
 *       monitors:
 *         - "http:Test monitor 1"
 *         - "http:Test monitor 2"
 */
@ConfigurationProperties(StatusPageDefaultConfig.CONFIG_PREFIX)
interface StatusPageDefaultConfig {

    @get:Bindable(defaultValue = StatusPageConfigDefaults.DEFAULT_PAGE_ENABLED.toString())
    val enabled: Boolean

    @get:Bindable(defaultValue = StatusPageConfigDefaults.TITLE)
    val title: String

    companion object {
        private const val CONFIG_PREFIX = "$STATUS_PAGES_CONFIG_PREFIX.default"
    }
}

@EachProperty(StatusPageConfig.CONFIG_PREFIX, list = true)
@Introspected
interface StatusPageConfig : StatusPageCreator {

    @get:Bindable(defaultValue = StatusPageConfigDefaults.TITLE)
    override val title: String

    override val slug: String

    override val monitors: List<String>?

    @get:Bindable(defaultValue = StatusPageConfigDefaults.CUSTOM_PAGE_ENABLED.toString())
    override val enabled: Boolean

    companion object {
        const val CONFIG_PREFIX = "$STATUS_PAGES_CONFIG_PREFIX.configs"
    }
}

object StatusPageConfigDefaults {
    const val DEFAULT_PAGE_ENABLED = false
    const val CUSTOM_PAGE_ENABLED = true
    const val TITLE = "Status - Kuvasz Uptime"
}
