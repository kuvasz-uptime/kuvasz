package com.kuvaszuptime.kuvasz.ui.fragments.layout

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

internal fun FlowContent.mainHeader(
    isReadOnlyMode: Boolean,
    isAuthenticated: Boolean,
    navbarMenuId: String,
) {
    header {
        classes(NAVBAR, NAVBAR_EXPAND_MD, D_PRINT_NONE)
        div {
            classes(CONTAINER_XL)
            // Navbar toggler
            if (isAuthenticated) {
                button(type = ButtonType.button) {
                    classes(NAVBAR_TOGGLER)
                    dataBsToggle("collapse")
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
                    img(src = "/public/kuvasz-avatar.png", alt = "Kuvasz") {
                        classes(ME_3)
                        width = "32"
                        height = "32"
                    }
                    +"Kuvasz"
                }
            }

            div {
                classes(NAVBAR_NAV, FLEX_ROW, ORDER_MD_LAST)
                // Read only notice
                if (isReadOnlyMode) {
                    div {
                        classes(NAV_ITEM, D_MD_FLEX, ME_3)
                        compactIconButton(Icon.LOCK_COG, setOf(PX_0)) {
                            tooltip(title = Messages.readOnlyNotice())
                        }
                    }
                }
                // Dark and light mode toggles
                div {
                    classes(NAV_ITEM, D_MD_FLEX)
                    a(href = "#") {
                        classes(NAV_LINK, PX_0, HIDE_THEME_DARK)
                        ariaLabel(Messages.enableDarkMode())
                        onClick = "setTheme('dark')"
                        icon(Icon.DARK_MODE)
                    }
                    a(href = "#") {
                        classes(NAV_LINK, PX_0, HIDE_THEME_LIGHT)
                        ariaLabel(Messages.enableLightMode())
                        onClick = "setTheme('light')"
                        icon(Icon.LIGHT_MODE)
                    }
                }
            }
        }
    }
}
