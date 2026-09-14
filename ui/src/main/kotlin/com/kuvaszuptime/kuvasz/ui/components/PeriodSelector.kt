package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import kotlinx.html.*
import java.time.Duration

private const val ONE_HOUR = 1L
private const val SIX_HOURS = 6L
private const val TWELVE_HOURS = 12L
private const val ONE_DAY = 1L
private const val SEVEN_DAYS = 7L
private const val THIRTY_DAYS = 30L

internal val PERIOD_SELECTOR_OPTIONS: List<Duration> = listOf(
    Duration.ofHours(ONE_HOUR),
    Duration.ofHours(SIX_HOURS),
    Duration.ofHours(TWELVE_HOURS),
    Duration.ofDays(ONE_DAY),
    Duration.ofDays(SEVEN_DAYS),
    Duration.ofDays(THIRTY_DAYS),
)

internal fun FlowContent.periodSelector(
    selected: Duration,
    options: List<Duration> = PERIOD_SELECTOR_OPTIONS,
    configure: SELECT.() -> Unit = {},
) {
    select {
        classes(FORM_SELECT)
        configure()
        options.forEach { duration ->
            option {
                value = duration.toString()
                this.selected = selected == duration
                +duration.formatAsSimpleInterval()
            }
        }
    }
}
