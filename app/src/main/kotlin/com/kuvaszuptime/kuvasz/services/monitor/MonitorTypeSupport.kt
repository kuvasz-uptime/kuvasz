package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.MonitorWithMetricsHistory
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorCreator
import com.kuvaszuptime.kuvasz.repositories.MonitorMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.monitorType
import org.jooq.DSLContext

/**
 * Everything the generic monitor flows need to know about a single monitor type: where its monitors live, how to turn
 * a config of it into a record, and what an update of it invalidates.
 *
 * Keeping the last one here, instead of in the type's own actions, is what stops the import path and the CRUD path
 * from drifting apart: both of them announce their changes through [onUpserted].
 */
interface MonitorTypeSupport<C : MonitorCreator<R>, R : MonitorRecord, D : MonitorDetailsDto> {

    val repository: MonitorRepository<R, D>

    val monitorType: MonitorType get() = repository.monitorType

    fun onUpserted(previous: R?, upserted: R, txCtx: DSLContext)
}

abstract class MetricsHistoryMonitorTypeSupport<C : MonitorCreator<R>, R, D : MonitorDetailsDto> :
    MonitorTypeSupport<C, R, D> where R : MonitorRecord, R : MonitorWithMetricsHistory {

    abstract val metricsLogRepository: MonitorMetricsLogRepository<*, *>

    override fun onUpserted(previous: R?, upserted: R, txCtx: DSLContext) {
        if (previous != null && previous.metricsHistoryEnabled && !upserted.metricsHistoryEnabled) {
            metricsLogRepository.deleteAllByMonitorId(upserted.id, txCtx)
        }
    }
}
