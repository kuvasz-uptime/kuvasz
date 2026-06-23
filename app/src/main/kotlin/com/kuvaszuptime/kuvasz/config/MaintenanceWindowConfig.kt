package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDefaults
import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceWindowCreator
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable
import java.time.OffsetDateTime

/**
 * maintenance-windows:
 *   - name: "Nightly DB maintenance"
 *     description: "Recurring nightly maintenance"
 *     enabled: true
 *     global: false
 *     show-on-status-pages: true
 *     cron: "0 2 * * *"
 *     duration: "PT1H"
 *     monitors:
 *       - "http:Test monitor 1"
 *     integrations:
 *       - "slack:my-slack"
 */
@EachProperty(MaintenanceWindowConfig.CONFIG_PREFIX, list = true)
@Introspected
interface MaintenanceWindowConfig : MaintenanceWindowCreator {

    override val name: String

    override val description: String?

    @get:Bindable(defaultValue = MaintenanceWindowDefaults.ENABLED.toString())
    override val enabled: Boolean

    @get:Bindable(defaultValue = MaintenanceWindowDefaults.GLOBAL.toString())
    override val global: Boolean

    @get:Bindable(defaultValue = MaintenanceWindowDefaults.SHOW_ON_STATUS_PAGES.toString())
    override val showOnStatusPages: Boolean

    override val cron: String?

    override val start: OffsetDateTime?

    override val duration: String?

    override val monitors: List<String>?

    override val integrations: List<String>?

    companion object {
        const val CONFIG_PREFIX = "maintenance-windows"
    }
}
