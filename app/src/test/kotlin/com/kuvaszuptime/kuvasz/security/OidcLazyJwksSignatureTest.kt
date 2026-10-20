package com.kuvaszuptime.kuvasz.security

import com.kuvaszuptime.kuvasz.security.oidc.OidcLazyJwksSignature
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.security.token.jwt.signature.jwks.DefaultJwkValidator
import io.micronaut.security.token.jwt.signature.jwks.JwkSetFetcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.reactive.awaitFirst
import reactor.core.publisher.Mono

class OidcLazyJwksSignatureTest : BehaviorSpec({

    val providerKey = generateRsaKey("provider-key")
    val jwksUri = "https://idp.example.com/jwks"

    fun signedJwt(key: RSAKey): SignedJWT =
        SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(),
            JWTClaimsSet.Builder().subject("sub-123").build(),
        ).apply { sign(RSASSASigner(key)) }

    fun fetcherServing(key: RSAKey): JwkSetFetcher<JWKSet> = mockk {
        every { fetch(any(), any()) } returns Mono.just(JWKSet(key.toPublicJWK()))
    }

    given("an OidcLazyJwksSignature that hasn't been activated yet") {
        val fetcher = fetcherServing(providerKey)
        val signature = OidcLazyJwksSignature(DefaultJwkValidator(), fetcher)

        `when`("a JWT signed by the provider is verified") {
            val result = signature.verify(signedJwt(providerKey)).awaitFirst()

            then("it should not verify it, without trying to fetch any JWKS") {
                result shouldBe false
                verify(exactly = 0) { fetcher.fetch(any(), any()) }
            }
        }
    }

    given("an activated OidcLazyJwksSignature") {
        val fetcher = fetcherServing(providerKey)
        val signature = OidcLazyJwksSignature(DefaultJwkValidator(), fetcher).apply { activate(jwksUri) }

        `when`("a JWT signed by the provider is verified") {
            val result = signature.verify(signedJwt(providerKey)).awaitFirst()

            then("it should verify it with the JWKS fetched from the given URI") {
                result shouldBe true
                verify { fetcher.fetch(any(), jwksUri) }
            }
        }

        `when`("a JWT signed by a different key is verified") {
            val result = signature.verify(signedJwt(generateRsaKey("stranger-key"))).awaitFirst()

            then("it should not verify it") {
                result shouldBe false
            }
        }
    }

    given("an OidcLazyJwksSignature that is activated multiple times") {
        val fetcher = fetcherServing(providerKey)
        val signature = OidcLazyJwksSignature(DefaultJwkValidator(), fetcher).apply {
            activate(jwksUri)
            activate("https://other.example.com/jwks")
        }

        `when`("a JWT is verified") {
            signature.verify(signedJwt(providerKey)).awaitFirst()

            then("it should keep using the JWKS URI it was first activated with") {
                verify { fetcher.fetch(any(), jwksUri) }
                verify(exactly = 0) { fetcher.fetch(any(), "https://other.example.com/jwks") }
            }
        }
    }
})

private const val RSA_KEY_SIZE = 2048

private fun generateRsaKey(keyId: String): RSAKey = RSAKeyGenerator(RSA_KEY_SIZE).keyID(keyId).generate()
