package com.kuvaszuptime.kuvasz.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.theme.ThemeBase
import com.kuvaszuptime.kuvasz.models.theme.ThemeOption
import com.kuvaszuptime.kuvasz.models.theme.ThemePrimary
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.layout.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import com.kuvaszuptime.kuvasz.util.AppInfo
import kotlinx.html.*
import kotlinx.html.stream.*

private const val DEFAULT_TITLE = AppInfo.NAME
internal const val DOCTYPE_NOTATION = "<!DOCTYPE html>"

internal fun withLayout(
    globals: AppGlobals,
    title: String? = null,
    pageTitle: HtmlBlockTag.() -> Unit = {},
    content: HtmlBlockTag.() -> Unit = {},
): String {
    // This is a tiny hack to have a DOCTYPE notation without using kotlinx.html's own document, because it's not
    // compatible with custom attr namespaces like `x-on` or `x-bind`, etc.
    return DOCTYPE_NOTATION +
        createHTML(prettyPrint = false, xhtmlCompatible = false)
            .html {
                withThemeBase()
                head {
                    commonHeadElements(
                        appVersion = globals.appVersion,
                        applyThemePreferences = true,
                        faviconsAndManifest = { defaultFaviconsAndManifest() },
                    )
                    title {
                        title?.let { +"$it | $DEFAULT_TITLE" } ?: +DEFAULT_TITLE
                    }
                    link(rel = "stylesheet", href = "/public/ext/css/tomselect.2.6.2.bootstrap5.min.css")
                    link(rel = "stylesheet", href = "/public/ext/css/tabler-vendors.1.6.0.min.css")
                    script(src = "/public/ext/js/apexcharts.7.1.0.min.js") {}
                    script(src = "/public/ext/js/tomselect.2.6.2.complete.min.js") {}
                }
                body {
                    div {
                        id = "toast-container"
                        classes(TOAST_CONTAINER, POSITION_ABSOLUTE, P_3, BOTTOM_0, END_0)
                    }
                    div {
                        classes(PAGE)
                        div {
                            classes(STICKY_TOP)
                            // Main header
                            val navbarMenuId = "navbar-menu"
                            mainHeader(
                                isAuthenticated = globals.isAuthenticated(),
                                navbarMenuId = navbarMenuId,
                                versionInfo = globals.versionInfo(),
                                connectivityStatus = globals.connectivityStatus(),
                            )
                            // Navigation - only if logged in
                            if (globals.isAuthenticated()) {
                                navigation(
                                    isAuthEnabled = globals.isAuthEnabled,
                                    isOidcLogoutEnabled = globals.isOidcLogoutEnabled,
                                    navbarMenuId = navbarMenuId,
                                )
                            }
                        }
                        div {
                            classes(PAGE_WRAPPER)
                            // Page header
                            div {
                                classes(PAGE_HEADER)
                                pageTitle()
                            }
                            // Page body
                            div {
                                classes(PAGE_BODY)
                                div {
                                    classes(CONTAINER_XL)
                                    content()
                                }
                            }
                            // Footer
                            if (globals.isAuthenticated()) {
                                footer(globals.versionInfo())
                            }
                        }
                    }
                    // Tabler's datepicker is a wrapper around Vanilla Calendar Pro, which has to be loaded before it
                    script(src = "/public/ext/js/vanilla-calendar-pro.3.3.2.min.js") {}
                    commonScripts(globals.appVersion)
                    script(src = "/public/ext/js/htmx.2.0.10.min.js") {}
                    script(src = "/public/ext/js/alpine.3.17.4.min.js") {}
                    script(src = "/public/ext/js/masonry.4.2.2.min.js") {}
                }
            }
}

/**
 * @param applyThemePreferences whether the gray palette and the accent color picked on the Settings page are applied,
 * which isn't the case for the public status pages, as they come with their own theme
 **/
internal fun FlowOrMetaDataOrPhrasingContent.commonHeadElements(
    appVersion: String,
    applyThemePreferences: Boolean = false,
    faviconsAndManifest: FlowOrMetaDataOrPhrasingContent.() -> Unit,
) {
    meta(charset = "utf-8")
    meta(name = "viewport", content = "width=device-width, initial-scale=1")
    faviconsAndManifest()
    script {
        unsafe {
            // Setting the theme based on user preference eagerly
            +"""
            (function() {
                const savedTheme = localStorage.getItem('kuvasz-theme') || 'dark';
                document.documentElement.setAttribute('data-bs-theme', savedTheme);
            })();
            """.trimIndent()
            if (applyThemePreferences) {
                +themePreferenceLoader("base", ThemeBase.entries)
                +themePreferenceLoader("primary", ThemePrimary.entries)
            }
        }
    }
    link(rel = "stylesheet", href = "/public/ext/css/tabler.1.6.0.min.css")
    link(rel = "stylesheet", href = "/public/ext/css/tabler-themes.1.6.0.min.css")
    link(rel = "stylesheet", href = "/public/css/kuvasz.css?cb=$appVersion")
}

// Applies a saved theme option (see setThemeOption in kuvasz.js), unless it's not one of the known values anymore
private fun themePreferenceLoader(option: String, values: List<ThemeOption>): String {
    val knownValues = values.joinToString(", ") { "'${it.value}'" }
    return """
        (function() {
            const saved = localStorage.getItem('kuvasz-theme-$option');
            if ([$knownValues].includes(saved)) {
                document.documentElement.setAttribute('data-bs-theme-$option', saved);
            }
        })();
    """.trimIndent()
}

internal fun HTML.withThemeBase(base: ThemeBase? = null) {
    attributes["data-bs-theme-base"] = (base ?: ThemeBase.DEFAULT).value
}

internal fun FlowOrMetaDataOrPhrasingContent.commonScripts(appVersion: String) {
    script(src = "/public/ext/js/tabler.1.6.0.min.js") {}
    script(src = "/public/dist/js/kuvasz.min.js?cb=$appVersion") {}
}

internal fun FlowOrMetaDataOrPhrasingContent.defaultFaviconsAndManifest() {
    link(rel = "apple-touch-icon", href = "/public/apple-touch-icon.png") { sizes = "180x180" }
    link(rel = "icon", href = "/public/favicon-32x32.png", type = "image/png") { sizes = "32x32" }
    link(rel = "icon", href = "/public/favicon-16x16.png", type = "image/png") { sizes = "16x16" }
    link(rel = "manifest", href = "/public/site.webmanifest")
}
