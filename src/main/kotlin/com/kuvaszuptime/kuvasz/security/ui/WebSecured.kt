package com.kuvaszuptime.kuvasz.security.ui

import com.kuvaszuptime.kuvasz.security.Role
import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.order.Ordered
import io.micronaut.security.utils.SecurityService
import jakarta.inject.Singleton

/**
 * Validates upon the invocation of the annotated methods if the request has a role called "ROLE_WEB" in case the
 * authentication is not disabled explicitly. If the role is missing, it throws a WebAuthError, which will be handled
 * and transformed into a redirection to the "/login" page.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@Around
annotation class WebSecured

@Singleton
@InterceptorBean(WebSecured::class)
class WebSecuredInterceptor(
    private val securityService: SecurityService?
) : MethodInterceptor<Any?, Any?>, Ordered {

    override fun intercept(context: MethodInvocationContext<Any?, Any?>): Any? {
        context.findAnnotation(WebSecured::class.java).ifPresent { _ ->
            if (securityService?.hasRole(Role.WEB.alias) == false) throw WebAuthError()
        }
        return context.proceed()
    }
}

class WebAuthError : RuntimeException()
