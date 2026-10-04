package com.kuvaszuptime.kuvasz.controllers.proxy

import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyDto
import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyType
import com.kuvaszuptime.kuvasz.testutils.PROXIES
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(environments = [PROXIES])
class ProxyControllerTest(private val proxyClient: ProxyClient) : ShouldSpec({

    context("the getProxies endpoint") {

        should("return every configured proxy, sorted by name, without the credentials") {
            proxyClient.getProxies() shouldContainExactly listOf(
                ProxyDto(
                    name = "corporate-egress",
                    type = ProxyType.HTTP,
                    host = "10.0.0.10",
                    port = 3128,
                    authenticated = true,
                ),
                ProxyDto(
                    name = "office-network",
                    type = ProxyType.SOCKS5,
                    host = "127.0.0.1",
                    port = 1080,
                    authenticated = false,
                ),
            )
        }
    }
})
