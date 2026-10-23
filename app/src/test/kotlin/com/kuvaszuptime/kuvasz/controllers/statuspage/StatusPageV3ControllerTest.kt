package com.kuvaszuptime.kuvasz.controllers.statuspage

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.controllers.monitor.expectedIdsSortedBy
import com.kuvaszuptime.kuvasz.controllers.monitor.shouldTellTheSortFieldsApart
import com.kuvaszuptime.kuvasz.jooq.tables.StatusPage.STATUS_PAGE
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageSortField
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
class StatusPageV3ControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val v3Client: StatusPageV3Client,
) : DatabaseBehaviorSpec() {

    init {
        given("the v3 getStatusPagesPage() endpoint") {

            `when`("there are more of them than fit on a page") {
                // Every field puts these in a different order, see shouldTellTheSortFieldsApart below
                val now = getCurrentTimestamp()
                val seededIds = listOf(
                    seed(now, "Bravo", slug = "echo", public = true, createdMinutesAgo = 10, updatedMinutesAgo = 5),
                    seed(now, "alpha", slug = "delta", public = false, createdMinutesAgo = 40, updatedMinutesAgo = 5),
                    seed(now, "echo", slug = "bravo", public = true, createdMinutesAgo = 20, updatedMinutesAgo = 60),
                    seed(now, "Delta", slug = "alpha", public = false, createdMinutesAgo = 30, updatedMinutesAgo = 1),
                    seed(
                        now, "charlie", slug = "charlie", public = true, createdMinutesAgo = 50, updatedMinutesAgo = 30,
                    ),
                )

                val items = fetchPage().result
                val keys = mapOf<StatusPageSortField, (StatusPageDto) -> Comparable<*>?>(
                    StatusPageSortField.ID to { it.id },
                    StatusPageSortField.TITLE to { it.title.lowercase() },
                    StatusPageSortField.SLUG to { it.slug.lowercase() },
                    StatusPageSortField.PUBLIC to { it.public },
                    StatusPageSortField.CREATED_AT to { it.createdAt.toInstant() },
                    StatusPageSortField.UPDATED_AT to { it.updatedAt.toInstant() },
                )

                then("the expectations should be calculated from every seeded item") {
                    items.map { it.id } shouldContainExactlyInAnyOrder seededIds
                }

                then("every field should be covered by the test") {
                    keys.keys shouldBe StatusPageSortField.entries.toSet()
                }

                then("the seeded items should tell every field apart") {
                    items.shouldTellTheSortFieldsApart(keys) { it.id }
                }

                then("they should be sorted by title by default") {
                    items.map { it.id } shouldBe
                        items.expectedIdsSortedBy(SortOrder.ASC, { it.id }) { it.title.lowercase() }
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
                    fetchPage(name = "CHARLIE").result.map { it.title } shouldContainExactly listOf("charlie")
                    val flagged = fetchPage(public = true, size = 1)
                    flagged.totalCount shouldBe 3
                    flagged.result.single().public shouldBe true
                }
            }

            `when`("the parameters are invalid") {
                val invalidQueries = listOf("page=0", "size=0", "sortedBy=NOPE", "sortOrder=SIDEWAYS")

                then("it should return 400") {
                    invalidQueries.forEach { query ->
                        withClue(query) {
                            val exception = shouldThrow<HttpClientResponseException> {
                                client.exchange("/api/v3/status-pages?$query").awaitFirst()
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
        title: String,
        slug: String,
        public: Boolean,
        createdMinutesAgo: Long,
        updatedMinutesAgo: Long,
    ): Long {
        val statusPage = createStatusPage(dslContext, title = title, slug = slug, public = public)
        dslContext.update(STATUS_PAGE)
            .set(STATUS_PAGE.CREATED_AT, now.minusMinutes(createdMinutesAgo))
            .set(STATUS_PAGE.UPDATED_AT, now.minusMinutes(updatedMinutesAgo))
            .where(STATUS_PAGE.ID.eq(statusPage.id))
            .execute()
        return statusPage.id
    }

    private fun fetchPage(
        public: Boolean? = null,
        name: String? = null,
        page: Int = 1,
        size: Int = 100,
        sortedBy: StatusPageSortField? = null,
        sortOrder: SortOrder? = null,
    ) = v3Client.getStatusPagesPage(public, name, page, size, sortedBy, sortOrder)
}
