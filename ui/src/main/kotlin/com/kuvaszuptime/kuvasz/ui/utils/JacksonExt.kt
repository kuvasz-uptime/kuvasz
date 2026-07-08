package com.kuvaszuptime.kuvasz.ui.utils

import tools.jackson.module.kotlin.jacksonObjectMapper

internal val objectMapper = jacksonObjectMapper()

fun <T : Any> T.asJsonString(): String = objectMapper.writeValueAsString(this)
