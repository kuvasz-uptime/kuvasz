package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

@Singleton
class AdminAuthProvider(private val authConfig: AdminAuthConfig) : AuthenticationProvider {

    override fun authenticate(
        httpRequest: HttpRequest<*>?,
        authenticationRequest: AuthenticationRequest<*, *>
    ): Publisher<AuthenticationResponse> {
        return Flowable.create(
            { emitter: FlowableEmitter<AuthenticationResponse> ->
                if (authenticationRequest.identity == authConfig.username &&
                    authenticationRequest.secret == authConfig.password
                ) {
                    emitter.onNext(
                        AuthenticationResponse.success(
                            authenticationRequest.identity as String,
                            listOf(Role.ADMIN.alias)
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
