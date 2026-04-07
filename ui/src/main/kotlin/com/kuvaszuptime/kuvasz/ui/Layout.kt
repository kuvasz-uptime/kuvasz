package com.kuvaszuptime.kuvasz.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.layout.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

private const val DEFAULT_TITLE = "Kuvasz Uptime"
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
                head {
                    commonHeadElements(
                        appVersion = globals.appVersion,
                        faviconsAndManifest = { defaultFaviconsAndManifest() },
                    )
                    title {
                        title?.let { +"$it | $DEFAULT_TITLE" } ?: +DEFAULT_TITLE
                    }
                    link(rel = "stylesheet", href = "/public/ext/css/tomselect.2.4.3.bootstrap5.min.css")
                    link(rel = "stylesheet", href = "/public/ext/css/tabler-vendors.1.4.0.min.css")
                    script(src = "/public/ext/js/apexcharts.3.54.1.min.js") {}
                    script(src = "/public/ext/js/tomselect.2.4.3.complete.min.js") {}
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
                            )
                            // Navigation - only if logged in
                            if (globals.isAuthenticated()) {
                                navigation(isAuthEnabled = globals.isAuthEnabled, navbarMenuId = navbarMenuId)
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
                    commonScripts(globals.appVersion)
                    script(src = "/public/ext/js/htmx.2.0.8.min.js") {}
                    script(src = "/public/ext/js/alpine.3.15.11.min.js") {}
                    script(src = "/public/ext/js/masonry.4.2.2.min.js") {}
                }
            }
}

internal fun FlowOrMetaDataOrPhrasingContent.commonHeadElements(
    appVersion: String,
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
        }
    }
    link(rel = "preconnect", href = "https://fonts.googleapis.com")
    link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "" }
    link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&display=swap")
    link(rel = "stylesheet", href = "/public/ext/css/tabler.1.4.0.min.css")
    link(rel = "stylesheet", href = "/public/css/kuvasz.css?cb=$appVersion")
}

internal fun FlowOrMetaDataOrPhrasingContent.commonScripts(appVersion: String) {
    script(src = "/public/ext/js/tabler.1.4.0.min.js") {}
    script(src = "/public/dist/js/kuvasz.min.js?cb=$appVersion") {}
}

internal fun FlowOrMetaDataOrPhrasingContent.defaultFaviconsAndManifest() {
    link(rel = "apple-touch-icon", href = "/public/apple-touch-icon.png") { sizes = "180x180" }
    link(rel = "icon", href = "/public/favicon-32x32.png", type = "image/png") { sizes = "32x32" }
    link(rel = "icon", href = "/public/favicon-16x16.png", type = "image/png") { sizes = "16x16" }
    link(rel = "manifest", href = "/public/site.webmanifest")
}
