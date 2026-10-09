package com.kuvaszuptime.kuvasz.controllers.incident

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.ReadOnlyIncidentNoteException
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
annotation class CheckIncidentNotesWritable

@Singleton
@InterceptorBean(CheckIncidentNotesWritable::class)
class IncidentNoteWriteInterceptor(private val appConfig: AppConfig) : MethodInterceptor<Any, Any>, Ordered {

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        context.findAnnotation(CheckIncidentNotesWritable::class.java).ifPresent { _ ->
            if (!appConfig.incidentNotesEnabled) throw ReadOnlyIncidentNoteException()
        }
        return context.proceed()
    }
}
