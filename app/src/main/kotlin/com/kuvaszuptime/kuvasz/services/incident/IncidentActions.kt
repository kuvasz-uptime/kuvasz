package com.kuvaszuptime.kuvasz.services.incident

import com.kuvaszuptime.kuvasz.jooq.tables.pojos.IncidentNote
import com.kuvaszuptime.kuvasz.jooq.tables.records.IncidentNoteRecord
import com.kuvaszuptime.kuvasz.models.IncidentNotFoundException
import com.kuvaszuptime.kuvasz.models.IncidentNoteNotFoundException
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteCreateDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentNoteUpdateDto
import com.kuvaszuptime.kuvasz.models.events.IncidentNoteCreatedEvent
import com.kuvaszuptime.kuvasz.models.events.IncidentNoteUpdatedEvent
import com.kuvaszuptime.kuvasz.models.isShownPublicly
import com.kuvaszuptime.kuvasz.repositories.IncidentNoteRepository
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.repositories.toDto
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageCacheInvalidator
import com.kuvaszuptime.kuvasz.util.entityUpdateMapper
import com.kuvaszuptime.kuvasz.util.transactionResultWithError
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.convertValue

@Singleton
class IncidentActions(
    private val incidentRepository: IncidentRepository,
    private val incidentNoteRepository: IncidentNoteRepository,
    private val statusPageCacheInvalidator: StatusPageCacheInvalidator,
    private val dslContext: DSLContext,
    private val validator: Validator,
    private val eventDispatcher: EventDispatcher,
) {

    fun getIncidentDetails(incidentType: IncidentType, eventId: Long): IncidentDetailsDto =
        getIncidentDetails(getExistingIncident(incidentType, eventId))

    fun createNote(incidentType: IncidentType, eventId: Long, createDto: IncidentNoteCreateDto): IncidentNoteDto =
        insertNote(getExistingIncident(incidentType, eventId), createDto)

    fun createNoteAndGetDetails(
        incidentType: IncidentType,
        eventId: Long,
        createDto: IncidentNoteCreateDto,
    ): IncidentDetailsDto {
        val incident = getExistingIncident(incidentType, eventId)
        insertNote(incident, createDto)

        return getIncidentDetails(incident)
    }

    private fun getExistingIncident(incidentType: IncidentType, eventId: Long): IncidentDto =
        incidentRepository.getIncident(incidentType, eventId) ?: throw IncidentNotFoundException(incidentType, eventId)

    private fun getIncidentDetails(incident: IncidentDto): IncidentDetailsDto {
        val notifiedIntegrations = incidentRepository.getNotifiedIntegrations(incident)

        return IncidentDetailsDto(
            incident = incident,
            notes = incidentNoteRepository.fetchByEvent(incident.incidentType, incident.eventId),
            integrations = notifiedIntegrations.started,
            resolutionIntegrations = notifiedIntegrations.resolved,
        )
    }

    private fun insertNote(incident: IncidentDto, createDto: IncidentNoteCreateDto): IncidentNoteDto {
        val note = IncidentNoteRecord()
            .setIncidentType(incident.incidentType)
            .setEventId(incident.eventId)
            .setStatus(createDto.status)
            .setContent(createDto.content)
            .setTimestamp(createDto.timestamp)
            .setPublic(createDto.public)

        return incidentNoteRepository.returningInsert(note).toDto()
            .also { created ->
                invalidateStatusPagesIfShown(incident.incidentType, created.public)
                eventDispatcher.dispatch(IncidentNoteCreatedEvent(created))
            }
    }

    fun updateNote(incidentNoteId: Long, updates: ObjectNode): IncidentNoteDto {
        val (wasPublic, updated) = dslContext.transactionResultWithError { txCtx ->
            val existing = incidentNoteRepository.findById(incidentNoteId, txCtx)
                ?: throw IncidentNoteNotFoundException(incidentNoteId)
            val filteredUpdates = updates.propertyNames()
                .filter { it in UPDATABLE_FIELDS }
                .fold(entityUpdateMapper.createObjectNode()) { acc, fieldName ->
                    acc.set(fieldName, updates.get(fieldName))
                }
            val updatedNote = entityUpdateMapper.updateValue(existing.into(IncidentNote::class.java), filteredUpdates)

            entityUpdateMapper.convertValue<IncidentNoteUpdateDto>(updatedNote).let { toValidate ->
                validator.validate(toValidate).throwIfNotEmpty()
            }

            existing.public to incidentNoteRepository.returningUpdate(IncidentNoteRecord(updatedNote), txCtx).toDto()
        }
        // A note that has just been hidden has to disappear from the status pages too
        invalidateStatusPagesIfShown(updated.incidentType, wasPublic || updated.public)
        eventDispatcher.dispatch(IncidentNoteUpdatedEvent(updated))

        return updated
    }

    fun deleteNote(incidentNoteId: Long) {
        val deleted = incidentNoteRepository.deleteById(incidentNoteId)
            ?: throw IncidentNoteNotFoundException(incidentNoteId)
        invalidateStatusPagesIfShown(deleted.incidentType, deleted.public)
    }

    private fun invalidateStatusPagesIfShown(incidentType: IncidentType, public: Boolean) {
        if (public && incidentType.isShownPublicly) {
            statusPageCacheInvalidator.invalidateAllCaches()
        }
    }

    companion object {
        private val UPDATABLE_FIELDS = setOf(
            IncidentNoteUpdateDto::status.name,
            IncidentNoteUpdateDto::content.name,
            IncidentNoteUpdateDto::timestamp.name,
            IncidentNoteUpdateDto::public.name,
        )
    }
}
