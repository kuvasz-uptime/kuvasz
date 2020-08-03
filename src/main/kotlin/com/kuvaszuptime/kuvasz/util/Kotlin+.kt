package com.kuvaszuptime.kuvasz.util

import java.time.Duration

fun Int.toDurationOfSeconds(): Duration = Duration.ofSeconds(toLong())
