package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.ProxyValidationMessages
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotBlank

/**
 * proxies:
 *   - name: "corporate-egress"
 *     url: "http://10.0.0.10:3128"
 *     username: "kuvasz"
 *     password: "${PROXY_PASSWORD}"
 *   - name: "office-network"
 *     url: "socks5://127.0.0.1:1080"
 */
@EachProperty(ProxyConfig.CONFIG_PREFIX, list = true)
@Introspected
interface ProxyConfig {

    @get:NotBlank(message = ProxyValidationMessages.NAME_NOT_BLANK)
    val name: String

    @get:NotBlank(message = ProxyValidationMessages.URL_NOT_BLANK)
    val url: String

    val username: String?

    val password: String?

    companion object {
        const val CONFIG_PREFIX = "proxies"
    }
}
