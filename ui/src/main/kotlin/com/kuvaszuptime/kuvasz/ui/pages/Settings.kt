package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.dto.SettingsDto
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.fragments.layout.*
import kotlinx.html.*

fun renderSettings(globals: AppGlobals, settings: SettingsDto) =
    withLayout(
        globals,
        title = Messages.settings(),
        pageTitle = { simplePageHeader(preTitle = Messages.overview(), title = Messages.settings()) }
    ) {
        div {
            h1 {
                +settings.toString()
            }
        }
    }
