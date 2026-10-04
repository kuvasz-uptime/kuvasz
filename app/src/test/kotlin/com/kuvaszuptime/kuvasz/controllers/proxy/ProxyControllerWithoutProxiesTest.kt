package com.kuvaszuptime.kuvasz.controllers.proxy

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest
class ProxyControllerWithoutProxiesTest(private val proxyClient: ProxyClient) : ShouldSpec({

    context("the getProxies endpoint without any configured proxy") {

        should("return an empty list rather than fail") {
            proxyClient.getProxies().shouldBeEmpty()
        }
    }
})
