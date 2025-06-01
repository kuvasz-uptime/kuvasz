package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.security.utils.SecurityService
import java.util.Locale

@Factory
class AppGlobalsFactory {

    @Context
    fun appGlobals(
        securityService: SecurityService?,
        appConfig: AppConfig,
    ) = AppGlobals(
        isReadOnlyMode = appConfig.isExternalWriteDisabled(),
        isAuthenticated = { securityService?.isAuthenticated ?: true },
        isAuthEnabled = securityService != null,
        appVersion = BuildConfig.APP_VERSION,
        locale = Locale.of(appConfig.language),
    )
}
