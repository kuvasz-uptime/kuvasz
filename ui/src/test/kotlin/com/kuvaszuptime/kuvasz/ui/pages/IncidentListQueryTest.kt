package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration

private fun paramsOf(vararg params: Pair<String, String>): QueryParams =
    { name -> params.filter { it.first == name }.map { it.second } }

class IncidentListQueryTest : BehaviorSpec({

    given("the state of the incident list") {

        `when`("there are no parameters") {
            val query = incidentListQuery(paramsOf())

            then("it lists the incidents of the last 7 days, the resolved ones too, the latest started first") {
                query.filters.period.value shouldBe Duration.ofDays(7)
                query.filters.includeResolved.value shouldBe true
                query.filters.type.value.shouldBeNull()
                query.filters.monitorId.value.shouldBeNull()
                query.sorting shouldBe Sorting(IncidentSortField.STARTED_AT, SortOrder.DESC)
                query.url shouldBe "/incidents"
                query.activeFilterCount shouldBe 0
            }
        }

        `when`("the period is given in another form of the same length") {
            val query = incidentListQuery(paramsOf("period" to "P30D"))

            then("it's matched by its length, and put into the URL the way the list uses it") {
                query.filters.period.value shouldBe Duration.ofDays(30)
                query.url shouldBe "/incidents?period=PT720H"
            }
        }

        `when`("the period is not one of the offered ones") {
            val query = incidentListQuery(paramsOf("period" to "P2D"))

            then("it's kept, and offered among the others, in the order of their length") {
                query.filters.period.value shouldBe Duration.ofDays(2)
                query.url shouldBe "/incidents?period=PT48H"
                query.filters.period.options.map { it.value } shouldBe listOf(
                    Duration.ofHours(1),
                    Duration.ofHours(6),
                    Duration.ofHours(12),
                    Duration.ofDays(1),
                    Duration.ofDays(2),
                    Duration.ofDays(7),
                    Duration.ofDays(30),
                )
            }
        }

        `when`("the period is not a positive one, or not a period at all") {

            then("it's the default one, and only the usual periods are offered") {
                listOf("PT0S", "-P2D", "foo").forEach { period ->
                    val filter = incidentListQuery(paramsOf("period" to period)).filters.period
                    filter.value shouldBe Duration.ofDays(7)
                    filter.options.map { it.value } shouldBe UIDefaults.PERIOD_SELECTOR_OPTIONS
                }
            }
        }

        `when`("the type is given in any case") {
            val query = incidentListQuery(paramsOf("type" to "DOCKER"))

            then("it's put into the URL in lowercase, the way the URLs of the incidents have it") {
                query.filters.type.value shouldBe IncidentType.DOCKER
                query.url shouldBe "/incidents?type=docker"
            }
        }

        `when`("every filter is set") {
            val query = incidentListQuery(
                paramsOf(
                    "monitorId" to "5",
                    "includeResolved" to "false",
                    "period" to "PT1H",
                    "type" to "ssl",
                    "name" to "api",
                ),
            )

            then("they are put into the URL in the order they are shown") {
                query.url shouldBe "/incidents?name=api&type=ssl&period=PT1H&includeResolved=false&monitorId=5"
                query.activeFilterCount shouldBe 5
            }
        }
    }

    given("the state of the incident list on the details page of a monitor") {
        val query = monitorIncidentListQuery(
            paramsOf("page" to "2", "size" to "10", "type" to "push", "name" to "foo", "sortedBy" to "MONITOR_NAME"),
            IncidentType.HTTP,
            monitorId = 42,
        )

        `when`("it's parsed") {

            then("only its page and its size come from the parameters, the monitor and the type are its own") {
                query.pageRequest shouldBe PageRequest(2, 10)
                query.filters.type.value shouldBe IncidentType.HTTP
                query.filters.monitorId.value shouldBe 42
                query.filters.name.value.shouldBeNull()
                query.sorting shouldBe Sorting(IncidentSortField.STARTED_AT, SortOrder.DESC)
            }

            then("its links lead to the incidents page, filtered the same way") {
                query.url shouldBe "/incidents?type=http&monitorId=42&page=2&size=10"
                query.pageUrl(1) shouldBe "/incidents?type=http&monitorId=42&size=10"
            }
        }
    }
})
