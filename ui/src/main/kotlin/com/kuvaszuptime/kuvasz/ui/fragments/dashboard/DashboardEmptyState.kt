package com.kuvaszuptime.kuvasz.ui.fragments.dashboard

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun renderDashboardEmptyState(): String =
    createHTML(prettyPrint = false, xhtmlCompatible = false)
        .div {
            emptyState(
                icon = Icon.HEART_RATE_MONITOR,
                title = Messages.noMonitorsYet(),
                subtitle = Messages.noMonitorsYetDescription(),
            )
        }
