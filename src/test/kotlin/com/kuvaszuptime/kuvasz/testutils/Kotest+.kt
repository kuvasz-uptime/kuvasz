package com.kuvaszuptime.kuvasz.testutils

import io.kotest.core.test.TestCase

fun TestCase.isLast(): Boolean = (description.name.prefix ?: "").startsWith("Then")
