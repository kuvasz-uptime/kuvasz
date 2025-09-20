package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import kotlinx.html.*

internal fun FlowContent.statCard(
    cssClasses: Set<CSSClass>,
    icon: Icon,
    iconBackground: CSSClass,
    text: String,
    secondaryText: String,
) {
    div {
        classes(cssClasses)
        div {
            classes(CARD, CARD_SM)
            div {
                classes(CARD_STAMP)
                div {
                    classes(CARD_STAMP_ICON, iconBackground)
                    icon(icon)
                }
            }
            div {
                classes(CARD_BODY)
                div {
                    classes(ROW, ALIGN_ITEMS_CENTER, TEXT_CENTER)
                    div {
                        classes(CSSClass.COL)
                        div {
                            classes(CSSClass.H2)
                            +text
                        }
                        div {
                            classes(TEXT_SECONDARY, TEXT_UPPERCASE)
                            +secondaryText
                        }
                    }
                }
            }
        }
    }
}

internal fun FlowContent.incidentsStatsCards(cssClasses: Set<CSSClass>, stats: HistoricalUptimeStatsDto) =
    statCard(
        cssClasses,
        icon = Icon.ALERT_TRIANGLE,
        iconBackground = BG_RED_LT,
        text = stats.incidents.toString(),
        secondaryText = Messages.incidents()
    )

internal fun FlowContent.affectedMonitorsStatsCards(cssClasses: Set<CSSClass>, stats: HistoricalUptimeStatsDto) =
    statCard(
        cssClasses,
        icon = Icon.BINOCULARS,
        iconBackground = BG_RED_LT,
        text = stats.affectedMonitors.toString(),
        secondaryText = Messages.affectedMonitors(),
    )

internal fun FlowContent.uptimeRatioStatsCards(cssClasses: Set<CSSClass>, stats: HistoricalUptimeStatsDto) {
    val uptimeRatioText = stats.uptimeRatio?.formatAsPercentage() ?: Messages.noData()
    statCard(
        cssClasses,
        icon = Icon.PERCENTAGE,
        iconBackground = BG_GREEN_LT,
        text = uptimeRatioText,
        secondaryText = Messages.uptimeRatio(),
    )
}

internal fun FlowContent.totalDowntimeStatsCards(cssClasses: Set<CSSClass>, stats: HistoricalUptimeStatsDto) {
    val totalDowntimeText = stats.totalDowntimeSeconds
        .takeIf { it > 0 }
        ?.formatAsInterval()
        ?: "-"
    statCard(
        cssClasses,
        icon = Icon.SUM,
        iconBackground = BG_RED_LT,
        text = totalDowntimeText,
        secondaryText = Messages.totalDowntime(),
    )
}
