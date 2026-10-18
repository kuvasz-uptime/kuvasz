package com.kuvaszuptime.kuvasz.ui.fragments.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private fun paramsOf(vararg params: Pair<String, String>): QueryParams =
    { name -> params.filter { it.first == name }.map { it.second } }

class MonitorListFiltersTest : BehaviorSpec({

    given("the category filter of a monitor list") {

        `when`("there is no category parameter") {
            val category = MonitorListFilters(paramsOf(), listOf("Search", "payments")).category

            then("nothing is selected, and the categories are offered by name, followed by the uncategorized option") {
                category.value.shouldBeNull()
                category.options.map { it.queryValue } shouldContainExactly listOf("payments", "Search", "")
                category.options.last().value shouldBe CategoryFilter.Uncategorized
            }
        }

        `when`("the parameter is empty or blank") {

            then("the monitors without a category are selected") {
                listOf("", "  ").forEach { raw ->
                    MonitorListFilters(paramsOf("category" to raw), emptyList()).category.value shouldBe
                        CategoryFilter.Uncategorized
                }
            }
        }

        `when`("the parameter is a category") {
            val category = MonitorListFilters(paramsOf("category" to " Payments "), listOf("Payments")).category

            then("the category is selected, trimmed") {
                category.value shouldBe CategoryFilter.InCategory("Payments")
                category.queryValues shouldContainExactly listOf("Payments")
            }
        }

        `when`("the category is not among the available ones") {
            val category = MonitorListFilters(paramsOf("category" to "Gone"), listOf("Search")).category

            then("it's still offered and selected, so a link to it keeps working") {
                category.value shouldBe CategoryFilter.InCategory("Gone")
                category.options.map { it.queryValue } shouldContainExactly listOf("Gone", "Search", "")
            }
        }
    }

    given("the filters of a monitor list") {
        val filters = MonitorListFilters(
            paramsOf("name" to "api", "enabled" to "true", "uptimeStatus" to "DOWN"),
            emptyList(),
        )

        `when`("they are parsed") {

            then("their values are the typed ones of the query parameters") {
                filters.name.value shouldBe "api"
                filters.enabled.value shouldBe true
                filters.uptimeStatus.values shouldContainExactly listOf(UptimeStatus.DOWN)
            }

            then("they are listed in the order they are shown, by the names of the v3 API parameters") {
                filters.all.map { it.param } shouldContainExactly listOf("name", "category", "enabled", "uptimeStatus")
            }
        }
    }

    given("the filters of the HTTP monitor list") {
        val filters = HttpMonitorListFilters(
            paramsOf("sslStatus" to "WILL_EXPIRE", "sslStatus" to "INVALID", "sslCheckEnabled" to "false"),
            emptyList(),
        )

        `when`("they are parsed") {

            then("the SSL filters are parsed too") {
                filters.sslStatus.values shouldContainExactly listOf(SslStatus.INVALID, SslStatus.WILL_EXPIRE)
                filters.sslCheckEnabled.value shouldBe false
            }

            then("they come after the ones of every monitor list") {
                filters.all.map { it.param } shouldContainExactly
                    listOf("name", "category", "enabled", "uptimeStatus", "sslStatus", "sslCheckEnabled")
            }
        }
    }

    given("the state of a monitor list") {

        `when`("there are no parameters") {
            val query = httpMonitorListQuery(paramsOf())

            then("it's sorted by name and links to the plain list page") {
                query.sorting shouldBe Sorting(HttpMonitorSortField.NAME, SortOrder.ASC)
                query.url shouldBe "/http-monitors"
            }
        }

        `when`("it's filtered by the monitors without a category") {
            val query = httpMonitorListQuery(paramsOf("category" to ""))

            then("the empty parameter is kept in the URL, since it's not the same as no parameter at all") {
                query.url shouldBe "/http-monitors?category="
                query.activeFilterCount shouldBe 1
            }
        }
    }
})
