package com.kuvaszuptime.kuvasz.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.fragments.layout.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*
import kotlinx.html.stream.*

private const val DEFAULT_TITLE = "Kuvasz Uptime"

internal fun withLayout(
    globals: AppGlobals,
    title: String? = null,
    pageTitle: HtmlBlockTag.() -> Unit = {},
    content: HtmlBlockTag.() -> Unit = {},
): String {
    // This is a tiny hack to have a DOCTYPE notation without using kotlinx.html's own document, because it's not
    // compatible with custom attr namespaces like `x-on` or `x-bind`, etc.
    return "<!DOCTYPE html>" +
        createHTML(prettyPrint = false, xhtmlCompatible = false)
            .html {
                head {
                    meta(charset = "utf-8")
                    meta(name = "viewport", content = "width=device-width, initial-scale=1")
                    title {
                        title?.let { +"$it | $DEFAULT_TITLE" } ?: +DEFAULT_TITLE
                    }
                    link(rel = "apple-touch-icon", href = "/public/apple-touch-icon.png") { sizes = "180x180" }
                    link(rel = "icon", href = "/public/favicon-32x32.png", type = "image/png") { sizes = "32x32" }
                    link(rel = "icon", href = "/public/favicon-16x16.png", type = "image/png") { sizes = "16x16" }
                    link(rel = "manifest", href = "/public/site.webmanifest")
                    script {
                        unsafe {
                            // Setting the theme based on user preference eagerly
                            +"""
                            (function() {
                                const savedTheme = localStorage.getItem('kuvasz-theme') || 'light';
                                document.documentElement.setAttribute('data-bs-theme', savedTheme);
                            })();
                            """.trimIndent()
                        }
                    }
                    link(rel = "stylesheet", href = "/public/ext/css/tabler.min.css")
                    link(rel = "stylesheet", href = "/public/ext/css/tabler-vendors.min.css")
                    script(src = "/public/ext/js/apexcharts.min.js") {}
                }
                body {
                    div {
                        classes(PAGE)
                        // Main header
                        val navbarMenuId = "navbar-menu"
                        mainHeader(
                            isReadOnlyMode = globals.isReadOnlyMode(),
                            isAuthenticated = globals.isAuthenticated(),
                            navbarMenuId = navbarMenuId,
                        )
                        // Navigation - only if logged in
                        if (globals.isAuthenticated()) {
                            navigation(isAuthEnabled = globals.isAuthEnabled, navbarMenuId = navbarMenuId)
                        }
                        div {
                            classes(PAGE_WRAPPER)
                            // Page header
                            div {
                                classes(PAGE_HEADER, D_PRINT_NONE)
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
                            footer(globals.appVersion)
                        }
                    }
                    script(src = "/public/ext/js/tabler.min.js") {}
                    script(src = "/public/dist/js/kuvasz.min.js?cb=${globals.appVersion}") {}
                    script(src = "/public/ext/js/htmx.2.0.5.min.js") {}
                    script(src = "/public/ext/js/alpine.3.min.js") {}
                    script(src = "/public/ext/js/masonry.4.2.2.min.js") {}
                }
            }
}
