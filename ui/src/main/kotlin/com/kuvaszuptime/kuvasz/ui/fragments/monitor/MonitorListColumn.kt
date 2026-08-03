package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import java.time.OffsetDateTime

/**
 * A column of a monitor list beyond the name and the status ones, which every type has.
 */
internal class MonitorListColumn<in T : MonitorDetailsDto>(
    val header: String,
    val headerClasses: Set<CSSClass>,
    val cellClasses: Set<CSSClass>,
    val cell: FlowContent.(T) -> Unit,
)

/** A column showing a timestamp as a "time ago" label with the exact value in a tooltip. */
internal fun <T : MonitorDetailsDto> timestampColumn(
    header: String,
    breakpoint: CSSClass,
    value: (T) -> OffsetDateTime?,
) = MonitorListColumn<T>(
    header = header,
    headerClasses = setOf(D_NONE, breakpoint, TEXT_CENTER),
    cellClasses = setOf(TEXT_NOWRAP, D_NONE, breakpoint, TEXT_CENTER),
    cell = { monitor ->
        span {
            value(monitor)?.let { timestamp ->
                tooltip(title = timestamp.toDateTimeString())
                +timestamp.timeAgo()
            }
        }
    },
)

/** The column showing since when the monitor has been in its current uptime status. */
internal fun <T : MonitorDetailsDto> uptimeStatusChangedColumn() =
    timestampColumn<T>(Messages.changed(), D_LG_TABLE_CELL) { it.uptimeStatusStartedAt }
