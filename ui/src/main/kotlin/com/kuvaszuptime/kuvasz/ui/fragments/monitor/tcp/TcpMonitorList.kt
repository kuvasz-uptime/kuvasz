package com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

fun renderTcpMonitorList(
    monitors: List<TcpMonitorDetailsDto>,
    editabilityState: AppGlobals.EditabilityState,
): String =
    renderMonitorList(
        monitors = monitors,
        typeUiConfig = MonitorTypeUiConfig.TCP,
        editabilityState = editabilityState,
        columns = listOf(
            uptimeStatusChangedColumn(),
            timestampColumn(Messages.nextCheck(), D_SM_TABLE_CELL) { it.nextUptimeCheck },
        ),
    )
