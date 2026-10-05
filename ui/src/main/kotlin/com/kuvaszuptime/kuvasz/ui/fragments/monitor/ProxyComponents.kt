package com.kuvaszuptime.kuvasz.ui.fragments.monitor

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

/**
 * The proxy field: a single-value TomSelect over the proxies of the YAML config, which is the only place they can be
 * defined, and clearing it means a direct connection.
 *
 * A monitor may name a proxy that has since been removed from the config, and that value has to survive an edit that
 * does not touch this field, so it is rendered as an option of its own, marked as no longer configured.
 */
internal fun FlowContent.proxySelector(
    selectId: String,
    description: String,
    isReadOnlyMode: Boolean,
    currentProxy: String?,
    configuredProxies: List<String>,
) {
    formLabel(
        label = Messages.proxyLabel(),
        inputName = selectId,
        description = description,
        required = false,
    )
    select {
        classes(FORM_SELECT)
        id = selectId
        name = selectId
        attributes["placeholder"] = Messages.proxyDirectConnection()
        xModel("proxy")
        xBindErrorClass("proxy")
        // Deferred, because x-model writes the picked value on this very event and may do so after this handler
        xOnChange("\$nextTick(() => validateProxy())")
        xInitNextTick("{ initProxySelect('#$selectId') }")
        if (isReadOnlyMode) disabled = true
        // What Alpine binds to while no proxy is picked, i.e. a direct connection
        option { value = "" }
        configuredProxies.forEach { proxy ->
            option {
                value = proxy
                selected = proxy == currentProxy
                +proxy
            }
        }
        currentProxy?.takeIf { it !in configuredProxies }?.let { danglingProxy ->
            option {
                value = danglingProxy
                selected = true
                +Messages.proxyNotConfiguredOption(danglingProxy)
            }
        }
    }
    templateTag {
        xIf("errors.proxy")
        div {
            classes(INVALID_FEEDBACK, D_BLOCK)
            xText("errors.proxy")
        }
    }
    if (configuredProxies.isEmpty()) {
        div {
            classes(FORM_TEXT, TEXT_SECONDARY)
            +Messages.proxyNoneConfigured()
        }
    }
}

internal fun UL.listItemProxyBadge(proxy: String, configuredProxies: List<String>) {
    li {
        classes(LIST_INLINE_ITEM, ALIGN_MIDDLE)
        testId("proxy-badge")
        proxyBadge(proxy, configuredProxies)
    }
}
