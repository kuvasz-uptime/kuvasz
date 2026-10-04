package com.kuvaszuptime.kuvasz.ui.fragments.monitor.http

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.proxyBadge(proxy: String, configuredProxies: List<String>) =
    if (proxy in configuredProxies) {
        inlineStatusBadge(
            text = proxy,
            icon = Icon.ROUTE_SQUARE,
            tooltip = Messages.proxyBadgeTooltip(proxy),
        )
    } else {
        inlineStatusBadge(
            text = Messages.proxyNotConfiguredBadge(),
            color = Color.ORANGE_LT,
            icon = Icon.ALERT_TRIANGLE,
            tooltip = Messages.proxyNotConfiguredTooltip(proxy),
        )
    }

internal fun FlowContent.proxyIconBadge(proxy: String, configuredProxies: List<String>) {
    span {
        if (proxy in configuredProxies) {
            testId("proxy-badge")
            classes(BADGE, MS_2)
            tooltip(Messages.proxyBadgeTooltip(proxy))
            icon(Icon.ROUTE_SQUARE)
        } else {
            testId("proxy-not-configured-badge")
            classes(BADGE, Color.ORANGE_LT.bgColor, Color.ORANGE_LT.textColor, MS_2)
            tooltip(Messages.proxyNotConfiguredTooltip(proxy))
            icon(Icon.ALERT_TRIANGLE)
        }
    }
}
