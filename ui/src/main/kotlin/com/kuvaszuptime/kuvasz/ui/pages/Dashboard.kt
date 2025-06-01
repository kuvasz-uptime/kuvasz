package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.fragments.layout.*
import kotlinx.html.*

fun renderDashboard(globals: AppGlobals) =
    withLayout(
        globals,
        title = Messages.dashboard(),
        pageTitle = { simplePageHeader(preTitle = Messages.overview(), title = Messages.dashboard()) }
    ) {
        h3 { +"Coming soon..." }
    }
