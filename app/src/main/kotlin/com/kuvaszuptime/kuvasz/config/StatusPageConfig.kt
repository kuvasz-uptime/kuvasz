package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.statuspage.StatusPageCreator
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable

@ConfigurationProperties(DefaultStatusPageConfig.CONFIG_PREFIX)
interface DefaultStatusPageConfig {

    @get:Bindable(defaultValue = StatusPageConfigDefaults.DEFAULT_PAGE_ENABLED.toString())
    val enabled: Boolean

    @get:Bindable(defaultValue = StatusPageConfigDefaults.TITLE)
    val title: String

    companion object {
        private const val CONFIG_PREFIX = "default-status-page"
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
        const val CONFIG_PREFIX = "status-pages"
    }
}

object StatusPageConfigDefaults {
    const val DEFAULT_PAGE_ENABLED = false
    const val CUSTOM_PAGE_ENABLED = true
    const val TITLE = "Status - Kuvasz Uptime"
}
