package com.akobor.kuvasz.repositories

import com.akobor.kuvasz.tables.daos.MonitorDao
import org.jooq.Configuration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorRepository @Inject constructor(jooqConfig: Configuration) : MonitorDao(jooqConfig)
