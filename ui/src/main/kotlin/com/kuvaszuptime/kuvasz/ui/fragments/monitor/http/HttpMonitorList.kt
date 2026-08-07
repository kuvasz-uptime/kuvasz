package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

fun renderHttpMonitorList(
    monitors: List<HttpMonitorDetailsDto>,
    editabilityState: AppGlobals.EditabilityState,
): String =
    renderMonitorList(
        monitors = monitors,
        typeUiConfig = MonitorTypeUiConfig.HTTP,
        editabilityState = editabilityState,
        columns = listOf(
            MonitorListColumn(
                header = "SSL",
                headerClasses = setOf(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER),
                cellClasses = setOf(D_NONE, D_MD_TABLE_CELL, TEXT_CENTER),
                cell = { monitor -> sslStatusOfMonitor(monitor, withTooltip = true) },
            ),
            uptimeStatusChangedColumn(),
            timestampColumn(Messages.nextCheck(), D_LG_TABLE_CELL) { it.nextUptimeCheck },
        ),
        nameTooltip = { it.url.toString() },
    )
