package com.kuvaszuptime.kuvasz.controllers.maintenance

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.MaintenanceWindowValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowCreateDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowExportDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowUpdateDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forOne
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirst
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.convertValue
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.kotlinModule
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.milliseconds

@MicronautTest(environments = ["full-integrations-setup"])
class MaintenanceWindowControllerTest(
    private val client: MaintenanceWindowClient,
    @param:Client("/") private val rawClient: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
) : DatabaseBehaviorSpec() {
    init {

        val mapper = jacksonObjectMapper()
        val assignedIntegration = IntegrationID(IntegrationType.SLACK, "test_implicitly_enabled")

        suspend fun getWindowStatus(id: Long): HttpStatus =
            try {
                rawClient.exchange(HttpRequest.GET("/api/v2/maintenance-windows/$id")).awaitFirst().status
            } catch (ex: HttpClientResponseException) {
                ex.status
            }

        given("the createMaintenanceWindow endpoint") {

            `when`("a valid manual window is created with default parameters") {
                val created = client.createMaintenanceWindow(MaintenanceWindowCreateDto(name = "Manual window"))

                then("it is persisted with the right defaults and is active because it is enabled") {
                    val inDb = maintenanceWindowRepository.findById(created.id).shouldNotBeNull()
                    inDb.name shouldBe "Manual window"
                    inDb.enabled shouldBe true
                    inDb.global shouldBe false
                    inDb.showOnStatusPages shouldBe false
                    inDb.cron.shouldBeNull()
                    inDb.start.shouldBeNull()
                    inDb.monitors.shouldBeEmpty()
                    inDb.integrations.shouldBeEmpty()
                    inDb.createdAt shouldBe inDb.updatedAt
                    created.active.shouldBeTrue()
                    created.nextStart.shouldBeNull()
                    created.endsAt.shouldBeNull()
                }
            }

            `when`("a valid window is created with explicit parameters") {
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "covered")
                val created = client.createMaintenanceWindow(
                    MaintenanceWindowCreateDto(
                        name = "Cron window",
                        description = "A recurring one",
                        global = false,
                        showOnStatusPages = true,
                        cron = "0 2 * * *",
                        duration = "PT1H",
                        monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name).toString()),
                        integrations = listOf(assignedIntegration.toString()),
                    )
                )

                then("it is persisted with the given parameters and a computed next start") {
                    val inDb = maintenanceWindowRepository.findById(created.id).shouldNotBeNull()
                    inDb.description shouldBe "A recurring one"
                    inDb.showOnStatusPages shouldBe true
                    inDb.cron shouldBe "0 2 * * *"
                    inDb.duration shouldBe "PT1H"
                    inDb.monitors shouldContainExactly arrayOf(MonitorID(MonitorType.HTTP_SSL, monitor.name))
                    inDb.integrations shouldContainExactly arrayOf(assignedIntegration)
                    created.nextStart.shouldNotBeNull()
                }
            }

            `when`("a future single window is created") {
                val created = client.createMaintenanceWindow(
                    MaintenanceWindowCreateDto(
                        name = "Single window",
                        start = OffsetDateTime.now().plusDays(1),
                        duration = "PT2H",
                    )
                )

                then("it is not active yet but has a next start") {
                    created.active.shouldBeFalse()
                    created.nextStart.shouldNotBeNull()
                }
            }

            `when`("there is already a window with the same name") {
                client.createMaintenanceWindow(MaintenanceWindowCreateDto(name = "Duplicated"))

                val response = shouldThrow<HttpClientResponseException> {
                    client.createMaintenanceWindow(MaintenanceWindowCreateDto(name = "Duplicated"))
                }

                then("it returns a 409") {
                    response.status shouldBe HttpStatus.CONFLICT
                    response.message shouldContain "There is already a maintenance window with the given name"
                }
            }

            `when`("the name is blank") {
                then("it returns a 400") {
                    val response = shouldThrow<HttpClientResponseException> {
                        client.createMaintenanceWindow(MaintenanceWindowCreateDto(name = ""))
                    }
                    response.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("the cron expression is invalid") {
                then("it returns a 400 and nothing is persisted") {
                    val response = shouldThrow<HttpClientResponseException> {
                        client.createMaintenanceWindow(
                            MaintenanceWindowCreateDto(name = "Bad cron", cron = "not a cron", duration = "PT1H")
                        )
                    }
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    maintenanceWindowRepository.fetchAll().shouldBeEmpty()
                }
            }

            `when`("the duration is not a positive ISO-8601 duration") {
                then("it returns a 400") {
                    val response = shouldThrow<HttpClientResponseException> {
                        client.createMaintenanceWindow(
                            MaintenanceWindowCreateDto(name = "Bad duration", cron = "0 2 * * *", duration = "PT0S")
                        )
                    }
                    response.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("both cron and start are provided") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.createMaintenanceWindow(
                        MaintenanceWindowCreateDto(
                            name = "Ambiguous",
                            cron = "0 2 * * *",
                            start = OffsetDateTime.now(),
                            duration = "PT1H",
                        )
                    )
                }

                then("the schedule combination is rejected with a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    response.message shouldContain MaintenanceWindowValidationMessages.SCHEDULE_INVALID
                }
            }

            `when`("a cron window is created without a duration") {
                then("it is rejected with a 400") {
                    val response = shouldThrow<HttpClientResponseException> {
                        client.createMaintenanceWindow(
                            MaintenanceWindowCreateDto(
                                name = "No duration",
                                cron = "0 2 * * *"
                            )
                        )
                    }
                    response.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("it is called with an invalid monitor ID format") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.createMaintenanceWindow(
                        MaintenanceWindowCreateDto(name = "Bad monitor", monitors = listOf("invalid-monitor-id"))
                    )
                }

                then("it returns a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    response.message shouldContain "Invalid monitor ID format: invalid-monitor-id"
                }
            }

            `when`("it references a non-existing but well-formed monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "existing")
                val created = client.createMaintenanceWindow(
                    MaintenanceWindowCreateDto(
                        name = "Lenient monitors",
                        monitors = listOf("http:non-existing-monitor", "http:${monitor.name}"),
                    )
                )

                then("the non-existing monitor is filtered out and the existing one is persisted") {
                    created.monitors shouldHaveSingleElement MonitorID(MonitorType.HTTP_SSL, monitor.name)
                }
            }

            `when`("it references a non-existing integration") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.createMaintenanceWindow(
                        MaintenanceWindowCreateDto(
                            name = "Bad integration",
                            integrations = listOf(IntegrationID(IntegrationType.SLACK, "nope").toString()),
                        )
                    )
                }

                then("it returns a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("it is called with an invalid integration ID format") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.createMaintenanceWindow(
                        MaintenanceWindowCreateDto(name = "Bad integration format", integrations = listOf("nonsense"))
                    )
                }

                then("it returns a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }

        given("the getMaintenanceWindow(s) endpoints") {

            `when`("windows exist") {
                createMaintenanceWindow(dslContext, name = "Window A")
                val windowB = createMaintenanceWindow(dslContext, name = "Window B")

                then("they can be listed and fetched by ID") {
                    client.getMaintenanceWindows() shouldHaveSize 2
                    client.getMaintenanceWindow(windowB.id).name shouldBe "Window B"
                }
            }

            `when`("a non-existing window is requested") {
                then("it returns a 404") {
                    getWindowStatus(123456L) shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("the deleteMaintenanceWindow endpoint") {

            `when`("it is called with an existing window ID") {
                val window = createMaintenanceWindow(dslContext, name = "To delete")
                val response = rawClient
                    .exchange(HttpRequest.DELETE("/api/v2/maintenance-windows/${window.id}")).awaitFirst()

                then("it deletes the window and returns a 204") {
                    response.status shouldBe HttpStatus.NO_CONTENT
                    maintenanceWindowRepository.findById(window.id).shouldBeNull()
                }
            }

            `when`("it is called with a non-existing window ID") {
                val response = shouldThrow<HttpClientResponseException> {
                    rawClient.exchange(HttpRequest.DELETE("/api/v2/maintenance-windows/123456")).awaitFirst()
                }

                then("it returns a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("the updateMaintenanceWindow endpoint") {

            `when`("a manual window is converted into a cron window with all values updated") {
                val window = createMaintenanceWindow(dslContext, name = "Manual", description = "before")
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(MaintenanceWindowUpdateDto::description.name, "after")
                    .put(MaintenanceWindowUpdateDto::global.name, true)
                    .put(MaintenanceWindowUpdateDto::cron.name, "0 2 * * *")
                    .put(MaintenanceWindowUpdateDto::duration.name, "PT1H")

                delay(1000.milliseconds) // make sure updatedAt differs from createdAt
                val updated = client.updateMaintenanceWindow(window.id, updateDto)
                val inDb = maintenanceWindowRepository.findById(window.id).shouldNotBeNull()

                then("the schedule type change is applied and persisted") {
                    inDb.description shouldBe "after"
                    inDb.global shouldBe true
                    inDb.cron shouldBe "0 2 * * *"
                    inDb.duration shouldBe "PT1H"
                    inDb.createdAt shouldBe window.createdAt
                    inDb.updatedAt shouldBeAfter window.createdAt
                    updated.nextStart.shouldNotBeNull()
                }
            }

            `when`("a window is disabled via a partial update") {
                val window = createMaintenanceWindow(dslContext, name = "Enabled", enabled = true)
                val updateDto =
                    JsonNodeFactory.instance.objectNode().put(MaintenanceWindowUpdateDto::enabled.name, false)

                val updated = client.updateMaintenanceWindow(window.id, updateDto)

                then("it becomes inactive") {
                    updated.enabled.shouldBeFalse()
                    updated.active.shouldBeFalse()
                }
            }

            `when`("the referenced monitors are removed via an empty array") {
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "covered")
                val window = createMaintenanceWindow(
                    dslContext,
                    name = "With monitors",
                    monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
                )
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .set(MaintenanceWindowUpdateDto::monitors.name, mapper.createArrayNode())

                val updated = client.updateMaintenanceWindow(window.id, updateDto)

                then("the monitors are cleared") {
                    updated.monitors.shouldBeEmpty()
                    maintenanceWindowRepository.findById(window.id).shouldNotBeNull().monitors.shouldBeEmpty()
                }
            }

            `when`("the monitors are omitted in the update") {
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "covered")
                val window = createMaintenanceWindow(
                    dslContext,
                    name = "Keep monitors",
                    monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
                )
                val updateDto = JsonNodeFactory.instance.objectNode().put(MaintenanceWindowUpdateDto::global.name, true)

                val updated = client.updateMaintenanceWindow(window.id, updateDto)

                then("the monitors remain unchanged") {
                    updated.global shouldBe true
                    updated.monitors shouldContainExactlyInAnyOrder setOf(MonitorID(MonitorType.HTTP_SSL, monitor.name))
                }
            }

            `when`("it is renamed to a name that already exists") {
                createMaintenanceWindow(dslContext, name = "Existing name")
                val window = createMaintenanceWindow(dslContext, name = "Original name")
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(MaintenanceWindowUpdateDto::name.name, "Existing name")

                val response = shouldThrow<HttpClientResponseException> {
                    client.updateMaintenanceWindow(window.id, updateDto)
                }

                then("it returns a 409 and the window is unchanged") {
                    response.status shouldBe HttpStatus.CONFLICT
                    maintenanceWindowRepository.findById(window.id).shouldNotBeNull().name shouldBe "Original name"
                }
            }

            `when`("it is called with a blank name") {
                val window = createMaintenanceWindow(dslContext, name = "Original")
                val updateDto = JsonNodeFactory.instance.objectNode().put(MaintenanceWindowUpdateDto::name.name, "   ")

                val response = shouldThrow<HttpClientResponseException> {
                    client.updateMaintenanceWindow(window.id, updateDto)
                }

                then("it returns a 400 and the window is unchanged") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    maintenanceWindowRepository.findById(window.id).shouldNotBeNull().name shouldBe "Original"
                }
            }

            `when`("it is called with a null on a non-nullable property") {
                val window = createMaintenanceWindow(dslContext, name = "Original", enabled = true)
                val updateDto = JsonNodeFactory.instance.objectNode().putNull(MaintenanceWindowUpdateDto::enabled.name)

                val response = shouldThrow<HttpClientResponseException> {
                    client.updateMaintenanceWindow(window.id, updateDto)
                }

                then("it returns a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    response.message shouldContain "must not be null"
                }
            }

            `when`("it is called with an invalid cron expression") {
                val window = createMaintenanceWindow(dslContext, name = "Cron", cron = "0 2 * * *", duration = "PT1H")
                val updateDto =
                    JsonNodeFactory.instance.objectNode().put(MaintenanceWindowUpdateDto::cron.name, "bad cron")

                val response = shouldThrow<HttpClientResponseException> {
                    client.updateMaintenanceWindow(window.id, updateDto)
                }

                then("it returns a 400 and the window is unchanged") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    maintenanceWindowRepository.findById(window.id).shouldNotBeNull().cron shouldBe "0 2 * * *"
                }
            }

            `when`("it is called with an invalid monitor ID") {
                val window = createMaintenanceWindow(dslContext, name = "Original")
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .set(
                        MaintenanceWindowUpdateDto::monitors.name,
                        mapper.createArrayNode().add("invalid-monitor-id"),
                    )

                val response = shouldThrow<HttpClientResponseException> {
                    rawClient.exchange(
                        HttpRequest.PATCH("/api/v2/maintenance-windows/${window.id}", updateDto)
                    ).awaitFirst()
                }

                then("it returns a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("it is called with a non-existing window ID") {
                val updateDto = JsonNodeFactory.instance.objectNode().put(MaintenanceWindowUpdateDto::global.name, true)

                val response = shouldThrow<HttpClientResponseException> {
                    rawClient.exchange(HttpRequest.PATCH("/api/v2/maintenance-windows/123456", updateDto)).awaitFirst()
                }

                then("it returns a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("the getYamlMaintenanceWindowsExport endpoint") {
            val yamlMapper = YAMLMapper.builder()
                .addModules(kotlinModule())
                .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .build()

            `when`("there are windows in the database") {
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "covered")
                createMaintenanceWindow(
                    dslContext,
                    name = "Cron window",
                    cron = "0 2 * * *",
                    duration = "PT1H",
                    showOnStatusPages = true,
                    monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
                )
                createMaintenanceWindow(dslContext, name = "Manual window", global = true)

                val request = HttpRequest.GET<Any>("/api/v2/maintenance-windows/export/yaml")
                    .accept(MediaType.APPLICATION_YAML)

                then("it exports them in YAML format") {
                    val response = rawClient.exchange(request).awaitFirst()
                    val responseBody = response.getBodyAs<ByteArray>()

                    response.status shouldBe HttpStatus.OK
                    with(response.headers[HttpHeaders.CONTENT_DISPOSITION]) {
                        this shouldContain "attachment;"
                        this shouldContain Regex("filename=\"kuvasz-maintenance-windows-export-\\d+\\.yml\"")
                    }
                    response.headers[HttpHeaders.CONTENT_TYPE] shouldBe MediaType.APPLICATION_YAML

                    val exportedRaw = yamlMapper.readTree(responseBody)["maintenance-windows"].shouldNotBeNull()
                    val parsed =
                        yamlMapper.convertValue<List<MaintenanceWindowExportDto>>(exportedRaw).shouldNotBeEmpty()

                    parsed shouldHaveSize 2
                    parsed.forOne { cronWindow ->
                        cronWindow.name shouldBe "Cron window"
                        cronWindow.cron shouldBe "0 2 * * *"
                        cronWindow.duration shouldBe "PT1H"
                        cronWindow.showOnStatusPages shouldBe true
                        cronWindow.monitors shouldContainExactly setOf(MonitorID(MonitorType.HTTP_SSL, monitor.name))
                    }
                    parsed.forOne { manualWindow ->
                        manualWindow.name shouldBe "Manual window"
                        manualWindow.global shouldBe true
                        manualWindow.cron.shouldBeNull()
                    }
                }
            }

            `when`("there are no windows in the database") {
                val request = HttpRequest.GET<Any>("/api/v2/maintenance-windows/export/yaml")
                    .accept(MediaType.APPLICATION_YAML)

                then("it exports an empty list in YAML format") {
                    val response = rawClient.exchange(request).awaitFirst()
                    val responseBody = response.getBodyAs<ByteArray>()

                    response.status shouldBe HttpStatus.OK
                    val exportedRaw = yamlMapper.readTree(responseBody)["maintenance-windows"].shouldNotBeNull()
                    yamlMapper.convertValue<List<MaintenanceWindowExportDto>>(exportedRaw).shouldBeEmpty()
                }
            }
        }
    }
}
