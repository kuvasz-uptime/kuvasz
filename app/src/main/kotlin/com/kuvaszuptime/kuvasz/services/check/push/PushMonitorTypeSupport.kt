package com.kuvaszuptime.kuvasz.services.check.push

import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.push.PushMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.push.affectsFailureCounting
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.services.monitor.MonitorTypeSupport
import jakarta.inject.Singleton
import org.jooq.DSLContext

@Singleton
class PushMonitorTypeSupport(
    override val repository: PushMonitorRepository,
    val pendingFailureRepository: PendingFailureRepository,
) : MonitorTypeSupport<PushMonitorCreator, PushMonitorRecord, PushMonitorDetailsDto> {

    override fun onUpserted(previous: PushMonitorRecord?, upserted: PushMonitorRecord, txCtx: DSLContext) {
        // The already recorded failures were counted against the previous settings of the monitor, so they are not
        // comparable to the updated ones anymore
        if (previous != null && upserted.affectsFailureCounting(previous)) {
            pendingFailureRepository.deleteByMonitorId(upserted.id, txCtx)
        }
    }
}
