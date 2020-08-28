package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.config.AdminAuthConfig
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

@Singleton
class AdminAuthProvider @Inject constructor(private val authConfig: AdminAuthConfig) : AuthenticationProvider {

    override fun authenticate(
        httpRequest: HttpRequest<*>?,
        authenticationRequest: AuthenticationRequest<*, *>
    ): Publisher<AuthenticationResponse> {
        return Flowable.create(
            { emitter: FlowableEmitter<AuthenticationResponse> ->
                if (authenticationRequest.identity == authConfig.username &&
                    authenticationRequest.secret == authConfig.password
                ) {
                    val userDetails =
                        UserDetails(
                            authenticationRequest.identity as String,
                            listOf(Role.ADMIN.alias)
                        )
                    emitter.onNext(userDetails)
                    emitter.onComplete()
                } else {
                    emitter.onError(AuthenticationException(AuthenticationFailed()))
                }
            },
            BackpressureStrategy.ERROR
        )
    }
}
