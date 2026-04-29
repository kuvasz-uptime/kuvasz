package com.kuvaszuptime.kuvasz.controllers.statuspage

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.config.DefaultStatusPageConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.StatusPageValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageCreateDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageExportDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageHttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageUpdateDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getBodyAs
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
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
import io.micronaut.context.annotation.Property
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirst
import kotlin.time.Duration.Companion.milliseconds

@MicronautTest
@Property(name = "default-status-page.public", value = StringUtils.TRUE)
class StatusPageControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val statusPageClient: StatusPageClient,
    private val statusPageRepository: StatusPageRepository,
    private val statusPageDataActions: StatusPageDataActions,
    private val defaultStatusPageConfig: DefaultStatusPageConfig,
) : DatabaseBehaviorSpec() {

    private val mapper = jacksonObjectMapper()

    init {
        given("StatusPageController's getStatusPagesExport() endpoint") {
            val mapper = YAMLMapper()
                .registerModules(kotlinModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

            `when`("there are status pages in the database") {
                val monitor = createHttpMonitor(
                    httpMonitorRepository,
                    monitorName = "irrelevant",
                )
                val monitor2 = createHttpMonitor(
                    httpMonitorRepository,
                    monitorName = "irrelevant2",
                )
                val monitor3 = createPushMonitor(
                    pushMonitorRepository,
                    monitorName = "irrelevant3"
                )
                val statusPage1 = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                    customLogoUrl = "https://example.com/logo.png",
                    customFaviconUrl = "https://example.com/favicon.png",
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
                        MonitorID(MonitorType.PUSH, monitor3.name),
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
                        page1.public shouldBe statusPage1.public
                        page1.customLogoUrl shouldBe statusPage1.customLogoUrl
                        page1.customFaviconUrl shouldBe statusPage1.customFaviconUrl
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
                        page2.customLogoUrl shouldBe statusPage2.customLogoUrl
                        page2.customFaviconUrl shouldBe statusPage2.customFaviconUrl
                        page2.public shouldBe statusPage2.public
                        page2.monitors.shouldBeEmpty()
                    }
                    parsedPages.forOne { page3 ->
                        page3.title shouldBe statusPage3.title
                        page3.slug shouldBe statusPage3.slug
                        page3.customLogoUrl shouldBe statusPage3.customLogoUrl
                        page3.customFaviconUrl shouldBe statusPage3.customFaviconUrl
                        page3.public shouldBe statusPage3.public
                        page3.monitors shouldContainExactlyInAnyOrder listOf(
                            MonitorID(MonitorType.HTTP_SSL, monitor.name),
                            MonitorID(MonitorType.PUSH, monitor3.name),
                        )
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
                val httpMonitor = createHttpMonitor(httpMonitorRepository)
                val pushMonitor = createPushMonitor(pushMonitorRepository)
                val page = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                    customLogoUrl = "https://example.com/logo.png",
                    customFaviconUrl = "https://example.com/favicon.png",
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, httpMonitor.name),
                        MonitorID(MonitorType.PUSH, pushMonitor.name),
                    ),
                )
                val page2 = createStatusPage(
                    dslContext,
                    public = false,
                    title = "Status Page 2",
                    slug = "status-page-2",
                )

                val response = statusPageClient.getStatusPages(public = null)

                then("it should return them") {
                    response shouldHaveSize 2
                    response.forOne { firstPage ->
                        firstPage.id shouldBe page.id
                        firstPage.title shouldBe page.title
                        firstPage.slug shouldBe page.slug
                        firstPage.customLogoUrl shouldBe page.customLogoUrl
                        firstPage.customFaviconUrl shouldBe page.customFaviconUrl
                        firstPage.public shouldBe page.public
                        firstPage.monitors shouldContainExactlyInAnyOrder listOf(
                            MonitorID(MonitorType.HTTP_SSL, httpMonitor.name),
                            MonitorID(MonitorType.PUSH, pushMonitor.name),
                        )
                    }
                    response.forOne { secondPage ->
                        secondPage.id shouldBe page2.id
                        secondPage.title shouldBe page2.title
                        secondPage.slug shouldBe page2.slug
                        secondPage.customLogoUrl shouldBe page2.customLogoUrl
                        secondPage.customFaviconUrl shouldBe page2.customFaviconUrl
                        secondPage.public shouldBe page2.public
                        secondPage.monitors.shouldBeEmpty()
                    }
                }
            }

            `when`("public parameter is set to true") {
                val publicPage = createStatusPage(
                    dslContext,
                    public = true,
                    title = "Public Page",
                    slug = "public-page",
                )
                createStatusPage(dslContext, public = false, title = "Private Page", slug = "private-page")

                val response = statusPageClient.getStatusPages(public = true)

                then("it should return only the public status pages") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe publicPage.id
                    responseItem.title shouldBe publicPage.title
                    responseItem.slug shouldBe publicPage.slug
                    responseItem.public shouldBe publicPage.public
                    responseItem.monitors.shouldBeEmpty()
                }
            }

            `when`("public parameter is set to false") {
                createStatusPage(
                    dslContext,
                    public = true,
                    title = "Public Page",
                    slug = "public-page",
                )
                val disabledPage = createStatusPage(
                    dslContext,
                    public = false,
                    title = "Disabled Page",
                    slug = "disabled-page"
                )

                val response = statusPageClient.getStatusPages(public = false)

                then("it should return only the disabled status pages") {
                    response shouldHaveSize 1
                    val responseItem = response.first()
                    responseItem.id shouldBe disabledPage.id
                    responseItem.title shouldBe disabledPage.title
                    responseItem.slug shouldBe disabledPage.slug
                    responseItem.public shouldBe disabledPage.public
                    responseItem.monitors.shouldBeEmpty()
                }
            }

            `when`("there isn't any status page in the database") {
                val response = statusPageClient.getStatusPages(public = null)

                then("it should return an empty list") {
                    response.shouldBeEmpty()
                }
            }
        }

        given("StatusPageController's getStatusPage() endpoint") {
            `when`("there is a status page with the given ID in the database") {
                val httpMonitor = createHttpMonitor(httpMonitorRepository)
                val pushMonitor = createPushMonitor(pushMonitorRepository)
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, httpMonitor.name),
                        MonitorID(MonitorType.PUSH, pushMonitor.name),
                    ),
                )

                val response = statusPageClient.getStatusPage(statusPageId = statusPage.id)

                then("it should return it") {
                    response.id shouldBe statusPage.id
                    response.title shouldBe statusPage.title
                    response.slug shouldBe statusPage.slug
                    response.public shouldBe statusPage.public
                    response.monitors shouldContainExactlyInAnyOrder listOf(
                        MonitorID(MonitorType.HTTP_SSL, httpMonitor.name),
                        MonitorID(MonitorType.PUSH, pushMonitor.name),
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

        given("StatusPageController's getStatusPageDetails() endpoint") {
            `when`("there is a status page with the given ID in the database") {
                val httpMonitor = createHttpMonitor(httpMonitorRepository)
                val pushMonitor = createPushMonitor(pushMonitorRepository)
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                    public = true,
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, httpMonitor.name),
                        MonitorID(MonitorType.PUSH, pushMonitor.name),
                    ),
                )

                val dataMock = getMock(statusPageDataActions)
                val mockDataResponse = StatusPageDataDto(
                    title = statusPage.title,
                    customLogoUrl = statusPage.customLogoUrl,
                    customFaviconUrl = statusPage.customFaviconUrl,
                    systemStatus = SystemStatus.PARTIAL_OUTAGE,
                    generatedAt = getCurrentTimestamp(),
                    monitors = listOf(
                        StatusPageHttpMonitorDetailsDto(
                            name = "irrelevant",
                            lastCheck = getCurrentTimestamp().minusMinutes(5),
                            uptimeRatio = 0.97,
                            uptimeStatus = UptimeStatus.UP,
                            uptimeStatusHistory = emptyList(),
                            averageLatencyInMs = 123,
                        )
                    )
                )
                every { dataMock.getCachedStatusPageData(statusPage.id) } returns mockDataResponse

                val response = statusPageClient.getStatusPageDetails(statusPageId = statusPage.id)

                then("it should return it") {
                    response.id shouldBe statusPage.id
                    response.title shouldBe statusPage.title
                    response.slug shouldBe statusPage.slug
                    response.public shouldBe statusPage.public
                    response.customLogoUrl shouldBe statusPage.customLogoUrl
                    response.customFaviconUrl shouldBe statusPage.customFaviconUrl
                    response.monitors.forOne { monitorDetails ->
                        mockDataResponse.monitors.single().let { mockDetails ->
                            monitorDetails.uptimeStatus shouldBe mockDetails.uptimeStatus
                            monitorDetails.uptimeRatio shouldBe mockDetails.uptimeRatio
                            monitorDetails.uptimeStatusHistory shouldBe mockDetails.uptimeStatusHistory
                            monitorDetails.name shouldBe mockDetails.name
                            monitorDetails.type shouldBe mockDetails.type
                            monitorDetails.lastCheck shouldBe mockDetails.lastCheck.shouldNotBeNull()
                        }
                    }

                    response.systemStatus shouldBe mockDataResponse.systemStatus
                    response.generatedAt shouldBe mockDataResponse.generatedAt
                }
            }

            `when`("the default status page is requested") {
                val dataMock = getMock(statusPageDataActions)
                val mockDataResponse = StatusPageDataDto(
                    title = defaultStatusPageConfig.title,
                    customLogoUrl = defaultStatusPageConfig.customLogoUrl,
                    customFaviconUrl = defaultStatusPageConfig.customFaviconUrl,
                    systemStatus = SystemStatus.PARTIAL_OUTAGE,
                    generatedAt = getCurrentTimestamp(),
                    monitors = listOf(
                        StatusPageHttpMonitorDetailsDto(
                            name = "irrelevant",
                            lastCheck = getCurrentTimestamp().minusMinutes(5),
                            uptimeRatio = 0.97,
                            uptimeStatus = UptimeStatus.UP,
                            uptimeStatusHistory = emptyList(),
                            averageLatencyInMs = 123,
                        )
                    )
                )
                every { dataMock.getCachedDefaultStatusPageData() } returns mockDataResponse

                val response = statusPageClient.getStatusPageDetails(statusPageId = 0)

                then("it should return it") {
                    response.id shouldBe 0
                    response.title shouldBe defaultStatusPageConfig.title
                    response.slug shouldBe null
                    response.public shouldBe defaultStatusPageConfig.public
                    response.customLogoUrl shouldBe defaultStatusPageConfig.customLogoUrl
                    response.customFaviconUrl shouldBe defaultStatusPageConfig.customFaviconUrl
                    response.monitors.forOne { monitorDetails ->
                        mockDataResponse.monitors.single().let { mockDetails ->
                            monitorDetails.uptimeStatus shouldBe mockDetails.uptimeStatus
                            monitorDetails.uptimeRatio shouldBe mockDetails.uptimeRatio
                            monitorDetails.uptimeStatusHistory shouldBe mockDetails.uptimeStatusHistory
                            monitorDetails.name shouldBe mockDetails.name
                            monitorDetails.type shouldBe mockDetails.type
                            monitorDetails.lastCheck shouldBe mockDetails.lastCheck.shouldNotBeNull()
                        }
                    }

                    response.systemStatus shouldBe mockDataResponse.systemStatus
                    response.generatedAt shouldBe mockDataResponse.generatedAt
                }
            }

            `when`("there is no status page with the given ID in the database") {
                val response = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v2/status-pages/1232132432/details").awaitFirst()
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
                    customLogoUrl = "https://example.com/logo.png",
                    customFaviconUrl = "https://example.com/favicon.png",
                )
                val createdMonitor = statusPageClient.createStatuspage(pageToCreate)

                then("it should create a status page with the right default parameters") {

                    val pageInDb = statusPageRepository.findById(createdMonitor.id).shouldNotBeNull()
                    pageInDb.title shouldBe "Status Page 1"
                    pageInDb.title shouldBe createdMonitor.title
                    pageInDb.slug shouldBe "status-page-1"
                    pageInDb.slug shouldBe createdMonitor.slug
                    pageInDb.customLogoUrl shouldBe "https://example.com/logo.png"
                    pageInDb.customLogoUrl shouldBe createdMonitor.customLogoUrl
                    pageInDb.customFaviconUrl shouldBe "https://example.com/favicon.png"
                    pageInDb.customFaviconUrl shouldBe createdMonitor.customFaviconUrl
                    pageInDb.public shouldBe false
                    pageInDb.public shouldBe createdMonitor.public
                    pageInDb.createdAt shouldBe createdMonitor.createdAt
                    pageInDb.updatedAt shouldBe pageInDb.createdAt
                    pageInDb.monitors.shouldBeEmpty()
                }
            }

            `when`("it is called with a valid DTO - explicit parameters") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val monitor2 = createHttpMonitor(httpMonitorRepository)
                val monitor3 = createPushMonitor(pushMonitorRepository)
                val pageToCreate = StatusPageCreateDto(
                    title = "Status Page 1",
                    slug = "status-page-1",
                    public = false,
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name).toString(),
                        MonitorID(MonitorType.HTTP_SSL, monitor2.name).toString(),
                        MonitorID(MonitorType.PUSH, monitor3.name).toString(),
                    )
                )
                val createdPage = statusPageClient.createStatuspage(pageToCreate)

                then("it should create a status page with the given parameters") {
                    val pageInDb = statusPageRepository.findById(createdPage.id).shouldNotBeNull()
                    pageInDb.title shouldBe "Status Page 1"
                    pageInDb.title shouldBe createdPage.title
                    pageInDb.slug shouldBe "status-page-1"
                    pageInDb.slug shouldBe createdPage.slug
                    pageInDb.public shouldBe false
                    pageInDb.public shouldBe createdPage.public
                    pageInDb.createdAt shouldBe createdPage.createdAt
                    pageInDb.updatedAt shouldBe pageInDb.createdAt
                    pageInDb.monitors shouldContainExactly arrayOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                        MonitorID(MonitorType.HTTP_SSL, monitor2.name),
                        MonitorID(MonitorType.PUSH, monitor3.name),
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
                val monitor = createHttpMonitor(httpMonitorRepository)
                val pageToCreate = StatusPageCreateDto(
                    title = "Status Page 1",
                    slug = "status-page-1",
                    monitors = listOf(
                        "http:non-existing-monitor",
                        "http:${monitor.name}"
                    )
                )

                val response = statusPageClient.createStatuspage(pageToCreate)

                then("it should filter it out and persist the existing one") {
                    response.monitors shouldHaveSingleElement MonitorID(MonitorType.HTTP_SSL, monitor.name)
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
                    customLogoUrl = "https://example.com/logo.png",
                    customFaviconUrl = "https://example.com/favicon.png",
                )
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "monitor1")
                val monitor2 = createHttpMonitor(httpMonitorRepository, monitorName = "monitor2")
                val monitor3 = createPushMonitor(pushMonitorRepository, monitorName = "monitor3")
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(StatusPageUpdateDto::public.name, false)
                    .put(StatusPageUpdateDto::title.name, "Updated Status Page")
                    .put(StatusPageUpdateDto::slug.name, "updated-status-page")
                    .put(StatusPageUpdateDto::customLogoUrl.name, "https://example.com/logo2.png")
                    .put(StatusPageUpdateDto::customFaviconUrl.name, "https://example.com/favicon2.png")
                    .set<ObjectNode>(
                        StatusPageUpdateDto::monitors.name,
                        mapper
                            .createArrayNode()
                            .add("http:${monitor.name}")
                            .add("http:${monitor2.name}")
                            .add("push:${monitor3.name}")
                    )

                delay(1000.milliseconds) // Ensure that updatedAt will be different than createdAt
                statusPageClient.updateStatusPage(statusPage.id, updateDto)
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should update the status page") {

                    statusPageInDb.title shouldBe "Updated Status Page"
                    statusPageInDb.slug shouldBe "updated-status-page"
                    statusPageInDb.customLogoUrl shouldBe "https://example.com/logo2.png"
                    statusPageInDb.customFaviconUrl shouldBe "https://example.com/favicon2.png"
                    statusPageInDb.public shouldBe false
                    statusPageInDb.createdAt shouldBe statusPage.createdAt
                    statusPageInDb.updatedAt shouldBeAfter statusPage.createdAt
                    statusPageInDb.monitors shouldContainExactlyInAnyOrder arrayOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                        MonitorID(MonitorType.HTTP_SSL, monitor2.name),
                        MonitorID(MonitorType.PUSH, monitor3.name),
                    )
                }
            }

            `when`("it is called to remove all the referenced monitors") {

                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "monitor1")
                val monitor2 = createPushMonitor(pushMonitorRepository, monitorName = "monitor2")
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                        MonitorID(MonitorType.PUSH, monitor2.name),
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

                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "monitor1")
                val monitor2 = createPushMonitor(pushMonitorRepository, monitorName = "monitor2")
                val statusPage = createStatusPage(
                    dslContext,
                    title = "Status Page 1",
                    slug = "status-page-1",
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                        MonitorID(MonitorType.PUSH, monitor2.name),
                    )
                )
                val updateDto = JsonNodeFactory.instance.objectNode()
                    .put(StatusPageUpdateDto::public.name, false)

                val updatedPage = statusPageClient.updateStatusPage(statusPage.id, updateDto)
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("the monitors should remain unchanged") {
                    updatedPage.public shouldBe false
                    updatedPage.monitors shouldContainExactlyInAnyOrder setOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                        MonitorID(MonitorType.PUSH, monitor2.name),
                    )
                    statusPageInDb.public shouldBe updatedPage.public
                    statusPageInDb.monitors shouldContainExactlyInAnyOrder arrayOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor.name),
                        MonitorID(MonitorType.PUSH, monitor2.name),
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
                    .putNull(StatusPageUpdateDto::public.name)
                val response = shouldThrow<HttpClientResponseException> {
                    statusPageClient.updateStatusPage(statusPage.id, updateDto)
                }
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should return a 400") {
                    response.status shouldBe HttpStatus.BAD_REQUEST
                    exceptionToMessage(response) shouldContain "Validation failed: public: must not be null"
                    statusPageInDb.public shouldBe statusPage.public
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
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "monitor1")
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
                            .add("http:${monitor.name}")
                    )
                val response = statusPageClient.updateStatusPage(statusPage.id, updateDto)
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should ignore the non-existing monitor") {
                    val expectedMonitorId = MonitorID(MonitorType.HTTP_SSL, monitor.name)
                    response.monitors shouldHaveSingleElement expectedMonitorId
                    statusPageInDb.monitors shouldHaveSingleElement expectedMonitorId
                }
            }

            `when`("it is called with a monitor that exists but with a different type") {
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "monitor1")
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
                            .add("push:${monitor.name}")
                    )
                val response = statusPageClient.updateStatusPage(statusPage.id, updateDto)
                val statusPageInDb = statusPageRepository.findById(statusPage.id).shouldNotBeNull()

                then("it should ignore the wrongly-referenced monitor") {
                    response.monitors.shouldBeEmpty()
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

    @MockBean(StatusPageDataActions::class)
    fun mockStatusPageDataActions(): StatusPageDataActions = mockk()
}
