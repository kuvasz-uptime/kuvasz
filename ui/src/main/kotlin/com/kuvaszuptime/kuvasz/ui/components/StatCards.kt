package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.formatAsInterval
import kotlinx.html.*

private val DEFAULT_ICON_COLOR = BG_GRAY_700

internal fun FlowContent.statCard(
    cssClasses: Set<CSSClass>,
    icon: Icon,
    iconBackground: CSSClass,
    text: String,
    secondaryText: String,
) {
    div {
        classes(cssClasses)
        testId("stat-card")
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
                            classes(H2)
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

internal fun FlowContent.numericStatCard(
    cssClasses: Set<CSSClass>,
    icon: Icon,
    iconBackground: CSSClass,
    value: Long,
    secondaryText: String,
) = statCard(
    cssClasses,
    icon = icon,
    iconBackground = if (value > 0) iconBackground else DEFAULT_ICON_COLOR,
    text = value.toString(),
    secondaryText = secondaryText,
)

internal fun FlowContent.incidentsStatsCards(cssClasses: Set<CSSClass>, stats: HistoricalUptimeStatsDto) =
    statCard(
        cssClasses,
        icon = Icon.ALERT_TRIANGLE,
        iconBackground = if (stats.incidents > 0) BG_RED_LT else DEFAULT_ICON_COLOR,
        text = stats.incidents.toString(),
        secondaryText = Messages.incidents()
    )

internal fun FlowContent.uptimeRatioStatsCards(cssClasses: Set<CSSClass>, stats: HistoricalUptimeStatsDto) {
    val uptimeRatioText = stats.uptimeRatio?.formatAsPercentage() ?: Messages.noData()
    val defaultedRatio = stats.uptimeRatio ?: 1.0 // Consider unknown as "good"
    statCard(
        cssClasses,
        icon = Icon.PERCENTAGE,
        iconBackground = when {
            defaultedRatio < 0.5 -> BG_RED_LT
            defaultedRatio < 1.0 -> BG_ORANGE_LT
            else -> DEFAULT_ICON_COLOR
        },
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
        iconBackground = if (stats.totalDowntimeSeconds > 0) BG_RED_LT else DEFAULT_ICON_COLOR,
        text = totalDowntimeText,
        secondaryText = Messages.totalDowntime(),
    )
}
