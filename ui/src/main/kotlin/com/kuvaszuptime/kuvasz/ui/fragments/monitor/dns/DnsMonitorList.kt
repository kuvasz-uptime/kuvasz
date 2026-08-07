package com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

fun renderDnsMonitorList(
    monitors: List<DnsMonitorDetailsDto>,
    editabilityState: AppGlobals.EditabilityState,
): String =
    renderMonitorList(
        monitors = monitors,
        typeUiConfig = MonitorTypeUiConfig.DNS,
        editabilityState = editabilityState,
        columns = listOf(
            uptimeStatusChangedColumn(),
            timestampColumn(Messages.nextCheck(), D_SM_TABLE_CELL) { it.nextUptimeCheck },
        ),
    )
