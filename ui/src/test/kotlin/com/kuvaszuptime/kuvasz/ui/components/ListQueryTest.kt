package com.kuvaszuptime.kuvasz.ui.components

import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private enum class TestSortField { NAME, STARTED_AT }

private enum class TestStatus { UP, DOWN }

private class TestFilters(params: QueryParams) : ListFilters {
    val name = textFilter(params, "name", label = "Name", placeholder = "Search")
    val enabled = yesNoFilter(params, "enabled", label = "Enabled")
    val status = multiChoiceFilter(params, "status", label = "Status", enumOptions(TestStatus.entries) { it.name })
    override val all: List<ListFilter> = listOf(name, enabled, status)
}

private class OtherFilters(params: QueryParams) : ListFilters {
    val id = numberFilter(params, "id", label = "ID")
    val resolved = yesNoOptions().let { options ->
        defaultedChoiceFilter(params, "resolved", label = "Resolved", options, default = options.first())
    }
    override val all: List<ListFilter> = listOf(id, resolved)
}

private fun otherQueryOf(vararg params: Pair<String, String>): ListQuery<TestSortField, OtherFilters> {
    val queryParams = paramsOf(*params)
    return ListQuery.parse(
        params = queryParams,
        path = "/others",
        filters = OtherFilters(queryParams),
        sortFields = TestSortField.entries,
        defaultSorting = Sorting(TestSortField.NAME, SortOrder.ASC),
    )
}

private fun paramsOf(vararg params: Pair<String, String>): QueryParams =
    { name -> params.filter { it.first == name }.map { it.second } }

private fun queryOf(
    vararg params: Pair<String, String>,
    defaultSorting: Sorting<TestSortField> = Sorting(TestSortField.NAME, SortOrder.ASC),
): ListQuery<TestSortField, TestFilters> {
    val queryParams = paramsOf(*params)
    return ListQuery.parse(
        params = queryParams,
        path = "/things",
        filters = TestFilters(queryParams),
        sortFields = TestSortField.entries,
        defaultSorting = defaultSorting,
    )
}

