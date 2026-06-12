package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_STATUS_PAGE_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_STATUS_PAGES
import com.kuvaszuptime.kuvasz.mcp.schemas.StatusPageDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.StatusPageListSchema
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.testutils.shouldHaveError
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.spec.McpSchema

@MicronautTest
class StatusPageToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-status-pages tool") {

            `when`("list-status-pages is called with no status pages in the DB") {
                val response = callToolWithMcpClient(LIST_STATUS_PAGES)

                then("it should return an empty list in both structured and text content") {
                    response.isError shouldBe false

                    val pageList = response.structuredContentAs<StatusPageListSchema>().shouldNotBeNull()
                    pageList.statusPages.shouldBeEmpty()

                    response.contentAs<StatusPageListSchema>() shouldBe pageList
                }
            }

            `when`("list-status-pages is called with a status page in the DB") {
                val page = createStatusPage(
                    dslContext,
                    title = "My Status Page",
                    slug = "my-status-page",
                    public = true,
                )
                val response = callToolWithMcpClient(LIST_STATUS_PAGES)

                then("it should populate all StatusPageSchema fields correctly") {
                    response.isError shouldBe false

                    val pageList = response.structuredContentAs<StatusPageListSchema>().shouldNotBeNull()
                    pageList.statusPages shouldHaveSize 1
                    with(pageList.statusPages.first()) {
                        id shouldBe page.id
                        title shouldBe "My Status Page"
                        slug shouldBe "my-status-page"
                        this.public shouldBe true
                        monitorCount shouldBe 0
                        createdAt.shouldNotBeNull()
                        updatedAt.shouldNotBeNull()
                    }

                    response.contentAs<StatusPageListSchema>() shouldBe pageList
                }
            }

            `when`("list-status-pages returns correct monitorCount") {
                val monitor1 = createHttpMonitor(httpMonitorRepository)
                val monitor2 = createHttpMonitor(httpMonitorRepository)
                createStatusPage(
                    dslContext,
                    monitors = listOf(
                        MonitorID(MonitorType.HTTP_SSL, monitor1.name),
                        MonitorID(MonitorType.HTTP_SSL, monitor2.name),
                    )
                )
                val response = callToolWithMcpClient(LIST_STATUS_PAGES)

                then("it should return monitorCount matching the number of configured monitors") {
                    val pageList = response.structuredContentAs<StatusPageListSchema>().shouldNotBeNull()
                    pageList.statusPages.forOne { it.monitorCount shouldBe 2 }
                }
            }
        }

        given("the get-status-page-details tool") {

            `when`("get-status-page-details is called for a page with an HTTP monitor") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val page = createStatusPage(
                    dslContext,
                    title = "HTTP Page",
                    monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name))
                )
                val response = callToolWithMcpClient(GET_STATUS_PAGE_DETAILS, mapOf("statusPageId" to page.id))

                then("it should populate the schema for an HTTP monitor properly") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<StatusPageDetailsSchema>().shouldNotBeNull()
                    details.title shouldBe "HTTP Page"
                    details.systemStatus.shouldNotBeNull()
                    details.generatedAt.shouldNotBeNull()

                    val monitors = details.monitors.shouldNotBeEmpty()
                    val httpMonitor = monitors.first()
                    httpMonitor.name shouldBe monitor.name
                    httpMonitor.type shouldBe "http"
                    httpMonitor.uptimeStatusHistory.shouldNotBeEmpty()
                    httpMonitor.uptimeStatusHistory.first().date.shouldNotBeNull()
                    httpMonitor.lastHeartbeat shouldBe null
                    httpMonitor.lastPacketLossPercentage shouldBe null

                    response.contentAs<StatusPageDetailsSchema>() shouldBe details
                }
            }

            `when`("get-status-page-details is called for a page with an ICMP monitor") {
                val monitor = createIcmpMonitor(icmpMonitorRepository)
                val page = createStatusPage(
                    dslContext,
                    title = "ICMP Page",
                    monitors = listOf(MonitorID(MonitorType.ICMP, monitor.name))
                )
                val response = callToolWithMcpClient(GET_STATUS_PAGE_DETAILS, mapOf("statusPageId" to page.id))

                then("it should set type='icmp' and include ICMP-specific fields in StatusPageMonitorSchema") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<StatusPageDetailsSchema>().shouldNotBeNull()
                    val icmpMonitor = details.monitors.shouldNotBeEmpty().first()
                    icmpMonitor.name shouldBe monitor.name
                    icmpMonitor.type shouldBe "icmp"
                    icmpMonitor.uptimeStatusHistory.shouldNotBeEmpty()
                    icmpMonitor.lastHeartbeat shouldBe null
                }
            }

            `when`("get-status-page-details is called for a page with a push monitor") {
                val monitor = createPushMonitor(pushMonitorRepository)
                val page = createStatusPage(
                    dslContext,
                    title = "Push Page",
                    monitors = listOf(MonitorID(MonitorType.PUSH, monitor.name))
                )
                val response = callToolWithMcpClient(GET_STATUS_PAGE_DETAILS, mapOf("statusPageId" to page.id))

                then("it should set type='push' and include push-specific lastHeartbeat in StatusPageMonitorSchema") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<StatusPageDetailsSchema>().shouldNotBeNull()
                    val pushMonitor = details.monitors.shouldNotBeEmpty().first()
                    pushMonitor.name shouldBe monitor.name
                    pushMonitor.type shouldBe "push"
                    pushMonitor.uptimeStatusHistory.shouldNotBeEmpty()
                    pushMonitor.averageLatencyInMs shouldBe null
                    pushMonitor.lastPacketLossPercentage shouldBe null
                }
            }

            `when`("get-status-page-details is called for a page with a push monitor that has a lastHeartbeat") {
                val now = com.kuvaszuptime.kuvasz.util.getCurrentTimestamp()
                val monitor = createPushMonitor(pushMonitorRepository, lastHeartbeat = now)
                val page = createStatusPage(
                    dslContext,
                    monitors = listOf(MonitorID(MonitorType.PUSH, monitor.name))
                )
                val response = callToolWithMcpClient(GET_STATUS_PAGE_DETAILS, mapOf("statusPageId" to page.id))

                then("it should include the non-null lastHeartbeat in StatusPageMonitorSchema") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<StatusPageDetailsSchema>().shouldNotBeNull()
                    details.monitors.shouldNotBeEmpty().first().lastHeartbeat.shouldNotBeNull()
                }
            }

            `when`("get-status-page-details is called with a status page that has no monitors") {
                val page = createStatusPage(dslContext, title = "Empty Page", monitors = emptyList())
                val response = callToolWithMcpClient(GET_STATUS_PAGE_DETAILS, mapOf("statusPageId" to page.id))

                then("it should return details with an empty monitors list") {
                    response.isError shouldBe false

                    val details = response.structuredContentAs<StatusPageDetailsSchema>().shouldNotBeNull()
                    details.monitors.shouldBeEmpty()
                }
            }

            `when`("get-status-page-details is called with a non-existent ID") {
                val response = callTool(GET_STATUS_PAGE_DETAILS, mapOf("statusPageId" to -999L))

                then("it should return a resource-not-found protocol error with no result") {
                    response.shouldHaveError(McpSchema.ErrorCodes.RESOURCE_NOT_FOUND)
                }
            }
        }
    }
}
