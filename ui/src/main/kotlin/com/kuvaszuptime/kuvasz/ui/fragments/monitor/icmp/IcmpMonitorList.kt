package com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

fun renderIcmpMonitorList(
    monitors: List<IcmpMonitorDetailsDto>,
    editabilityState: AppGlobals.EditabilityState,
): String =
    renderMonitorList(
        monitors = monitors,
        typeUiConfig = MonitorTypeUiConfig.ICMP,
        editabilityState = editabilityState,
        columns = listOf(
            uptimeStatusChangedColumn(),
            timestampColumn(Messages.lastCheck(), D_SM_TABLE_CELL) { it.lastUptimeCheck },
        ),
    )
