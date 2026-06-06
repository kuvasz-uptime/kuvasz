package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.heartbeat.PushMonitorFailureDetailsDto
import com.kuvaszuptime.kuvasz.models.events.sanitizeAsError
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorActions
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Status
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory

@Controller("${API_V2_PREFIX}/push-monitors/heartbeats", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.PUSH_MONITORS)
class PushMonitorHeartbeatController(
    private val monitorActions: PushMonitorActions,
) : PushMonitorHeartbeatOperations {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @ApiResponses(
        ApiResponse(
            responseCode = "202",
            description = "Heartbeat received"
        ),
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Status(HttpStatus.ACCEPTED)
    override fun sendHeartbeatViaPost(clientSecret: String) {
        monitorActions.updateLastHeartbeat(clientSecret, getCurrentTimestamp())
            ?.also { it.logHeartbeatAcceptance() }
    }

    @ApiResponses(
        ApiResponse(
            responseCode = "202",
            description = "Heartbeat received"
        ),
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Status(HttpStatus.ACCEPTED)
    override fun sendHeartbeatViaGet(clientSecret: String) {
        monitorActions.updateLastHeartbeat(clientSecret, getCurrentTimestamp())
            ?.also { it.logHeartbeatAcceptance() }
    }

    @ApiResponses(
        ApiResponse(
            responseCode = "202",
            description = "Failure received"
        ),
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Status(HttpStatus.ACCEPTED)
    override fun signalFailureViaPost(clientSecret: String, failureDetails: PushMonitorFailureDetailsDto?) {
        monitorActions.signalFailure(
            clientSecret,
            error = failureDetails?.error?.sanitizeAsError() ?: Messages.signaledExplicitError(),
        )?.also { it.logFailureAcceptance() }
    }

    @ApiResponses(
        ApiResponse(
            responseCode = "202",
            description = "Failure received"
        ),
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Status(HttpStatus.ACCEPTED)
    override fun signalFailureViaGet(clientSecret: String) {
        monitorActions.signalFailure(clientSecret, Messages.signaledExplicitError())
            ?.also { it.logFailureAcceptance() }
    }

    private fun PushMonitorRecord.logHeartbeatAcceptance() =
        logger.debug("Received a heartbeat for push monitor [${this.name}]")

    private fun PushMonitorRecord.logFailureAcceptance() =
        logger.debug("Received a manual failure for push monitor [${this.name}]")
}
