package com.akobor.kuvasz.security

import io.micronaut.context.annotation.Property
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
import javax.inject.Singleton

@Singleton
class BasicAuthProvider : AuthenticationProvider {

    @field:Property(name = "micronaut.security.basic-auth.user")
    var basicAuthUser: String = ""

    @field:Property(name = "micronaut.security.basic-auth.password")
    var basicAuthPassword: String = ""

    override fun authenticate(
        httpRequest: HttpRequest<*>?,
        authenticationRequest: AuthenticationRequest<*, *>
    ): Publisher<AuthenticationResponse> {
        return Flowable.create({ emitter: FlowableEmitter<AuthenticationResponse> ->
            if (authenticationRequest.identity == basicAuthUser && authenticationRequest.secret == basicAuthPassword) {
                emitter.onNext(UserDetails(authenticationRequest.identity as String, ArrayList()))
                emitter.onComplete()
            } else {
                emitter.onError(AuthenticationException(AuthenticationFailed()))
            }
        }, BackpressureStrategy.ERROR)
    }
}
