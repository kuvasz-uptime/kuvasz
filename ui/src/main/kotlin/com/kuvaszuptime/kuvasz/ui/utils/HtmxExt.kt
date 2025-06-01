package com.kuvaszuptime.kuvasz.ui.utils

import com.iodesystems.htmx.HtmxAttrs

internal fun HtmxAttrs.onSwapReinitTooltips() {
    on("htmx:after-swap", "reInitTooltips()")
}
