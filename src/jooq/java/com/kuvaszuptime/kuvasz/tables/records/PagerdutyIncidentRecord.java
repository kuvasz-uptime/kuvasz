/*
 * This file is generated by jOOQ.
 */
package com.kuvaszuptime.kuvasz.tables.records;


import com.kuvaszuptime.kuvasz.tables.PagerdutyIncident;

import java.time.OffsetDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record6;
import org.jooq.Row6;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
@Entity
@Table(name = "pagerduty_incident", uniqueConstraints = {
    @UniqueConstraint(name = "pagerduty_incident_pkey", columnNames = {"id"})
}, indexes = {
    @Index(name = "pagerduty_incident_dedup_key_idx", unique = true, columnList = "deduplication_key ASC"),
    @Index(name = "pagerduty_incident_ended_at_idx", columnList = "ended_at ASC"),
    @Index(name = "pagerduty_incident_ssl_event_idx", columnList = "ssl_event_id ASC"),
    @Index(name = "pagerduty_incident_uptime_event_idx", columnList = "uptime_event_id ASC")
})
public class PagerdutyIncidentRecord extends UpdatableRecordImpl<PagerdutyIncidentRecord> implements Record6<Integer, Integer, Integer, String, OffsetDateTime, OffsetDateTime> {

    private static final long serialVersionUID = -1278699234;

    /**
     * Setter for <code>pagerduty_incident.id</code>.
     */
    public PagerdutyIncidentRecord setId(Integer value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>pagerduty_incident.id</code>.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, precision = 32)
    public Integer getId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>pagerduty_incident.uptime_event_id</code>.
     */
    public PagerdutyIncidentRecord setUptimeEventId(Integer value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>pagerduty_incident.uptime_event_id</code>.
     */
    @Column(name = "uptime_event_id", precision = 32)
    public Integer getUptimeEventId() {
        return (Integer) get(1);
    }

    /**
     * Setter for <code>pagerduty_incident.ssl_event_id</code>.
     */
    public PagerdutyIncidentRecord setSslEventId(Integer value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>pagerduty_incident.ssl_event_id</code>.
     */
    @Column(name = "ssl_event_id", precision = 32)
    public Integer getSslEventId() {
        return (Integer) get(2);
    }

    /**
     * Setter for <code>pagerduty_incident.deduplication_key</code>.
     */
    public PagerdutyIncidentRecord setDeduplicationKey(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>pagerduty_incident.deduplication_key</code>.
     */
    @Column(name = "deduplication_key", nullable = false, length = 100)
    @NotNull
    @Size(max = 100)
    public String getDeduplicationKey() {
        return (String) get(3);
    }

    /**
     * Setter for <code>pagerduty_incident.started_at</code>.
     */
    public PagerdutyIncidentRecord setStartedAt(OffsetDateTime value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>pagerduty_incident.started_at</code>.
     */
    @Column(name = "started_at", nullable = false)
    public OffsetDateTime getStartedAt() {
        return (OffsetDateTime) get(4);
    }

    /**
     * Setter for <code>pagerduty_incident.ended_at</code>.
     */
    public PagerdutyIncidentRecord setEndedAt(OffsetDateTime value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>pagerduty_incident.ended_at</code>.
     */
    @Column(name = "ended_at")
    public OffsetDateTime getEndedAt() {
        return (OffsetDateTime) get(5);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Integer> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record6 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row6<Integer, Integer, Integer, String, OffsetDateTime, OffsetDateTime> fieldsRow() {
        return (Row6) super.fieldsRow();
    }

    @Override
    public Row6<Integer, Integer, Integer, String, OffsetDateTime, OffsetDateTime> valuesRow() {
        return (Row6) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return PagerdutyIncident.PAGERDUTY_INCIDENT.ID;
    }

    @Override
    public Field<Integer> field2() {
        return PagerdutyIncident.PAGERDUTY_INCIDENT.UPTIME_EVENT_ID;
    }

    @Override
    public Field<Integer> field3() {
        return PagerdutyIncident.PAGERDUTY_INCIDENT.SSL_EVENT_ID;
    }

    @Override
    public Field<String> field4() {
        return PagerdutyIncident.PAGERDUTY_INCIDENT.DEDUPLICATION_KEY;
    }

    @Override
    public Field<OffsetDateTime> field5() {
        return PagerdutyIncident.PAGERDUTY_INCIDENT.STARTED_AT;
    }

    @Override
    public Field<OffsetDateTime> field6() {
        return PagerdutyIncident.PAGERDUTY_INCIDENT.ENDED_AT;
    }

    @Override
    public Integer component1() {
        return getId();
    }

    @Override
    public Integer component2() {
        return getUptimeEventId();
    }

    @Override
    public Integer component3() {
        return getSslEventId();
    }

    @Override
    public String component4() {
        return getDeduplicationKey();
    }

    @Override
    public OffsetDateTime component5() {
        return getStartedAt();
    }

    @Override
    public OffsetDateTime component6() {
        return getEndedAt();
    }

    @Override
    public Integer value1() {
        return getId();
    }

    @Override
    public Integer value2() {
        return getUptimeEventId();
    }

    @Override
    public Integer value3() {
        return getSslEventId();
    }

    @Override
    public String value4() {
        return getDeduplicationKey();
    }

    @Override
    public OffsetDateTime value5() {
        return getStartedAt();
    }

    @Override
    public OffsetDateTime value6() {
        return getEndedAt();
    }

    @Override
    public PagerdutyIncidentRecord value1(Integer value) {
        setId(value);
        return this;
    }

    @Override
    public PagerdutyIncidentRecord value2(Integer value) {
        setUptimeEventId(value);
        return this;
    }

    @Override
    public PagerdutyIncidentRecord value3(Integer value) {
        setSslEventId(value);
        return this;
    }

    @Override
    public PagerdutyIncidentRecord value4(String value) {
        setDeduplicationKey(value);
        return this;
    }

    @Override
    public PagerdutyIncidentRecord value5(OffsetDateTime value) {
        setStartedAt(value);
        return this;
    }

    @Override
    public PagerdutyIncidentRecord value6(OffsetDateTime value) {
        setEndedAt(value);
        return this;
    }

    @Override
    public PagerdutyIncidentRecord values(Integer value1, Integer value2, Integer value3, String value4, OffsetDateTime value5, OffsetDateTime value6) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached PagerdutyIncidentRecord
     */
    public PagerdutyIncidentRecord() {
        super(PagerdutyIncident.PAGERDUTY_INCIDENT);
    }

    /**
     * Create a detached, initialised PagerdutyIncidentRecord
     */
    public PagerdutyIncidentRecord(Integer id, Integer uptimeEventId, Integer sslEventId, String deduplicationKey, OffsetDateTime startedAt, OffsetDateTime endedAt) {
        super(PagerdutyIncident.PAGERDUTY_INCIDENT);

        set(0, id);
        set(1, uptimeEventId);
        set(2, sslEventId);
        set(3, deduplicationKey);
        set(4, startedAt);
        set(5, endedAt);
    }
}