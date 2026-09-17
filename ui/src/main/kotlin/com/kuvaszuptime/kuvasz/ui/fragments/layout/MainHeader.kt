package com.kuvaszuptime.kuvasz.ui.fragments.layout

import com.iodesystems.htmx.Htmx.Companion.hx
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.settings.ConnectivityStatus
import com.kuvaszuptime.kuvasz.models.settings.VersionInfo
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.AppInfo
import kotlinx.html.*
import kotlinx.html.stream.*
import kotlin.time.Duration.Companion.seconds

internal const val DEFAULT_SITE_LOGO_URL = "/public/kuvasz-avatar.png"
internal const val DEFAULT_SITE_LOGO_ALT = AppInfo.NAME

const val CONNECTIVITY_BADGE_FRAGMENT_PATH = "/fragments/connectivity-badge"
private const val CONNECTIVITY_BADGE_CONTAINER_ID = "connectivity-badge-container"

fun renderConnectivityBadgeFragment(connectivityStatus: ConnectivityStatus?): String =
    if (connectivityStatus?.areChecksSuspended != true) {
        ""
    } else {
        createHTML(prettyPrint = false, xhtmlCompatible = false)
            .a(href = "/settings#connectivity-check-settings") {
                testId("connectivity-lost-badge")
                classes(BADGE, BADGE_SM, BG_YELLOW, MS_2, TEXT_YELLOW_FG)
                tooltip(Messages.connectivityLostWarning())
                icon(Icon.ALERT_TRIANGLE)
            }
    }

internal fun FlowContent.mainHeader(
    isAuthenticated: Boolean,
    navbarMenuId: String,
    versionInfo: VersionInfo,
    connectivityStatus: ConnectivityStatus?,
) {
    header {
        classes(NAVBAR, NAVBAR_EXPAND_MD)
        div {
            classes(CONTAINER_XL)
            // Navbar toggler
            if (isAuthenticated) {
                button(type = ButtonType.button) {
                    classes(NAVBAR_TOGGLER)
                    collapseToggler()
                    dataBsTarget("#$navbarMenuId")
                    ariaControls(navbarMenuId)
                    ariaExpanded(false)
                    ariaLabel(Messages.toggleNavigation())
                    span {
                        classes(NAVBAR_TOGGLER_ICON)
                    }
                }
            }
            // Navbar logo
            div {
                classes(NAVBAR_BRAND, NAVBAR_BRAND_AUTODARK, D_NONE_NAVBAR_HORIZONTAL, PE_0, PE_MD_3)
                a(href = "/") {
                    classes(TEXT_RESET, TEXT_DECORATION_NONE)
                    img(src = DEFAULT_SITE_LOGO_URL, alt = DEFAULT_SITE_LOGO_ALT) {
                        classes(ME_3)
                        width = "32"
                        height = "32"
                    }
                    +"Kuvasz"
                }
            }

            div {
                classes(NAVBAR_NAV, FLEX_ROW, ALIGN_ITEMS_CENTER, ORDER_MD_LAST, PX_0)
                if (isAuthenticated) {
                    if (connectivityStatus != null) {
                        div {
                            classes(NAV_ITEM, D_MD_FLEX, ME_2)
                            hx {
                                get(CONNECTIVITY_BADGE_FRAGMENT_PATH)
                                trigger {
                                    load()
                                    every(connectivityStatus.intervalSeconds.seconds)
                                }
                                onSwapReinitTooltips()
                            }
                            id = CONNECTIVITY_BADGE_CONTAINER_ID
                        }
                    }
                    div {
                        classes(NAV_ITEM, D_MD_FLEX, ME_2)
                        inlineVersionUpdateBadge(versionInfo)
                    }
                }
                darkModeToggle()
            }
        }
    }
}

internal fun FlowContent.darkModeToggle() {
    div {
        classes(NAV_ITEM, D_MD_FLEX)
        a(href = "#") {
            classes(NAV_LINK, PX_0, JUSTIFY_CONTENT_CENTER, HIDE_THEME_DARK)
            ariaLabel(Messages.enableDarkMode())
            onClick = "setTheme('dark')"
            testId("theme-toggle-dark")
            icon(Icon.DARK_MODE)
        }
        a(href = "#") {
            classes(NAV_LINK, PX_0, JUSTIFY_CONTENT_CENTER, HIDE_THEME_LIGHT)
            ariaLabel(Messages.enableLightMode())
            onClick = "setTheme('light')"
            testId("theme-toggle-light")
            icon(Icon.LIGHT_MODE)
        }
    }
}
