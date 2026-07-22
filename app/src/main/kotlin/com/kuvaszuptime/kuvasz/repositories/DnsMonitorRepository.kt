package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.jooq.tables.DnsMonitor.DNS_MONITOR
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.util.fetchOneOrThrow
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException

@Singleton
class DnsMonitorRepository(private val dslContext: DSLContext) : MonitorRepository<DnsMonitorRecord> {

    override fun findById(monitorId: Long, txCtx: DSLContext?): DnsMonitorRecord? = (txCtx ?: dslContext)
        .selectFrom(DNS_MONITOR)
        .where(DNS_MONITOR.ID.eq(monitorId))
        .fetchOne()

    fun findByName(name: String): DnsMonitorRecord? = dslContext
        .selectFrom(DNS_MONITOR)
        .where(DNS_MONITOR.NAME.eq(name))
        .fetchOne()

    fun fetchAll(): List<DnsMonitorRecord> = dslContext
        .selectFrom(DNS_MONITOR)
        .fetch()

    fun fetchByEnabled(enabled: Boolean): List<DnsMonitorRecord> = dslContext
        .selectFrom(DNS_MONITOR)
        .where(DNS_MONITOR.ENABLED.eq(enabled))
        .fetch()

    override fun deleteById(monitorId: Long, txCtx: DSLContext?): Int = (txCtx ?: dslContext)
        .deleteFrom(DNS_MONITOR)
        .where(DNS_MONITOR.ID.eq(monitorId))
        .execute()

    fun returningInsert(monitor: DnsMonitorRecord): DnsMonitorRecord =
        try {
            dslContext
                .insertInto(DNS_MONITOR)
                .set(monitor)
                .returning(DNS_MONITOR.asterisk())
                .fetchOneOrThrow<DnsMonitorRecord>()
        } catch (e: DataAccessException) {
            throw e.checkForDuplication()
        }

    fun updateIntegrations(monitorId: Long, newIntegrations: Array<IntegrationID>) {
        dslContext
            .update(DNS_MONITOR)
            .set(DNS_MONITOR.INTEGRATIONS, newIntegrations)
            .where(DNS_MONITOR.ID.eq(monitorId))
            .execute()
    }
}
