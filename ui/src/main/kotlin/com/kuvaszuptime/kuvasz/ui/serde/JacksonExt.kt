package com.kuvaszuptime.kuvasz.ui.serde

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

internal val objectMapper = jacksonObjectMapper().registerModules(JavaTimeModule())
