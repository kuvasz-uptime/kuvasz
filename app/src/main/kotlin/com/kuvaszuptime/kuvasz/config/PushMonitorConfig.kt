package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDefaults
import com.kuvaszuptime.kuvasz.models.monitor.push.PushMonitorCreator
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable

@EachProperty(PushMonitorConfig.CONFIG_PREFIX, list = true)
@Introspected
interface PushMonitorConfig : PushMonitorCreator {

    companion object {
        const val CONFIG_PREFIX = "push-monitors"
    }

    override val name: String
    override val clientSecret: String
    override val heartbeatInterval: Long

    @get:Bindable(defaultValue = PushMonitorDefaults.GRACE_PERIOD_SECONDS.toString())
    override val gracePeriod: Long

    @get:Bindable(defaultValue = PushMonitorDefaults.MONITOR_ENABLED.toString())
    override val enabled: Boolean

    override val integrations: List<String>?
}
