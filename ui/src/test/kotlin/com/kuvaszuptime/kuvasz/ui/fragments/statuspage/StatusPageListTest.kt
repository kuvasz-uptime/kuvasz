package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageSortField
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.ui.components.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

private fun paramsOf(vararg params: Pair<String, String>): QueryParams =
    { name -> params.filter { it.first == name }.map { it.second } }

class StatusPageListTest : BehaviorSpec({

    given("the state of the status page list") {

        `when`("there are no parameters") {
            val query = statusPageListQuery(paramsOf())

            then("it's sorted by title") {
                query.sorting shouldBe Sorting(StatusPageSortField.TITLE, SortOrder.ASC)
                query.url shouldBe "/status-pages"
            }
        }

        `when`("it's filtered by its title and visibility") {
            val query = statusPageListQuery(paramsOf("public" to "true", "name" to "acme"))

            then("the filters are put into the URL by the names of the v3 API") {
                query.filters.name.value shouldBe "acme"
                query.filters.public.value shouldBe true
                query.url shouldBe "/status-pages?name=acme&public=true"
            }
        }
    }

    given("the default status page in the status page list") {
        val defaultPage = AppGlobals.DefaultStatusPageSettings(title = "Acme Status", public = true)

        `when`("the list is on its first page without filters") {

            then("it's listed") {
                statusPageListQuery(paramsOf()).listsDefaultStatusPage(defaultPage) shouldBe true
            }
        }

        `when`("the list is on another page") {

            then("it's not listed, as it's pinned to the first page") {
                statusPageListQuery(paramsOf("page" to "2")).listsDefaultStatusPage(defaultPage) shouldBe false
            }
        }

        `when`("the list is searched by title") {

            then("it's listed only if its title contains the text, regardless of its case") {
                statusPageListQuery(paramsOf("name" to "acme")).listsDefaultStatusPage(defaultPage) shouldBe true
                statusPageListQuery(paramsOf("name" to "other")).listsDefaultStatusPage(defaultPage) shouldBe false
            }
        }

        `when`("the list is filtered by visibility") {

            then("it's listed only if it's visible the same way") {
                statusPageListQuery(paramsOf("public" to "true")).listsDefaultStatusPage(defaultPage) shouldBe true
                statusPageListQuery(paramsOf("public" to "false")).listsDefaultStatusPage(defaultPage) shouldBe false
            }
        }
    }
})
