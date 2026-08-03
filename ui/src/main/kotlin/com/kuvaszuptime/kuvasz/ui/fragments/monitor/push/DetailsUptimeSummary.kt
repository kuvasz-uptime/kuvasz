package com.kuvaszuptime.kuvasz.ui.fragments.monitor.push

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderPushUptimeSummary(monitor: PushMonitorDetailsDto, stats: HistoricalUptimeStatsDto): String =
    buildString { appendHTML().div { detailsPushUptimeSummary(monitor, stats) } }

fun FlowContent.detailsPushUptimeSummary(monitor: PushMonitorDetailsDto, stats: HistoricalUptimeStatsDto) =
    monitorUptimeSummary(
        typeUiConfig = MonitorTypeUiConfig.PUSH,
        monitor = monitor,
        stats = stats,
        statsPeriodInDays = UIDefaults.PUSH_MONITOR_UPTIME_STATS_PERIOD_DAYS,
        // A push monitor is driven by the heartbeats its client sends, not by checks Kuvasz makes on its own
        pendingLabel = Messages.waitingForFirstHeartbeat(),
        lastCheckLabel = Messages.lastHeartbeat(),
        lastCheckAt = monitor.lastHeartbeat,
        nextCheckLabel = Messages.heartbeatExpected(),
        nextCheckAt = monitor.nextExpectedHeartbeat,
        nextCheckPendingLabel = Messages.waitingForFirstHeartbeat(),
    )
