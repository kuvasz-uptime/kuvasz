package com.kuvaszuptime.kuvasz.ui.fragments.monitor.push

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*

fun renderPushMonitorList(
    monitors: List<PushMonitorDetailsDto>,
    editabilityState: AppGlobals.EditabilityState,
): String =
    renderMonitorList(
        monitors = monitors,
        typeUiConfig = MonitorTypeUiConfig.PUSH,
        editabilityState = editabilityState,
        columns = listOf(
            uptimeStatusChangedColumn(),
            // A push monitor is driven by the heartbeats of its client, not by checks Kuvasz makes on its own
            timestampColumn(Messages.lastHeartbeat(), D_SM_TABLE_CELL) { it.lastHeartbeat },
        ),
    )
