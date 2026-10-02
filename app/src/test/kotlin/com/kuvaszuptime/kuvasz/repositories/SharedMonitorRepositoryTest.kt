package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class SharedMonitorRepositoryTest(
    private val sharedMonitorRepository: SharedMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("the findById() method") {

            `when`("it is called with the ID of a Docker monitor") {
                val monitor = createDockerMonitor(dockerMonitorRepository)

                then("it should return the record from the Docker table") {
                    val found: DockerMonitorRecord? =
                        sharedMonitorRepository.findById(NumericMonitorID(MonitorType.DOCKER, monitor.id))

                    found?.id shouldBe monitor.id
                    found?.name shouldBe monitor.name
                    found?.dockerHost shouldBe monitor.dockerHost
                    found?.container shouldBe monitor.container
                }
            }

            `when`("it is called with a Docker ID that does not exist") {
                then("it should return null") {
                    val found: DockerMonitorRecord? =
                        sharedMonitorRepository.findById(NumericMonitorID(MonitorType.DOCKER, -1L))

                    found shouldBe null
                }
            }
        }
    }
}
