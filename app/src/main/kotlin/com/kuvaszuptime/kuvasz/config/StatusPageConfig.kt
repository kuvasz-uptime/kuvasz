package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDefaults
import com.kuvaszuptime.kuvasz.models.statuspage.StatusPageCreator
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable

/**
 * default-status-page:
 *   public: true
 *   title: "Status - Kuvasz Uptime"
 *   custom-logo-url: "https://example.com/logo.png"
 *   custom-favicon-url: "https://example.com/favicon.png"
 *   display-categories: true
 */
@ConfigurationProperties(DefaultStatusPageConfig.CONFIG_PREFIX)
interface DefaultStatusPageConfig {

    @get:Bindable(defaultValue = StatusPageDefaults.DEFAULT_PAGE_PUBLIC.toString())
    val public: Boolean

    val customLogoUrl: String?

    val customFaviconUrl: String?

    @get:Bindable(defaultValue = StatusPageDefaults.TITLE)
    val title: String

    @get:Bindable(defaultValue = StatusPageDefaults.DISPLAY_CATEGORIES.toString())
    val displayCategories: Boolean

    companion object {
        private const val CONFIG_PREFIX = "default-status-page"
    }
}

/**
 * status-pages:
 *   - title: "Example Status Page"
 *     slug: "example-status"
 *     public: true
 *     custom-logo-url: "https://example.com/logo.png"
 *     custom-favicon-url: "https://example.com/favicon.png"
 *     monitors:
 *       - "http:Test monitor 1"
 *       - "http:Test monitor 2"
 *     categories:
 *       - "Payments"
 *     display-categories: true
 */
@EachProperty(StatusPageConfig.CONFIG_PREFIX, list = true)
@Introspected
interface StatusPageConfig : StatusPageCreator {

    override val title: String

    override val slug: String

    override val customLogoUrl: String?

    override val customFaviconUrl: String?

    override val monitors: List<String>?

    override val categories: List<String>?

    @get:Bindable(defaultValue = StatusPageDefaults.DISPLAY_CATEGORIES.toString())
    override val displayCategories: Boolean

    @get:Bindable(defaultValue = StatusPageDefaults.CUSTOM_PAGE_PUBLIC.toString())
    override val public: Boolean

    companion object {
        const val CONFIG_PREFIX = "status-pages"
    }
}
