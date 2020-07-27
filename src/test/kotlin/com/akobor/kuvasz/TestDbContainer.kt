package com.akobor.kuvasz

import org.testcontainers.containers.PostgreSQLContainer

class TestDbContainer : PostgreSQLContainer<TestDbContainer>() {
    companion object {
        lateinit var instance: TestDbContainer

        fun start() {
            if (!::instance.isInitialized) {
                instance = TestDbContainer()
                instance.dockerImageName = "postgres:12"
                instance.start()

                System.setProperty("datasources.default.url", instance.jdbcUrl)
                System.setProperty("datasources.default.username", instance.username)
                System.setProperty("datasources.default.password", instance.password)
            }
        }

        fun stop() {
            instance.stop()
        }
    }
}
