package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.tables.daos.MonitorDao
import org.jooq.Configuration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorRepository @Inject constructor(jooqConfig: Configuration) : MonitorDao(jooqConfig)
