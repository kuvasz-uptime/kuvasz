package com.kuvaszuptime.kuvasz.metrics.http

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.events.WithCertInfo
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton
import java.time.OffsetDateTime

@Singleton
@Requirements(
    Requires(bean = MeterRegistry::class),
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.ssl-expiry", value = StringUtils.TRUE),
)
class SSLCertificateExpiryExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val monitorRepository: HttpMonitorRepository,
) : HttpGaugeExporter<OffsetDateTime>(meterRegistry, eventDispatcher, monitorRepository) {

    companion object {
        private const val MONITOR_SSL_EXPIRY = "http.ssl.expiry.seconds"
    }

    override val meterName = MONITOR_SSL_EXPIRY

    override fun subscribeToEvents() {
        eventDispatcher.subscribeToSSLValidEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToSSLWillExpireEvents { event ->
            event.handle()
        }
    }

    private fun WithCertInfo.handle() {
        logger.debug("Updating SSL certificate expiry for monitor with ID: ${monitor.id} to ${certInfo.validTo}")
        upsertMeter(monitor.id, certInfo.validTo)
    }

    override fun filterCondition(monitor: HttpMonitorRecord): Boolean = monitor.enabled && monitor.sslCheckEnabled

    override fun transform(valueSource: OffsetDateTime): Long = valueSource.toEpochSecond()

    override fun computeInitialValue(monitor: HttpMonitorRecord): OffsetDateTime? =
        monitorRepository.getMonitorWithDetails(monitor.id)?.sslValidUntil
}
