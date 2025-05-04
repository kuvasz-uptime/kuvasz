package com.kuvaszuptime.kuvasz.security.ui

import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
import com.kuvaszuptime.kuvasz.security.Role
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.provider.HttpRequestReactiveAuthenticationProvider
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.FlowableEmitter
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

@Singleton
@Requires(property = "micronaut.security.enabled", value = "true")
class WebAuthProvider(private val authConfig: AdminAuthConfig) : HttpRequestReactiveAuthenticationProvider<Any> {

    override fun authenticate(
        requestContext: HttpRequest<Any>?,
        authenticationRequest: AuthenticationRequest<String, String>,
    ): Publisher<AuthenticationResponse> {
        return Flowable.create(
            { emitter: FlowableEmitter<AuthenticationResponse> ->
                if (authenticationRequest.identity == authConfig.username &&
                    authenticationRequest.secret == authConfig.password
                ) {
                    emitter.onNext(
                        AuthenticationResponse.success(
                            authenticationRequest.identity as String,
                            listOf(Role.WEB.alias, Role.API.alias)
                        )
                    )
                    emitter.onComplete()
                } else {
                    emitter.onError(AuthenticationResponse.exception())
                }
            },
            BackpressureStrategy.ERROR
        )
    }
}
