package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import kotlinx.html.*
import java.time.Duration

internal fun FlowContent.periodSelector(
    selected: Duration,
    options: List<Duration> = UIDefaults.PERIOD_SELECTOR_OPTIONS,
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
