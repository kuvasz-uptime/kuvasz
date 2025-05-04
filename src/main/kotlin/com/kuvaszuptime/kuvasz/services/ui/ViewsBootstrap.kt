package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.models.ui.ViewParams
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.security.utils.SecurityService
import io.micronaut.views.ModelAndView
import io.micronaut.views.model.ViewModelProcessor
import jakarta.inject.Singleton
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect
import org.thymeleaf.TemplateEngine
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect
import kotlin.jvm.optionals.getOrNull

/**
 * This class is responsible for configuring the Thymeleaf template engine.
 * It adds the LayoutDialect and Java8TimeDialect to the template engine.
 */
@Singleton
class TemplateListener : BeanCreatedEventListener<TemplateEngine> {
    override fun onCreated(event: BeanCreatedEvent<TemplateEngine>): TemplateEngine {
        val builder = event.bean
        builder.addDialect(LayoutDialect())
        builder.addDialect(Java8TimeDialect())
        return builder
    }
}

/**
 * A custom ViewModelProcessor that adds non-trivial data to the model of the views.
 */
@Singleton
class HydratorViewModelProcessor(private val securityService: SecurityService?) : ViewModelProcessor<ViewParams> {
    companion object {
        private const val APP_VERSION_KEY = "appVersion"
        private const val AUTHENTICATION_KEY = "isAuthenticated"
        private const val AUTH_ENABLED_KEY = "isAuthEnabled"
    }

    override fun process(request: HttpRequest<*>, modelAndView: ModelAndView<ViewParams>) {
        modelAndView.model.getOrNull()?.let { model ->
            model[APP_VERSION_KEY] = BuildConfig.APP_VERSION
            model[AUTH_ENABLED_KEY] = securityService != null
            model[AUTHENTICATION_KEY] = securityService?.isAuthenticated ?: true
        }
    }
}
