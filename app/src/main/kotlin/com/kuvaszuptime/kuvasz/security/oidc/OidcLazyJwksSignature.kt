package com.kuvaszuptime.kuvasz.security.oidc

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.SignedJWT
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.security.token.jwt.nimbus.ReactiveJwksSignature
import io.micronaut.security.token.jwt.signature.ReactiveSignatureConfiguration
import io.micronaut.security.token.jwt.signature.jwks.JwkSetFetcher
import io.micronaut.security.token.jwt.signature.jwks.JwkValidator
import io.micronaut.security.token.jwt.signature.jwks.JwksSignatureConfigurationProperties
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

private const val OIDC_LAZY_JWKS_NAME = "oidc-lazy-jwks"

/**
 * Part of the workaround for https://github.com/micronaut-projects/micronaut-security/issues/1797 (see
 * [OidcProviderMetadataFilter])
 *
 * The JWT validator collects its signatures only once, early on. If the OIDC provider is unreachable at that point,
 * the JWKS signature derived from the provider metadata is missing from it for good, so the ID tokens can't be verified
 * even after the metadata got recovered. This signature is always part of that collection, and starts verifying with
 * the provider's JWKS as soon as [OidcProviderMetadataFilter] hands over its URL.
 *
 * It replaces Micronaut's own JWKS signatures, so the provider's JWKS isn't fetched twice when it was reachable at
 * startup. The provider is the only JWKS source of Kuvasz, there are no statically configured JWKS signatures.
 **/
@Singleton
@Replaces(ReactiveJwksSignature::class)
@Requires(property = "micronaut.security.oauth2.clients.oidc.enabled", value = "true")
class OidcLazyJwksSignature(
    private val jwkValidator: JwkValidator,
    private val jwkSetFetcher: JwkSetFetcher<JWKSet>,
) : ReactiveSignatureConfiguration<SignedJWT> {

    private val delegate = AtomicReference<ReactiveJwksSignature?>()

    fun activate(jwksUri: String) {
        if (delegate.get() != null) return
        val configuration = JwksSignatureConfigurationProperties(OIDC_LAZY_JWKS_NAME).apply { url = jwksUri }
        delegate.compareAndSet(null, ReactiveJwksSignature(configuration, jwkValidator, jwkSetFetcher))
    }

    override fun verify(jwt: SignedJWT): Publisher<Boolean> = delegate.get()?.verify(jwt) ?: Mono.just(false)
}
