package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration

@MicronautTest
class IncidentControllerTest(
    incidentClient: IncidentClient,
    incidentRepository: IncidentRepository,
) : DatabaseBehaviorSpec({

    given("the getIncidents() endpoint") {

        `when`("it is called without explicit parameters") {
            val monitorId = 123L

            then("it should delegate to the IncidentRepository with default parameters") {
                val mockRepo = getMock(incidentRepository)
                every {
                    mockRepo.getIncidents(any(), any(), any())
                } returns emptyList()

                val result = incidentClient.getIncidents(monitorId, null, null)

                result.shouldBeEmpty()
                verify(exactly = 1) { mockRepo.getIncidents(monitorId, Duration.ofDays(7), true) }
            }
        }

        `when`("it is called with explicit parameters") {
            val monitorId = 123L

            then("it should delegate to the IncidentRepository with the provided parameters") {
                val mockRepo = getMock(incidentRepository)
                every {
                    mockRepo.getIncidents(any(), any(), any())
                } returns emptyList()

                val result = incidentClient.getIncidents(monitorId, Duration.ofMinutes(10), false)

                result.shouldBeEmpty()
                verify(exactly = 1) { mockRepo.getIncidents(monitorId, Duration.ofMinutes(10), false) }
            }
        }
    }
}) {
    @MockBean(IncidentRepository::class)
    fun mockIncidentRepository(): IncidentRepository = mockk()
}
