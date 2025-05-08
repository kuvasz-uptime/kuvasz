package com.kuvaszuptime.kuvasz.security.ui

import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.order.Ordered
import io.micronaut.security.utils.SecurityService
import jakarta.inject.Singleton

/**
 * Validates upon the invocation of the annotated methods if the request is authenticated (or the authentication is
 * disabled). If so, then it throws an AlreadyLoggedInError, which can be handled by the controller to redirect the user
 * to a different page.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class UnauthorizedOnly

@Singleton
@InterceptorBean(UnauthorizedOnly::class)
class UnauthorizedOnlyInterceptor(
    private val securityService: SecurityService?
) : MethodInterceptor<Any?, Any?>, Ordered {

    override fun intercept(context: MethodInvocationContext<Any?, Any?>): Any? {
        context.findAnnotation(UnauthorizedOnly::class.java).ifPresent { _ ->
            if (securityService?.isAuthenticated != false) throw AlreadyLoggedInError()
        }
        return context.proceed()
    }
}

class AlreadyLoggedInError : Exception()
