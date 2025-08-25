package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.config.AppConfig
import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.order.Ordered
import jakarta.inject.Singleton

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class CheckHttpMonitorsWritable

@Singleton
@InterceptorBean(CheckHttpMonitorsWritable::class)
class HttpMonitorWriteInterceptor(private val appConfig: AppConfig) : MethodInterceptor<Any?, Any?>, Ordered {

    override fun intercept(context: MethodInvocationContext<Any?, Any?>): Any? {
        context.findAnnotation(CheckHttpMonitorsWritable::class.java).ifPresent { _ ->
            if (appConfig.isHttpMonitorExternalWriteDisabled()) throw ReadOnlyMonitorException()
        }
        return context.proceed()
    }
}

class ReadOnlyMonitorException : Exception(
    "The monitors were configured via a YAML file. " +
        "You cannot modify them via the API. Please change the configuration in the YAML file and restart the server."
)
