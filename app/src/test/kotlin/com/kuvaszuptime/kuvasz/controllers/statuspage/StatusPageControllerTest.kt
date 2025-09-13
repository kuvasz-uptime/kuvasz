package com.kuvaszuptime.kuvasz.controllers.statuspage

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageExportDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst

@MicronautTest(environments = ["full-integrations-setup"])
class StatusPageControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {

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

                val request =
                    HttpRequest.GET<Any>("/api/v2/status-pages/export/yaml").accept(MediaType.APPLICATION_YAML)

                then("it should export an empty status pages list in YAML format") {
                    val response = client.exchange(request).awaitFirst()
                    val responseBody = response.getBodyAs<ByteArray>()

                    response.status shouldBe HttpStatus.OK
                    val exportedPagesRaw = mapper.readTree(responseBody)["status-pages"].shouldNotBeNull()
                    mapper.convertValue<List<StatusPageExportDto>>(exportedPagesRaw).shouldBeEmpty()
                }
            }
        }
    }
}