class ListQueryTest : BehaviorSpec({

    given("the query parameters of a list") {

        `when`("there are none") {
            val query = queryOf()

            then("the list is on its first page of the default size, sorted the default way, without filters") {
                query.sorting shouldBe Sorting(TestSortField.NAME, SortOrder.ASC)
                query.pageRequest shouldBe PageRequest(PageRequest.DEFAULT_PAGE, PageRequest.DEFAULT_SIZE)
                query.activeFilterCount shouldBe 0
                query.url shouldBe "/things"
            }
        }

        `when`("the page or the page size is out of range, or not a number") {

            then("the default is used instead") {
                listOf("0", "-1", "abc", "").forEach { page ->
                    queryOf("page" to page).pageRequest.page shouldBe PageRequest.DEFAULT_PAGE
                }
                // Only the sizes offered by the list are accepted
                listOf("0", "7", "1000", "abc").forEach { size ->
                    queryOf("size" to size).pageRequest.size shouldBe PageRequest.DEFAULT_SIZE
                }
            }
        }

        `when`("the page and the page size are valid") {

            then("they are used") {
                queryOf("page" to "3", "size" to "50").pageRequest shouldBe PageRequest(3, 50)
            }
        }

        `when`("the sorting is given") {

            then("the field is sorted ascending, unless the order is given too") {
                queryOf("sortedBy" to "STARTED_AT").sorting shouldBe Sorting(TestSortField.STARTED_AT, SortOrder.ASC)
                queryOf("sortedBy" to "STARTED_AT", "sortOrder" to "DESC").sorting shouldBe
                    Sorting(TestSortField.STARTED_AT, SortOrder.DESC)
            }

            then("only the order sorts the default field in that order") {
                queryOf("sortOrder" to "DESC").sorting shouldBe Sorting(TestSortField.NAME, SortOrder.DESC)
            }

            then("the case of the values doesn't matter") {
                queryOf("sortedBy" to "started_at", "sortOrder" to "desc").sorting shouldBe
                    Sorting(TestSortField.STARTED_AT, SortOrder.DESC)
            }

            then("an unknown field or order is ignored") {
                queryOf("sortedBy" to "FOO").sorting shouldBe Sorting(TestSortField.NAME, SortOrder.ASC)
                queryOf("sortedBy" to "STARTED_AT", "sortOrder" to "FOO").sorting shouldBe
                    Sorting(TestSortField.STARTED_AT, SortOrder.ASC)
            }
        }
    }

    given("the filters of a list") {

        `when`("a text filter is blank") {

            then("it's not set, otherwise it's trimmed") {
                queryOf("name" to "   ").filters.name.value.shouldBeNull()
                queryOf("name" to " foo ").filters.name.value shouldBe "foo"
            }
        }

        `when`("a choice filter has an unknown value") {

            then("it's not set") {
                queryOf("enabled" to "maybe").filters.enabled.value.shouldBeNull()
                queryOf("enabled" to "false").filters.enabled.value shouldBe false
            }
        }

        `when`("a choice filter has a known value in another case") {
            val query = queryOf("enabled" to "TRUE", "status" to "down")

            then("it's set, and put into the URL the way the option is") {
                query.filters.enabled.value shouldBe true
                query.filters.status.values shouldContainExactly listOf(TestStatus.DOWN)
                query.url shouldBe "/things?enabled=true&status=DOWN"
            }
        }

        `when`("a number filter is not a positive whole number") {

            then("it's not set") {
                listOf("0", "-1", "1.5", "abc", "").forEach { id ->
                    otherQueryOf("id" to id).filters.id.value.shouldBeNull()
                }
            }
        }

        `when`("a number filter is a positive whole number") {
            val query = otherQueryOf("id" to " 42 ")

            then("it's set") {
                query.filters.id.value shouldBe 42
                query.url shouldBe "/others?id=42"
                query.activeFilterCount shouldBe 1
            }
        }

        `when`("a filter with a default value has no value, or an unknown one") {

            then("it has the default value, which is left out of the URL") {
                listOf(otherQueryOf(), otherQueryOf("resolved" to "maybe"), otherQueryOf("resolved" to "true"))
                    .forEach { query ->
                        query.filters.resolved.value shouldBe true
                        query.url shouldBe "/others"
                        query.activeFilterCount shouldBe 0
                    }
            }
        }

        `when`("a filter with a default value has another value") {
            val query = otherQueryOf("resolved" to "False")

            then("it's set and put into the URL") {
                query.filters.resolved.value shouldBe false
                query.url shouldBe "/others?resolved=false"
                query.activeFilterCount shouldBe 1
            }
        }

        `when`("a multi choice filter has several values") {
            val status = queryOf("status" to "DOWN", "status" to "FOO", "status" to "UP", "status" to "DOWN")
                .filters.status

            then("the known ones are kept once, in the order of the options") {
                status.values shouldContainExactly listOf(TestStatus.UP, TestStatus.DOWN)
            }
        }

        `when`("some of them are set") {
            val query = queryOf("name" to "foo", "status" to "UP", "enabled" to "")

            then("they are counted as active") {
                query.activeFilterCount shouldBe 2
            }
        }
    }

    given("the URL of a list") {

        `when`("the parameters have the default values") {
            val query = queryOf("sortedBy" to "NAME", "sortOrder" to "ASC", "page" to "1", "size" to "25")

            then("they are left out") {
                query.url shouldBe "/things"
            }
        }

        `when`("the parameters have other values") {
            val query = queryOf(
                "size" to "10",
                "page" to "2",
                "status" to "DOWN",
                "status" to "UP",
                "name" to "a & b",
                "sortedBy" to "STARTED_AT",
                "sortOrder" to "DESC",
            )

            then("they are listed in the order of the filters, with the repeated and escaped values") {
                query.url shouldBe
                    "/things?name=a+%26+b&status=UP&status=DOWN&sortedBy=STARTED_AT&sortOrder=DESC&page=2&size=10"
            }

            then("the requests of the list itself have the same parameters") {
                query.fragmentUrl("/things/fragments/list") shouldBe
                    "/things/fragments/list?name=a+%26+b&status=UP&status=DOWN&sortedBy=STARTED_AT&sortOrder=DESC" +
                    "&page=2&size=10"
            }
        }

        `when`("an ascending field is not the default") {

            then("the order is left out, as a field is sorted ascending without it") {
                queryOf("sortedBy" to "STARTED_AT", "sortOrder" to "ASC").url shouldBe "/things?sortedBy=STARTED_AT"
            }
        }

        `when`("the default sorting is descending") {
            val defaultSorting = Sorting(TestSortField.STARTED_AT, SortOrder.DESC)

            then("its field sorted ascending is still put into the URL") {
                queryOf(defaultSorting = defaultSorting).url shouldBe "/things"
                queryOf("sortedBy" to "STARTED_AT", defaultSorting = defaultSorting).url shouldBe
                    "/things?sortedBy=STARTED_AT"
            }
        }

        `when`("it's on another page") {
            val query = queryOf("name" to "foo", "page" to "4")

            then("the state is the same, except the page") {
                query.withPage(2).url shouldBe "/things?name=foo&page=2"
                query.withPage(1).url shouldBe "/things?name=foo"
            }
        }
    }

    given("the links of a list") {

        `when`("the list is sorted by a field") {
            val query = queryOf("name" to "foo", "page" to "3", "size" to "10")

            then("its header links to the opposite order, on the first page, keeping the filters and the size") {
                query.sortedByUrl(TestSortField.NAME) shouldBe "/things?name=foo&sortedBy=NAME&sortOrder=DESC&size=10"
            }

            then("the header of another field links to it, sorted ascending") {
                query.sortedByUrl(TestSortField.STARTED_AT) shouldBe "/things?name=foo&sortedBy=STARTED_AT&size=10"
            }
        }

        `when`("the list is sorted descending") {
            val query = queryOf("sortedBy" to "NAME", "sortOrder" to "DESC")

            then("its header links back to the ascending order, which is the default here") {
                query.sortedByUrl(TestSortField.NAME) shouldBe "/things"
            }
        }

        `when`("the pages are linked") {
            val query = queryOf("sortedBy" to "STARTED_AT", "page" to "2")

            then("they keep the sorting") {
                query.pageUrl(3) shouldBe "/things?sortedBy=STARTED_AT&page=3"
                query.pageUrl(1) shouldBe "/things?sortedBy=STARTED_AT"
            }
        }
    }

    given("the state of the Alpine.js component of a list") {

        `when`("the list has no parameters") {

            then("the filters are empty, and so are the parameters of the navigation") {
                queryOf().alpineState() shouldBe
                    """{"filters":{"name":"","enabled":null,"status":[]},""" +
                    """"emptyFilters":{"name":"","enabled":null,"status":[]},""" +
                    """"navigation":{"page":null,"size":null,"sortedBy":null,"sortOrder":null}}"""
            }
        }

        `when`("the list has parameters") {
            val query = queryOf(
                "name" to "foo",
                "enabled" to "true",
                "status" to "DOWN",
                "sortedBy" to "STARTED_AT",
                "page" to "2",
            )

            then("they are the values of the URL") {
                query.alpineState() shouldBe
                    """{"filters":{"name":"foo","enabled":"true","status":["DOWN"]},""" +
                    """"emptyFilters":{"name":"","enabled":null,"status":[]},""" +
                    """"navigation":{"page":"2","size":null,"sortedBy":"STARTED_AT","sortOrder":null}}"""
            }
        }
    }

    given("the state of the Alpine.js component of a list with a number and a default filter") {

        `when`("the filters have their default values") {

            then("they are empty") {
                otherQueryOf().alpineState() shouldBe
                    """{"filters":{"id":"","resolved":null},"emptyFilters":{"id":"","resolved":null},""" +
                    """"navigation":{"page":null,"size":null,"sortedBy":null,"sortOrder":null}}"""
            }
        }

        `when`("the filters have other values") {

            then("they are the values of the URL") {
                otherQueryOf("id" to "7", "resolved" to "false").alpineState() shouldBe
                    """{"filters":{"id":"7","resolved":"false"},"emptyFilters":{"id":"","resolved":null},""" +
                    """"navigation":{"page":null,"size":null,"sortedBy":null,"sortOrder":null}}"""
            }
        }
    }

    given("the pages shown in the pagination of a list") {

        `when`("there are only a few pages") {

            then("every one of them is shown") {
                paginationWindow(page = 1, totalPages = 1) shouldContainExactly listOf(1)
                paginationWindow(page = 2, totalPages = 3) shouldContainExactly listOf(1, 2, 3)
                paginationWindow(page = 1, totalPages = 4) shouldContainExactly listOf(1, 2, 3, 4)
            }
        }

        `when`("there are many pages") {

            then("the first, the last and the neighbours of the current one are shown, with gaps between them") {
                paginationWindow(page = 1, totalPages = 10) shouldContainExactly listOf(1, 2, null, 10)
                paginationWindow(page = 5, totalPages = 10) shouldContainExactly listOf(1, null, 4, 5, 6, null, 10)
                paginationWindow(page = 10, totalPages = 10) shouldContainExactly listOf(1, null, 9, 10)
            }

            then("a gap of a single page shows the page instead") {
                paginationWindow(page = 4, totalPages = 10) shouldContainExactly listOf(1, 2, 3, 4, 5, null, 10)
                paginationWindow(page = 7, totalPages = 10) shouldContainExactly listOf(1, null, 6, 7, 8, 9, 10)
            }
        }

        `when`("there are no pages") {

            then("nothing is shown") {
                paginationWindow(page = 1, totalPages = 0).shouldBeEmpty()
            }
        }
    }
})
