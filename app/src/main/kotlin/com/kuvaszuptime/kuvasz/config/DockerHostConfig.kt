package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.DockerHostValidationMessages
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotBlank

/**
 * docker-hosts:
 *   - name: "local"
 *     url: "unix:///var/run/docker.sock"
 *   - name: "vps-1"
 *     url: "tcp://10.0.0.5:2376"
 *     tls:
 *       ca: "/certs/vps1/ca.pem"
 *       cert: "/certs/vps1/cert.pem"
 *       key: "/certs/vps1/key.pem"
 */
@EachProperty(DockerHostConfig.CONFIG_PREFIX, list = true)
@Introspected
interface DockerHostConfig {

    @get:NotBlank(message = DockerHostValidationMessages.NAME_NOT_BLANK)
    val name: String

    @get:NotBlank(message = DockerHostValidationMessages.URL_NOT_BLANK)
    val url: String

    val tls: TlsConfig?

    /**
     * The paths of the TLS material used to reach the daemon, following the same rules as Docker's own client:
     * `ca` on its own verifies a self-signed daemon certificate, `cert` + `key` on their own authenticate against a
     * publicly trusted one, and all three together are the usual mTLS setup. `cert` and `key` only make sense as a
     * pair. Readability is checked at startup by `DockerHostRegistry`.
     */
    @ConfigurationProperties("tls")
    interface TlsConfig {

        val ca: String?

        val cert: String?

        val key: String?
    }

    companion object {
        const val CONFIG_PREFIX = "docker-hosts"
    }
}
