package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.models.DuplicationException
import com.kuvaszuptime.kuvasz.models.MonitorCannotBeDeletedException
import com.kuvaszuptime.kuvasz.models.ReadOnlyMaintenanceWindowException
import com.kuvaszuptime.kuvasz.models.ReadOnlyMonitorException
import com.kuvaszuptime.kuvasz.models.ResourceNotFoundException
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper
import io.modelcontextprotocol.spec.McpError
import io.modelcontextprotocol.spec.McpSchema
import jakarta.inject.Singleton
import jakarta.validation.ValidationException
import java.time.format.DateTimeParseException

@Singleton
internal class ResourceNotFoundExceptionMcpMapper : McpErrorExceptionMapper<ResourceNotFoundException> {
    override fun canMap(clazz: Class<out Throwable>) =
        ResourceNotFoundException::class.java.isAssignableFrom(clazz)

    override fun map(exception: ResourceNotFoundException): McpError =
        McpError.builder(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
            .message(exception.message ?: "Resource not found")
            .build()
}

@Singleton
internal class DuplicationExceptionMcpMapper : McpErrorExceptionMapper<DuplicationException> {
    override fun canMap(clazz: Class<out Throwable>) =
        DuplicationException::class.java.isAssignableFrom(clazz)

    override fun map(exception: DuplicationException): McpError =
        McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
            .message(exception.message ?: "Resource already exists")
            .build()
}

@Singleton
internal class ReadOnlyMonitorExceptionMcpMapper : McpErrorExceptionMapper<ReadOnlyMonitorException> {
    override fun canMap(clazz: Class<out Throwable>) =
        ReadOnlyMonitorException::class.java.isAssignableFrom(clazz)

    override fun map(exception: ReadOnlyMonitorException): McpError =
        McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
            .message(exception.message)
            .build()
}

@Singleton
internal class ReadOnlyMaintenanceWindowExceptionMcpMapper :
    McpErrorExceptionMapper<ReadOnlyMaintenanceWindowException> {
    override fun canMap(clazz: Class<out Throwable>) =
        ReadOnlyMaintenanceWindowException::class.java.isAssignableFrom(clazz)

    override fun map(exception: ReadOnlyMaintenanceWindowException): McpError =
        McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
            .message(exception.message)
            .build()
}

@Singleton
internal class MonitorCannotBeDeletedExceptionMcpMapper : McpErrorExceptionMapper<MonitorCannotBeDeletedException> {
    override fun canMap(clazz: Class<out Throwable>) =
        MonitorCannotBeDeletedException::class.java.isAssignableFrom(clazz)

    override fun map(exception: MonitorCannotBeDeletedException): McpError =
        McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
            .message(exception.message)
            .build()
}

@Singleton
internal class DateTimeParseExceptionMcpMapper : McpErrorExceptionMapper<DateTimeParseException> {
    override fun canMap(clazz: Class<out Throwable>) =
        DateTimeParseException::class.java.isAssignableFrom(clazz)

    override fun map(exception: DateTimeParseException): McpError =
        McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
            .message(exception.message)
            .build()
}

@Singleton
internal class ValidationExceptionMcpMapper : McpErrorExceptionMapper<ValidationException> {
    override fun canMap(clazz: Class<out Throwable>) =
        ValidationException::class.java.isAssignableFrom(clazz)

    override fun map(exception: ValidationException): McpError =
        McpError.builder(McpSchema.ErrorCodes.INVALID_PARAMS)
            .message(exception.message)
            .build()
}
