package com.kuvaszuptime.kuvasz.testutils

import org.testcontainers.postgresql.PostgreSQLContainer

class TestDbContainer : PostgreSQLContainer("postgres:14-alpine") {
    companion object {
        private lateinit var instance: TestDbContainer

        fun start() {
            if (!Companion::instance.isInitialized) {
                instance = TestDbContainer()
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
