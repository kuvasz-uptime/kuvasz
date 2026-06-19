package com.kuvaszuptime.kuvasz.ui.fragments.layout

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.settings.VersionInfo
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal const val DEFAULT_SITE_LOGO_URL = "/public/kuvasz-avatar.png"
internal const val DEFAULT_SITE_LOGO_ALT = "Kuvasz Uptime"

internal fun FlowContent.mainHeader(
    isAuthenticated: Boolean,
    navbarMenuId: String,
    versionInfo: VersionInfo,
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
                classes(NAVBAR_NAV, FLEX_ROW, ORDER_MD_LAST)
                div {
                    classes(NAV_ITEM, D_MD_FLEX, ME_2)
                    inlineVersionUpdateBadge(versionInfo)
                }
                // Dark and light mode toggles
                div {
                    classes(NAV_ITEM, D_MD_FLEX)
                    a(href = "#") {
                        classes(NAV_LINK, PX_0, HIDE_THEME_DARK)
                        ariaLabel(Messages.enableDarkMode())
                        onClick = "setTheme('dark')"
                        testId("theme-toggle-dark")
                        icon(Icon.DARK_MODE)
                    }
                    a(href = "#") {
                        classes(NAV_LINK, PX_0, HIDE_THEME_LIGHT)
                        ariaLabel(Messages.enableLightMode())
                        onClick = "setTheme('light')"
                        testId("theme-toggle-light")
                        icon(Icon.LIGHT_MODE)
                    }
                }
            }
        }
    }
}
