package com.kuvaszuptime.kuvasz.util

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

// Merges the fields of a PATCH request into the existing entity. Unknown properties are ignored, so the entities can be
// updated by requests that contain more fields than they have
val entityUpdateMapper: ObjectMapper = jacksonMapperBuilder()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()
