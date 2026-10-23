package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import kotlinx.html.*
import java.time.Duration

/**
 * [customOptionLabel] adds an option without a period, which can't be picked, only shown when something else than a
 * period is selected, e.g. a range of dates.
 */
internal fun FlowContent.periodSelector(
    selected: Duration,
    options: List<Duration> = UIDefaults.PERIOD_SELECTOR_OPTIONS,
    customOptionLabel: String? = null,
    configure: SELECT.() -> Unit = {},
) {
    select {
        classes(FORM_SELECT)
        configure()
        customOptionLabel?.let { label ->
            option {
                value = ""
                hidden = true
                +label
            }
        }
        options.forEach { duration ->
            option {
                value = duration.toString()
                this.selected = selected == duration
                +duration.formatAsSimpleInterval()
            }
        }
    }
}
