package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.durationBetween
import com.kuvaszuptime.kuvasz.util.timeAgo
import kotlinx.html.*
import java.time.OffsetDateTime

private fun FlowContent.summaryCard(body: FlowContent.() -> Unit) {
    div {
        classes(COL_MD_4)
        div {
            classes(CARD)
            body()
        }
    }
}

private fun FlowContent.summaryCardBody(subheader: String, value: FlowContent.() -> Unit) {
    div {
        classes(CARD_BODY)
        div {
            classes(SUBHEADER)
            +subheader
        }
        value()
    }
}

private fun FlowContent.summaryCardValue(text: String, tooltipText: String? = null) {
    h4 {
        classes(M_0)
        tooltipText?.let { tooltip(it) }
        +text
    }
}

/**
 * The card showing how long the monitor has been in its current uptime status. [pendingLabel] is what a monitor that
 * has not been checked yet shows, which is heartbeat-flavoured for push monitors.
 */
private fun FlowContent.uptimeStatusCard(monitor: MonitorDetailsDto, pendingLabel: String) {
    summaryCard {
        div {
            classes(CARD_STAMP)
            div {
                classes(mutableSetOf(CARD_STAMP_ICON).addIfNotNull(getUptimeCardStatusClass(monitor)))
                icon(getUptimeCardIcon(monitor))
            }
        }
        div {
            classes(mutableSetOf(CARD_STATUS_START).addIfNotNull(getUptimeCardStatusClass(monitor)))
        }
        div {
            classes(CARD_BODY)
            when {
                !monitor.enabled -> {
                    div {
                        classes(SUBHEADER)
                        +Messages.currentUptimeStatus()
                    }
                    summaryCardValue(Messages.monitorIsPaused())
                }

                monitor.uptimeStatusStartedAt != null -> {
                    div {
                        classes(SUBHEADER)
                        +Messages.currentlyFor(monitor.uptimeStatus?.literal.orEmpty())
                    }
                    summaryCardValue(monitor.uptimeStatusStartedAt?.durationBetween().orEmpty())
                }

                else -> {
                    div {
                        classes(SUBHEADER)
                        +Messages.monitorWasJustCreated()
                    }
                    summaryCardValue(pendingLabel)
                }
            }
        }
    }
}

/**
 * The whole uptime summary of a monitor's details page. Push monitors track heartbeats rather than checks they make
 * themselves, so the labels and the timestamps of the last two cards are given by the caller.
 */
internal fun FlowContent.monitorUptimeSummary(
    typeUiConfig: MonitorTypeUiConfig,
    monitor: MonitorDetailsDto,
    stats: HistoricalUptimeStatsDto,
    statsPeriodInDays: Long,
    pendingLabel: String,
    lastCheckLabel: String,
    lastCheckAt: OffsetDateTime?,
    nextCheckLabel: String,
    nextCheckAt: OffsetDateTime?,
    // What a monitor that is enabled but has no next check scheduled yet shows
    nextCheckPendingLabel: String = "",
) {
    div {
        id = "${typeUiConfig.slug}-monitor-details-uptime-summary"
        hx { swapOob() }

        div {
            classes(ROW, ROW_CARDS, MB_3)
            uptimeStatusCard(monitor, pendingLabel)

            summaryCard {
                summaryCardBody(lastCheckLabel) {
                    lastCheckAt
                        ?.let { summaryCardValue(it.timeAgo(), tooltipText = it.toDateTimeString()) }
                        ?: summaryCardValue(pendingLabel)
                }
            }

            summaryCard {
                summaryCardBody(nextCheckLabel) {
                    if (monitor.enabled) {
                        summaryCardValue(nextCheckAt?.timeAgo() ?: nextCheckPendingLabel)
                    } else {
                        summaryCardValue(Messages.monitorIsPaused())
                    }
                }
            }
        }

        div {
            classes(ROW, ROW_CARDS, MB_3)
            // Historical stats
            h3 {
                classes(MT_3, MB_0)
                +Messages.metrics()
                inlineBadge(Messages.lastXDays(statsPeriodInDays))
            }
            incidentsStatsCards(cssClasses = setOf(COL_12, COL_MD_4), stats)
            uptimeRatioStatsCards(cssClasses = setOf(COL_12, COL_MD_4), stats)
            totalDowntimeStatsCards(cssClasses = setOf(COL_12, COL_MD_4), stats)
        }
    }
}
