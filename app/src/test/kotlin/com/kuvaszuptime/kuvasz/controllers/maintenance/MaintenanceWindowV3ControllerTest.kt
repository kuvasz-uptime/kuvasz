package com.kuvaszuptime.kuvasz.controllers.maintenance

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.controllers.monitor.expectedIdsSortedBy
import com.kuvaszuptime.kuvasz.controllers.monitor.shouldTellTheSortFieldsApart
import com.kuvaszuptime.kuvasz.jooq.tables.MaintenanceWindow.MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import java.time.OffsetDateTime

@MicronautTest
class MaintenanceWindowV3ControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val v3Client: MaintenanceWindowV3Client,
) : DatabaseBehaviorSpec() {

    init {
        given("the v3 getMaintenanceWindowsPage() endpoint") {

            `when`("there are more of them than fit on a page") {
                // Every field puts these in a different order, see shouldTellTheSortFieldsApart below
                val now = getCurrentTimestamp()
                val seededIds = listOf(
                    seed(
                        now, "Bravo", enabled = true, global = false, showOnStatusPages = true,
                        startsInDays = null, createdMinutesAgo = 10, updatedMinutesAgo = 5,
                    ),
                    seed(
                        now, "alpha", enabled = false, global = false, showOnStatusPages = false,
                        startsInDays = 2, createdMinutesAgo = 40, updatedMinutesAgo = 5,
                    ),
                    seed(
                        now, "echo", enabled = true, global = true, showOnStatusPages = false,
                        startsInDays = 1, createdMinutesAgo = 20, updatedMinutesAgo = 60,
                    ),
                    seed(
                        now, "Delta", enabled = false, global = true, showOnStatusPages = true,
                        startsInDays = null, createdMinutesAgo = 30, updatedMinutesAgo = 1,
                    ),
                    seed(
                        now, "charlie", enabled = true, global = false, showOnStatusPages = false,
                        startsInDays = 3, createdMinutesAgo = 50, updatedMinutesAgo = 30,
                    ),
                )

                val items = fetchPage().result
                val keys = mapOf<MaintenanceWindowSortField, (MaintenanceWindowDetailsDto) -> Comparable<*>?>(
                    MaintenanceWindowSortField.ID to { it.id },
                    MaintenanceWindowSortField.NAME to { it.name.lowercase() },
                    MaintenanceWindowSortField.ENABLED to { it.enabled },
                    MaintenanceWindowSortField.GLOBAL to { it.global },
                    MaintenanceWindowSortField.SHOW_ON_STATUS_PAGES to { it.showOnStatusPages },
                    MaintenanceWindowSortField.START to { it.start?.toInstant() },
                    MaintenanceWindowSortField.CREATED_AT to { it.createdAt.toInstant() },
                    MaintenanceWindowSortField.UPDATED_AT to { it.updatedAt.toInstant() },
                )

                then("the expectations should be calculated from every seeded item") {
                    items.map { it.id } shouldContainExactlyInAnyOrder seededIds
                }

                then("every field should be covered by the test") {
                    keys.keys shouldBe MaintenanceWindowSortField.entries.toSet()
                }

                then("the seeded items should tell every field apart") {
                    items.shouldTellTheSortFieldsApart(keys) { it.id }
                }

                then("they should be sorted by name by default") {
                    items.map { it.id } shouldBe
                        items.expectedIdsSortedBy(SortOrder.ASC, { it.id }) { it.name.lowercase() }
                }

                then("they should be sorted in both orders, the ones without a value last, the same values by ID") {
                    keys.forEach { (field, key) ->
                        SortOrder.entries.forEach { order ->
                            withClue("$field $order") {
                                val page = fetchPage(sortedBy = field, sortOrder = order)

                                page.result.map { it.id } shouldBe
                                    items.expectedIdsSortedBy(order, { it.id }, key)
                            }
                        }
                    }
                }

                then("walking the pages should return the same order, without any item twice or missing") {
                    val walkedIds = (1..3).flatMap { fetchPage(page = it, size = 2).result.map { item -> item.id } }

                    walkedIds shouldBe items.map { it.id }
                    fetchPage(page = 3, size = 2).totalPages shouldBe 3
                }

                then("the filters should narrow the items and their totals") {
                    fetchPage(name = "CHARLIE").result.map { it.name } shouldContainExactly listOf("charlie")
                    val flagged = fetchPage(enabled = true, size = 1)
                    flagged.totalCount shouldBe 3
                    flagged.result.single().enabled shouldBe true
                }
            }

            `when`("the parameters are invalid") {
                val invalidQueries = listOf("page=0", "size=0", "sortedBy=NOPE", "sortOrder=SIDEWAYS")

                then("it should return 400") {
                    invalidQueries.forEach { query ->
                        withClue(query) {
                            val exception = shouldThrow<HttpClientResponseException> {
                                client.exchange("/api/v3/maintenance-windows?$query").awaitFirst()
                            }
                            exception.status shouldBe HttpStatus.BAD_REQUEST
                        }
                    }
                }
            }
        }
    }

    private fun seed(
        now: OffsetDateTime,
        name: String,
        enabled: Boolean,
        global: Boolean,
        showOnStatusPages: Boolean,
        startsInDays: Long?,
        createdMinutesAgo: Long,
        updatedMinutesAgo: Long,
    ): Long {
        val window = createMaintenanceWindow(
            dslContext,
            name = name,
            enabled = enabled,
            global = global,
            showOnStatusPages = showOnStatusPages,
            start = startsInDays?.let { now.plusDays(it) },
            duration = startsInDays?.let { "PT1H" },
        )
        dslContext.update(MAINTENANCE_WINDOW)
            .set(MAINTENANCE_WINDOW.CREATED_AT, now.minusMinutes(createdMinutesAgo))
            .set(MAINTENANCE_WINDOW.UPDATED_AT, now.minusMinutes(updatedMinutesAgo))
            .where(MAINTENANCE_WINDOW.ID.eq(window.id))
            .execute()
        return window.id
    }

    private fun fetchPage(
        enabled: Boolean? = null,
        name: String? = null,
        page: Int = 1,
        size: Int = 100,
        sortedBy: MaintenanceWindowSortField? = null,
        sortOrder: SortOrder? = null,
    ) = v3Client.getMaintenanceWindowsPage(enabled, name, page, size, sortedBy, sortOrder)
}
