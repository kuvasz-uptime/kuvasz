package com.kuvaszuptime.kuvasz.models.checks

import java.net.URI

sealed class HttpCheckResult {
    data object Continue : HttpCheckResult()
    data object Finished : HttpCheckResult()
    data class Redirected(
        val redirectionUri: URI,
        val visitedUrls: MutableList<URI>,
    ) : HttpCheckResult()
}
