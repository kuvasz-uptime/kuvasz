package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.repositories.SettingsRepository
import com.kuvaszuptime.kuvasz.security.ui.UnauthorizedOnly
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.ui.pages.*
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.swagger.v3.oas.annotations.Hidden

@Controller("/")
@Hidden
class WebUIController(
    private val appGlobals: AppGlobals,
    private val settingsRepository: SettingsRepository,
) {

    companion object {
        const val DASHBOARD_PATH = "/"
        const val LOGIN_PATH = "/login"
    }

    @Get(DASHBOARD_PATH)
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.IO)
    fun dashboard() = renderDashboard(appGlobals)

    @Get(LOGIN_PATH)
    @UnauthorizedOnly
    @Produces(MediaType.TEXT_HTML)
    fun login(@QueryValue error: Boolean?): String = renderLoginPage(
        appGlobals,
        loginErrorMessage = if (error == true) Messages.invalidCredentials() else null,
    )

    @Get("/settings")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.IO)
    fun settings() = renderSettings(appGlobals, settingsRepository.getSettings())
}
