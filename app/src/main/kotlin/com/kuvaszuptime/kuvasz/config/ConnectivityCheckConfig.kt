package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import com.kuvaszuptime.kuvasz.validation.ValidConnectivityTargets
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.util.StringUtils
import jakarta.validation.constraints.Min

@ConfigurationProperties(ConnectivityCheckConfig.PREFIX)
@ValidConnectivityTargets
@Context
@Introspected
@Requires(property = ConnectivityCheckConfig.ENABLED_PROPERTY, value = StringUtils.TRUE)
class ConnectivityCheckConfig {

    companion object {
        const val PREFIX = "app-config.connectivity-check"
        const val ENABLED_PROPERTY = "$PREFIX.enabled"
        const val MIN_INTERVAL_SECONDS = 5L
        const val MIN_TIMEOUT_SECONDS = 1L
        private const val DEFAULT_INTERVAL_SECONDS = 60L
        private const val DEFAULT_TIMEOUT_SECONDS = 5L
        private const val MILLIS_IN_SECOND = 1000L
    }

    var targets: List<String> = listOf("1.1.1.1:53", "8.8.8.8:53")

    @Min(MIN_INTERVAL_SECONDS, message = ValidationMessages.CONNECTIVITY_CHECK_INTERVAL_MIN)
    var intervalSeconds: Long = DEFAULT_INTERVAL_SECONDS

    @Min(MIN_TIMEOUT_SECONDS, message = ValidationMessages.CONNECTIVITY_CHECK_TIMEOUT_MIN)
    var timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS

    // Dropping the malformed entries is safe, because [ValidConnectivityTargets] rejects them at startup, and
    // this bean only exists at all when the connectivity check is enabled
    val parsedTargets: List<ConnectivityTarget> get() = targets.mapNotNull { ConnectivityTarget.parseOrNull(it) }

    val areTargetsValid: Boolean get() = targets.all { ConnectivityTarget.parseOrNull(it) != null }

    val timeoutMillis: Int get() = (timeoutSeconds * MILLIS_IN_SECOND).toInt()
}

data class ConnectivityTarget(val host: String, val port: Int) {

    override fun toString(): String = "$host:$port"

    companion object {
        /**
         * Parses a `host:port` target, returning null if it's malformed. The separator is looked up from the end,
         * so IPv6 literals in brackets (e.g. `[2606:4700:4700::1111]:53`) are handled too.
         */
        fun parseOrNull(raw: String): ConnectivityTarget? {
            val trimmed = raw.trim()
            val separatorIdx = trimmed.lastIndexOf(':')
            val host = if (separatorIdx > 0) trimmed.substring(0, separatorIdx) else ""
            // Anything without a separator, or with nothing after it, yields no port at all
            val port = trimmed.takeIf { separatorIdx in 0 until it.lastIndex }
                ?.substring(separatorIdx + 1)
                ?.toIntOrNull()

            return port
                ?.takeIf { host.isNotBlank() && it.toLong() in Validation.MIN_PORT..Validation.MAX_PORT }
                ?.let { ConnectivityTarget(host, it) }
        }
    }
}
