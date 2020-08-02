package com.akobor.kuvasz.util

import arrow.core.Either
import kotlinx.coroutines.runBlocking

fun <R> Either.Companion.catchBlocking(f: () -> R): Either<Throwable, R> = runBlocking {
    catch { f() }
}
