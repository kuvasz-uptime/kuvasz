package com.kuvaszuptime.kuvasz.controllers.statuspage

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.ReadOnlyStatusPageException
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
annotation class CheckStatusPagesWritable

@Singleton
@InterceptorBean(CheckStatusPagesWritable::class)
class StatusPageWriteInterceptor(private val appConfig: AppConfig) : MethodInterceptor<Any, Any>, Ordered {

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        context.findAnnotation(CheckStatusPagesWritable::class.java).ifPresent { _ ->
            if (appConfig.isStatusPageExternalWriteDisabled()) throw ReadOnlyStatusPageException()
        }
        return context.proceed()
    }
}
