package com.kuvaszuptime.kuvasz.controllers.maintenance

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.ReadOnlyMaintenanceWindowException
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
annotation class CheckMaintenanceWindowsWritable

@Singleton
@InterceptorBean(CheckMaintenanceWindowsWritable::class)
class MaintenanceWindowWriteInterceptor(private val appConfig: AppConfig) : MethodInterceptor<Any, Any>, Ordered {

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        context.findAnnotation(CheckMaintenanceWindowsWritable::class.java).ifPresent { _ ->
            if (appConfig.isMaintenanceWindowExternalWriteDisabled()) throw ReadOnlyMaintenanceWindowException()
        }
        return context.proceed()
    }
}
