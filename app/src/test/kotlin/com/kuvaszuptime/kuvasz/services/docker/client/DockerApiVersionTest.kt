package com.kuvaszuptime.kuvasz.services.docker.client

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class DockerApiVersionTest : StringSpec({

    "a version is parsed from the form the daemon reports it in" {
        forAll(
            row("1.55", DockerApiVersion(1, 55)),
            row(" 1.40 ", DockerApiVersion(1, 40)),
            row("2.0", DockerApiVersion(2, 0)),
        ) { value, expected ->
            DockerApiVersion.parse(value) shouldBe expected
        }
    }

    "anything else is not a version" {
        forAll(row(null), row(""), row("1"), row("1.2.3"), row("v1.40"), row("latest")) { value ->
            DockerApiVersion.parse(value).shouldBeNull()
        }
    }

    // As text, 1.9 would sort after 1.40
    "versions are ordered by their numbers, not as text" {
        DockerApiVersion(1, 9) shouldBeLessThan DockerApiVersion(1, 40)
        DockerApiVersion(1, 55) shouldBeLessThan DockerApiVersion(2, 0)
    }

    "a version is rendered the way a path is prefixed with it" {
        DockerApiVersion(1, 44).pathPrefix shouldBe "/v1.44"
    }

    "the negotiated version is the daemon's own, kept between the oldest supported and the newest used" {
        forAll(
            row(DockerApiVersion(1, 55), DockerApiVersion.NEWEST_USED),
            row(DockerApiVersion(1, 44), DockerApiVersion(1, 44)),
            row(DockerApiVersion(1, 41), DockerApiVersion(1, 41)),
            row(DockerApiVersion(1, 40), DockerApiVersion(1, 40)),
            row(DockerApiVersion(1, 24), DockerApiVersion.OLDEST_SUPPORTED),
            row(null, DockerApiVersion.OLDEST_SUPPORTED),
        ) { daemonVersion, expected ->
            DockerApiVersion.negotiate(daemonVersion) shouldBe expected
        }
    }
})
