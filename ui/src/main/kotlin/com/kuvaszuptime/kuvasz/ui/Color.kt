package com.kuvaszuptime.kuvasz.ui

import com.kuvaszuptime.kuvasz.ui.CSSClass.*

enum class Color(internal val bgColor: CSSClass, internal val textColor: CSSClass) {
    DEFAULT(BG_DEFAULT, TEXT_DEFAULT_FG),
    SUCCESS(BG_SUCCESS, TEXT_GREEN_FG),
    YELLOW_LT(BG_YELLOW_LT, TEXT_YELLOW_LT_FG),
    GREEN_LT(BG_GREEN_LT, TEXT_GREEN_LT_FG),
    BLUE_LT(BG_BLUE_LT, TEXT_BLUE_LT_FG),
}
