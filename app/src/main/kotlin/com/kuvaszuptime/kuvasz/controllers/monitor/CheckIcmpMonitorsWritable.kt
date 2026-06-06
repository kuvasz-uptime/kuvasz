package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.ReadOnlyMonitorException
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
annotation class CheckIcmpMonitorsWritable

@Singleton
@InterceptorBean(CheckIcmpMonitorsWritable::class)
class IcmpMonitorWriteInterceptor(private val appConfig: AppConfig) : MethodInterceptor<Any, Any>, Ordered {

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        context.findAnnotation(CheckIcmpMonitorsWritable::class.java).ifPresent { _ ->
            if (appConfig.isIcmpMonitorExternalWriteDisabled()) throw ReadOnlyMonitorException()
        }
        return context.proceed()
    }
}
