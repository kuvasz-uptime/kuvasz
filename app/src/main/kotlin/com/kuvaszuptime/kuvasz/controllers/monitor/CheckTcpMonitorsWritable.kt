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
annotation class CheckTcpMonitorsWritable

@Singleton
@InterceptorBean(CheckTcpMonitorsWritable::class)
class TcpMonitorWriteInterceptor(private val appConfig: AppConfig) : MethodInterceptor<Any, Any>, Ordered {

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        context.findAnnotation(CheckTcpMonitorsWritable::class.java).ifPresent { _ ->
            if (appConfig.isTcpMonitorExternalWriteDisabled()) throw ReadOnlyMonitorException()
        }
        return context.proceed()
    }
}
