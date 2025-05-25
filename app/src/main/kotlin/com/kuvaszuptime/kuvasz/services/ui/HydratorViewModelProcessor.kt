package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.ui.ViewParams
import io.micronaut.http.HttpRequest
import io.micronaut.security.utils.SecurityService
import io.micronaut.views.ModelAndView
import io.micronaut.views.model.ViewModelProcessor
import jakarta.inject.Singleton
import kotlin.jvm.optionals.getOrNull

/**
 * A custom ViewModelProcessor that adds global variables to the model of the views.
 */
@Singleton
class HydratorViewModelProcessor(
    private val securityService: SecurityService?,
    private val appConfig: AppConfig,
) : ViewModelProcessor<ViewParams> {
    companion object {
        private const val APP_VERSION_KEY = "appVersion"
        private const val AUTHENTICATION_KEY = "isAuthenticated"
        private const val AUTH_ENABLED_KEY = "isAuthEnabled"
        private const val READ_ONLY_MODE_KEY = "isReadOnlyMode"
    }

    override fun process(request: HttpRequest<*>, modelAndView: ModelAndView<ViewParams>) {
        modelAndView.model.getOrNull()?.let { model ->
            model[APP_VERSION_KEY] = BuildConfig.APP_VERSION
            model[AUTH_ENABLED_KEY] = securityService != null
            model[AUTHENTICATION_KEY] = securityService?.isAuthenticated ?: true
            model[READ_ONLY_MODE_KEY] = appConfig.isExternalWriteDisabled()
        }
    }
}
