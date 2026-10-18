package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import jakarta.validation.ValidationException
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration

private val objectMapper: ObjectMapper = jacksonObjectMapper()

fun monitorToggleUpdate(enabled: Boolean): ObjectNode =
    objectMapper.createObjectNode().put(MonitorDetailsDto::enabled.name, enabled)

fun String?.asDuration() = this?.ifBlank { null }?.let { Duration.parse(it) }

/** The page a list tool returns: the first one, with the same size as the API's, if the args are omitted. */
fun pageRequestOf(page: Int?, size: Int?): PageRequest {
    val pageRequest = PageRequest(page = page ?: PageRequest.DEFAULT_PAGE, size = size ?: PageRequest.DEFAULT_SIZE)
    if (pageRequest.page < 1 || pageRequest.size < 1) {
        throw ValidationException("'page' and 'size' should be at least 1")
    }
    return pageRequest
}

/**
 * The sorting of a list tool, the same as the API's, but its args are matched without regard to the case, and blank
 * ones are ignored.
 */
inline fun <reified F : Enum<F>> sortingOf(sortedBy: String?, sortOrder: String?, default: Sorting<F>): Sorting<F> =
    Sorting.of(
        sortedBy?.ifBlank { null }?.toEnumArg<F>("sortedBy"),
        sortOrder?.ifBlank { null }?.toEnumArg<SortOrder>("sortOrder"),
        default,
    )

inline fun <reified E : Enum<E>> String.toEnumArg(argName: String): E =
    enumValues<E>().firstOrNull { it.name.equals(trim(), ignoreCase = true) }
        ?: throw ValidationException(
            "Unknown $argName: '$this', it should be one of: ${enumValues<E>().joinToString { "'${it.name}'" }}"
        )
