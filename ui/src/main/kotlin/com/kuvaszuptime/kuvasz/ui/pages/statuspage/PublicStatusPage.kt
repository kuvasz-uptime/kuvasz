package com.kuvaszuptime.kuvasz.ui.pages.statuspage

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.ui.*

fun renderPublicStatusPage(
    globals: AppGlobals,
    pageData: StatusPageDataDto,
) = withStatusPageLayout(globals, pageData)
