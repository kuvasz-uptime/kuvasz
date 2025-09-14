package com.kuvaszuptime.kuvasz.controllers.statuspage

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.StatusPageValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageCreateDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageExportDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageUpdateDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.kotest.assertions.exceptionToMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.date.shouldBeAfter
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

@MicronautTest
class StatusPageControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorRepository: HttpMonitorRepository,
    private val statusPageClient: StatusPageClient,
    private val statusPageRepository: StatusPageRepository,
) : DatabaseBehaviorSpec() {

    private val mapper = jacksonObjectMapper()

    init {
        given("StatusPageController's getStatusPagesExport() endpoint") {
            val mapper = YAMLMapper()
                .registerModules(kotlinModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

            `when`("there are status pages in the database") {
                val monitor = createMonitor(
                    monitorRepository,
                    monitorName = "irrelevant",
                )
                val monitor2 = createMonitor(
                    monitorRepository,
                    monitorName = "irrelevant2",
                )
                val statusPage1 = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                        MonitorID(MonitorType.HTTP_SSL, monitor2.name),
                    ),
                )
                val statusPage2 = createStatusPage(
                    dslContext,
                    title = "Status Page 2",
                    slug = "status-page-2",
                    monitors = emptyList(),
                )
                val statusPage3 = createStatusPage(
                    dslContext,
                    title = "Status Page 3",
                    slug = "status-page-3",
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                    ),
                )

                val request =
                    HttpRequest.GET<Any>("/api/v2/status-pages/export/yaml").accept(MediaType.APPLICATION_YAML)

                then("it should export them in YAML format") {
                    val response = client.exchange(request).awaitFirst()
                    val responseBody = response.getBody(ByteArray::class.java).get()

                    response.status shouldBe HttpStatus.OK
                    with(response.headers[HttpHeaders.CONTENT_DISPOSITION]) {
                        this shouldContain "attachment;"
                        this shouldContain Regex("filename=\"kuvasz-status-pages-export-\\d+\\.yml\"")
                    }
                    response.headers[HttpHeaders.CONTENT_TYPE] shouldBe MediaType.APPLICATION_YAML

                    val exportedPagesRaw = mapper.readTree(responseBody)["status-pages"].shouldNotBeNull()
                    val parsedPages =
                        mapper.convertValue<List<StatusPageExportDto>>(exportedPagesRaw).shouldNotBeEmpty()

                    parsedPages.size shouldBe 3
                    parsedPages.forOne { page1 ->
                        page1.title shouldBe statusPage1.title
                        page1.slug shouldBe statusPage1.slug
                        page1.enabled shouldBe statusPage1.enabled
                        page1.monitors.shouldContainExactlyInAnyOrder(
                            listOf(
                                MonitorID(MonitorType.HTTP_SSL, monitor.name),
                                MonitorID(MonitorType.HTTP_SSL, monitor2.name),
                            )
                        )
                    }
                    parsedPages.forOne { page2 ->
                        page2.title shouldBe statusPage2.title
                        page2.slug shouldBe statusPage2.slug
                        page2.enabled shouldBe statusPage2.enabled
                        page2.monitors.shouldBeEmpty()
                    }
                    parsedPages.forOne { page3 ->
                        page3.title shouldBe statusPage3.title
                        page3.slug shouldBe statusPage3.slug
                        page3.enabled shouldBe statusPage3.enabled
                        page3.monitors shouldHaveSingleElement
                            MonitorID(MonitorType.HTTP_SSL, monitor.name)
                    }
                }
            }

            `when`("there are no status pages in the database") {

                val request = HttpRequest
                    .GET<Any>("/api/v2/status-pages/export/yaml")
                    .accept(MediaType.APPLICATION_YAML)

                then("it should export an empty status pages list in YAML format") {
                    val response = client.exchange(request).awaitFirst()
                    val responseBody = response.getBodyAs<ByteArray>()

                    response.status shouldBe HttpStatus.OK
                    val exportedPagesRaw = mapper.readTree(responseBody)["status-pages"].shouldNotBeNull()
                    mapper.convertValue<List<StatusPageExportDto>>(exportedPagesRaw).shouldBeEmpty()
                }
            }
        }

        given("StatusPageController's getStatusPages() endpoint") {
            `when`("there are statusPages in the database") {
                val monitor = createMonitor(monitorRepository)
                val page = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                    ),
                )
                val page2 = createStatusPage(
                    dslContext,
                    enabled = false,
                    title = "Status Page 2",
                    slug = "status-page-2",
                )

                val response = statusPageClient.getStatusPages(enabled = null)

                then("it should return them") {
                    response shouldHaveSize 2
                    response.forOne { firstPage ->
                        firstPage.id shouldBe page.id
                        firstPage.title shouldBe page.title
                        firstPage.slug shouldBe page.slug
                        firstPage.enabled shouldBe page.enabled
                        firstPage.monitors shouldContainExactly listOf(
                            MonitorID(MonitorType.HTTP_SSL, monitor.name)
                        )
                    }
                    response.forOne { secondPage ->
                        secondPage.id shouldBe page2.id
                        secondPage.title shouldBe page2.title
                        secondPage.slug shouldBe page2.slug
                        secondPage.enabled shouldBe page2.enabled
                        secondPage.monitors.shouldBeEmpty()
                    }
                }
            }

            `when`("enabled parameter is set to true") {
                val enabledPage = createStatusPage(dslContext, title = "Enabled Page", slug = "enabled-page")
                createStatusPage(dslContext, enabled = false, title = "Disabled Page", slug = "disabled-page")

                val response = statusPageClient.getStatusPages(enabled = true)

                then("it should return only the enabled status pages") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe enabledPage.id
                    responseItem.title shouldBe enabledPage.title
                    responseItem.slug shouldBe enabledPage.slug
                    responseItem.enabled shouldBe enabledPage.enabled
                    responseItem.monitors.shouldBeEmpty()
                }
            }

            `when`("enabled parameter is set to false") {
                createStatusPage(dslContext, title = "Enabled Page", slug = "enabled-page")
                val disabledPage = createStatusPage(
                    dslContext,
                    enabled = false,
                    title = "Disabled Page",
                    slug = "disabled-page"
                )

                val response = statusPageClient.getStatusPages(enabled = false)

                then("it should return only the disabled status pages") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe disabledPage.id
                    responseItem.title shouldBe disabledPage.title
                    responseItem.slug shouldBe disabledPage.slug
                    responseItem.enabled shouldBe disabledPage.enabled
                    responseItem.monitors.shouldBeEmpty()
                }
            }

            `when`("there isn't any status page in the database") {
                val response = statusPageClient.getStatusPages(enabled = null)

                then("it should return an empty list") {
                    response.shouldBeEmpty()
                }
            }
        }

        given("StatusPageController's getStatusPage() endpoint") {
            `when`("there is a status page with the given ID in the database") {
                val monitor = createMonitor(monitorRepository)
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                    ),
                )

                val response = statusPageClient.getStatusPage(statusPageId = statusPage.id)

                then("it should return it") {
                    response.id shouldBe statusPage.id
                    response.title shouldBe statusPage.title
                    response.slug shouldBe statusPage.slug
                    response.enabled shouldBe statusPage.enabled
                    response.monitors shouldContainExactly listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name)
                    )
                }
            }

            `when`("there is no status page with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/status-pages/1232132432").awaitFirst()
                }
                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("StatusPageController's createStatusPage() endpoint") {

            `when`("it is called with a valid DTO - default parameters") {
                val pageToCreate = StatusPageCreateDto(
                    title = "Status Page 1",
                    slug = "status-page-1",
                )
                val createdMonitor = statusPageClient.createStatuspage(pageToCreate)

                then("it should create a status page with the right default parameters") {

                    val pageInDb = statusPageRepository.findById(createdMonitor.id).shouldNotBeNull()
                    pageInDb.title shouldBe "Status Page 1"
                    pageInDb.title shouldBe createdMonitor.title
                    pageInDb.slug shouldBe "status-page-1"
                    pageInDb.slug shouldBe createdMonitor.slug
                    pageInDb.enabled shouldBe true
                    pageInDb.enabled shouldBe createdMonitor.enabled
                    pageInDb.createdAt shouldBe createdMonitor.createdAt
                    pageInDb.updatedAt shouldBe pageInDb.createdAt
                    pageInDb.monitors.shouldBeEmpty()
                }
            }

            `when`("it is called with a valid DTO - explicit parameters") {
                val monitor = createMonitor(monitorRepository)
                val monitor2 = createMonitor(monitorRepository)
                val pageToCreate = StatusPageCreateDto(
                    title = "Status Page 1",
                    slug = "status-page-1",
                    enabled = false,
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name).toString(),
                        MonitorID(MonitorType.HTTP_SSL, monitor2.name).toString(),
                    )
                )
                val createdPage = statusPageClient.createStatuspage(pageToCreate)

                then("it should create a status page with the given parameters") {
                    val pageInDb = statusPageRepository.findById(createdPage.id).shouldNotBeNull()
                    pageInDb.title shouldBe "Status Page 1"
                    pageInDb.title shouldBe createdPage.title
                    pageInDb.slug shouldBe "status-page-1"
                    pageInDb.slug shouldBe createdPage.slug
                    pageInDb.enabled shouldBe false
                    pageInDb.enabled shouldBe createdPage.enabled
                    pageInDb.createdAt shouldBe createdPage.createdAt
                    pageInDb.updatedAt shouldBe pageInDb.createdAt
                    pageInDb.monitors shouldContainExactly arrayOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                        MonitorID(MonitorType.HTTP_SSL, monitor2.name),
                    )
                }
            }

            `when`("there is already a status page with the same slug") {
                val pageToCreate = StatusPageCreateDto(
                    title = "Status Page 1",
                    slug = "status-page-1",
                )
                statusPageClient.createStatuspage(pageToCreate)

                val response = shouldThrow<HttpClientResponseException> {
                    statusPageClient.createStatuspage(pageToCreate.copy(title = "Status Page 2"))
                }

                then("it should return a 409") {
                    response.status shouldBe HttpStatus.CONFLICT
                    response.message shouldContain "There is already a status page with the given slug"
                }
            }

            `when`("it is called with an invalid property") {
                val pageToCreate = StatusPageCreateDto(
                    title = "Status Page 1",
                    slug = "Invalid Slug",
                )

                val response = shouldThrow<HttpClientResponseException> {
                    statusPageClient.createStatuspage(pageToCreate)
                }

                then("the DTO should be validated, response should be a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain StatusPageValidationMessages.SLUG_PATTERN
                }
            }

            `when`("it is called with an invalid monitor ID") {
                val pageToCreate = StatusPageCreateDto(
                    title = "Status Page 1",
                    slug = "status-page-1",
                    monitors = listOf("invalid-monitor-id")
                )

                val response = shouldThrow<HttpClientResponseException> {
                    statusPageClient.createStatuspage(pageToCreate)
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain
                        "Invalid monitor ID format: invalid-monitor-id. Expected format is 'type:name'"
                }
            }

            `when`("it is called with a non-existing monitor") {
                val pageToCreate = StatusPageCreateDto(
                    title = "Status Page 1",
                    slug = "status-page-1",
                    monitors = listOf("http:non-existing-monitor")
                )

                val response = shouldThrow<HttpClientResponseException> {
                    statusPageClient.createStatuspage(pageToCreate)
                }

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain
                        "Non-existing monitor ID found: http:non-existing-monitor. " +
                        "Make sure the monitor is defined before referencing it."
                }
            }
        }

        given("StatusPageController's deleteStatusPage() endpoint") {

            `when`("it is called with an existing status page ID") {

                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                )

                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/status-pages/${statusPage.id}")
                val response = client.exchange(deleteRequest).awaitFirst()
                val pageInDb = statusPageRepository.findById(statusPage.id)

                then("it should delete the status page") {
                    response.status shouldBe HttpStatus.NO_CONTENT
                    pageInDb shouldBe null
                }
            }

            `when`("it is called with a non existing status page ID") {

                val deleteRequest = HttpRequest.DELETE<Any>("/api/v2/status-pages/1232132432")
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(deleteRequest).awaitFirst()
                }

                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("StatusPageController's updateStatusPage() endpoint") {

            `when`("it is called with an existing status page ID and a valid DTO to update all of the values") {
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                )
                val monitor = createMonitor(monitorRepository, monitorName = "monitor1")
                val monitor2 = createMonitor(monitorRepository, monitorName = "monitor2")
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(StatusPageUpdateDto::enabled.name, false)
                    .put(StatusPageUpdateDto::title.name, "Updated Status Page")
                    .put(StatusPageUpdateDto::slug.name, "updated-status-page")
                    .set<ObjectNode>(
                        StatusPageUpdateDto::monitors.name,
                        mapper
                            .createArrayNode()
                            .add("http:${monitor.name}")
                            .add("http:${monitor2.name}")
                    )

                delay(1000) // Ensure that updatedAt will be different than createdAt
                statusPageClient.updateStatusPage(statusPage.id, updateDto)
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should update the status page") {

                    statusPageInDb.title shouldBe "Updated Status Page"
                    statusPageInDb.slug shouldBe "updated-status-page"
                    statusPageInDb.enabled shouldBe false
                    statusPageInDb.createdAt shouldBe statusPage.createdAt
                    statusPageInDb.updatedAt shouldBeAfter statusPage.createdAt
                    statusPageInDb.monitors shouldContainExactly arrayOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                        MonitorID(MonitorType.HTTP_SSL, monitor2.name),
                    )
                }
            }

            `when`("it is called to remove all the referenced monitors") {

                val monitor = createMonitor(monitorRepository, monitorName = "monitor1")
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                    )
                )
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .set<ObjectNode>(
                        StatusPageUpdateDto::monitors.name,
                        mapper.createArrayNode()
                    )

                val updatedPage = statusPageClient.updateStatusPage(statusPage.id, updateDto)
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should update the status page and remove the monitors from it") {
                    updatedPage.monitors.shouldBeEmpty()
                    statusPageInDb.monitors.shouldBeEmpty()
                }
            }

            `when`("monitors are omitted in the update") {

                val monitor = createMonitor(monitorRepository, monitorName = "monitor1")
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                    )
                )
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(StatusPageUpdateDto::enabled.name, false)

                val updatedPage = statusPageClient.updateStatusPage(statusPage.id, updateDto)
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("the monitors should remain unchanged") {
                    updatedPage.enabled shouldBe false
                    updatedPage.monitors shouldContainExactly listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name)
                    )
                    statusPageInDb.enabled shouldBe updatedPage.enabled
                    statusPageInDb.monitors shouldContainExactly arrayOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name)
                    )
                }
            }

            `when`("it is called with an existing page ID but there is another page with the given slug") {

                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                )
                val anotherStatusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 2",
                    slug = "status-page-2",
                )

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(StatusPageUpdateDto::slug.name, anotherStatusPage.slug)

                val response = shouldThrow<HttpClientResponseException> {
                    statusPageClient.updateStatusPage(statusPage.id, updateDto)
                }
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should return a 409") {
                    response.status shouldBe HttpStatus.CONFLICT
                    statusPageInDb.slug shouldBe statusPage.slug
                }
            }

            `when`("it is called with a blank title") {
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                )
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(StatusPageUpdateDto::title.name, "   ")
                val response = shouldThrow<HttpClientResponseException> {
                    statusPageClient.updateStatusPage(statusPage.id, updateDto)
                }
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain
                        "Validation failed: title: Status page title must not be blank"
                    statusPageInDb.title shouldBe statusPage.title
                }
            }

            `when`("it is called with a null on a property that is non-nullable") {
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                )
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .putNull(StatusPageUpdateDto::enabled.name)
                val response = shouldThrow<HttpClientResponseException> {
                    statusPageClient.updateStatusPage(statusPage.id, updateDto)
                }
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain "Validation failed: enabled: must not be null"
                    statusPageInDb.enabled shouldBe statusPage.enabled
                }
            }

            `when`("it is called with an invalid slug") {

                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                )
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(StatusPageUpdateDto::slug.name, "Invalid Slug")
                val response = shouldThrow<HttpClientResponseException> {
                    statusPageClient.updateStatusPage(statusPage.id, updateDto)
                }
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain StatusPageValidationMessages.SLUG_PATTERN
                    statusPageInDb.slug shouldBe statusPage.slug
                }
            }

            `when`("it is called with a non existing status page ID") {

                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(StatusPageUpdateDto::title.name, "Updated Status Page")
                val updateRequest = HttpRequest.PATCH(
                    "/api/v2/status-pages/123123124",
                    updateDto
                )

                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }

                then("it should return a 404") {
                    response.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("it is called with an invalid monitor ID") {

                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                )
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .set<ObjectNode>(
                        StatusPageUpdateDto::monitors.name,
                        mapper.createArrayNode().add("invalid-monitor-id")
                    )
                val updateRequest = HttpRequest.PATCH(
                    "/api/v2/status-pages/${statusPage.id}",
                    updateDto
                )
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange(updateRequest).awaitFirst()
                }
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain "Invalid JSON"
                    statusPageInDb.monitors.shouldBeEmpty()
                }
            }

            `when`("it is called with a non-existing monitor") {
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                )
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .set<ObjectNode>(
                        StatusPageUpdateDto::monitors.name,
                        mapper
                            .createArrayNode()
                            .add("http:non-existing-monitor")
                    )
                val response = shouldThrow<HttpClientResponseException> {
                    statusPageClient.updateStatusPage(statusPage.id, updateDto)
                }
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain
                        "Non-existing monitor ID found: http:non-existing-monitor. " +
                        "Make sure the monitor is defined before referencing it."
                    statusPageInDb.monitors.shouldBeEmpty()
                }
            }

            `when`("it is called to update a non-updatable field") {
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                )
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(StatusPageUpdateDto::title.name, "Updated Status Page")
                    .put("createdAt", "2024-01-01T00:00:00Z")

                statusPageClient.updateStatusPage(statusPage.id, updateDto)
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("the non-updatable field should be ignored") {
                    statusPageInDb.title shouldBe "Updated Status Page"
                    statusPageInDb.createdAt shouldBe statusPage.createdAt
                }
            }
        }
    }
}
