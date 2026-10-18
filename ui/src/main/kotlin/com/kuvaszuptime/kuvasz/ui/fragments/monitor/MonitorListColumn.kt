package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import java.time.OffsetDateTime

/**
 * A column of a monitor list beyond the name and the status ones, which every type has. It can be sorted by
 * [sortField], if there is one for it.
 */
internal class MonitorListColumn<in T : MonitorDetailsDto, F : Enum<F>>(
    val header: String,
    val headerClasses: Set<CSSClass>,
    val cellClasses: Set<CSSClass>,
    val sortField: F?,
    val cell: FlowContent.(T) -> Unit,
)

/**
 * A column showing a timestamp as a "time ago" label with the exact value in a tooltip.
 */
internal fun <T : MonitorDetailsDto, F : Enum<F>> timestampColumn(
    header: String,
    breakpoint: CSSClass,
    sortField: F?,
    value: (T) -> OffsetDateTime?,
) = MonitorListColumn<T, F>(
    header = header,
    headerClasses = setOf(D_NONE, breakpoint, TEXT_CENTER),
    cellClasses = setOf(TEXT_NOWRAP, D_NONE, breakpoint, TEXT_CENTER),
    sortField = sortField,
    cell = { monitor ->
        span {
            value(monitor)?.let { timestamp ->
                tooltip(title = timestamp.toDateTimeString())
                +timestamp.timeAgo()
            }
        }
    },
)

/**
 * The column showing since when the monitor has been in its current uptime status.
 */
internal fun <T : MonitorDetailsDto, F : Enum<F>> uptimeStatusChangedColumn(sortField: F) =
    timestampColumn<T, F>(Messages.changed(), D_LG_TABLE_CELL, sortField) { it.uptimeStatusStartedAt }

/**
 * The column showing when the monitor is checked next. It can't be sorted by, since the time of the next check is
 * known only by the scheduler, not by the database.
 */
internal fun <T : MonitorDetailsDto, F : Enum<F>> nextCheckColumn(breakpoint: CSSClass, value: (T) -> OffsetDateTime?) =
    timestampColumn<T, F>(Messages.nextCheck(), breakpoint, sortField = null, value)
