package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton

@Singleton
@Requirements(
    Requires(bean = MeterRegistry::class),
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.ssl-status", value = StringUtils.TRUE),
)
class SSLStatusExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val monitorRepository: MonitorRepository,
) : GaugeExporter<SslStatus>(meterRegistry, eventDispatcher, monitorRepository) {

    companion object {
        private const val MONITOR_SSL_STATUS = "monitor.ssl.status"
    }

    override val meterName = MONITOR_SSL_STATUS

    override fun subscribeToEvents() {
        eventDispatcher.subscribeToSSLValidEvents { it.handle() }
        eventDispatcher.subscribeToSSLInvalidEvents { it.handle() }
        eventDispatcher.subscribeToSSLWillExpireEvents { it.handle() }
    }

    private fun SSLMonitorEvent.handle() {
        runWhenStateChanges {
            logger.debug("Updating SSL status for monitor with ID: ${monitor.id} to $sslStatus")
            upsertMeter(monitor.id, sslStatus)
        }
    }

    override fun filterCondition(monitor: MonitorRecord): Boolean = monitor.enabled && monitor.sslCheckEnabled

    override fun transform(valueSource: SslStatus): Long =
        when (valueSource) {
            SslStatus.VALID, SslStatus.WILL_EXPIRE -> 1L
            SslStatus.INVALID -> 0L
        }

    override fun computeInitialValue(monitor: MonitorRecord): SslStatus? =
        monitorRepository.getMonitorWithDetails(monitor.id)?.sslStatus
}
