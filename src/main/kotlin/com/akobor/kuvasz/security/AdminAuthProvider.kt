package com.akobor.kuvasz.security

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationException
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import org.reactivestreams.Publisher
import javax.inject.Inject
import javax.inject.Singleton

@ConfigurationProperties("admin-auth-config")
@Introspected
class AdminAuthConfig {
    var user: String? = null
    var password: String? = null
}

@Singleton
class AdminAuthProvider @Inject constructor(private val authConfig: AdminAuthConfig) : AuthenticationProvider {

    override fun authenticate(
        httpRequest: HttpRequest<*>?,
        authenticationRequest: AuthenticationRequest<*, *>
    ): Publisher<AuthenticationResponse> {
        return Flowable.create({ emitter: FlowableEmitter<AuthenticationResponse> ->
            if (authenticationRequest.identity == authConfig.user &&
                authenticationRequest.secret == authConfig.password
            ) {
                emitter.onNext(UserDetails(authenticationRequest.identity as String, ArrayList()))
                emitter.onComplete()
            } else {
                emitter.onError(AuthenticationException(AuthenticationFailed()))
            }
        }, BackpressureStrategy.ERROR)
    }
}
