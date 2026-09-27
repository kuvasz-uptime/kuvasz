package com.kuvaszuptime.kuvasz.services.docker.client

/**
 * An Engine API version, as the daemon reports it in the `Api-Version` header of `/_ping` and as it prefixes a path.
 */
internal data class DockerApiVersion(val major: Int, val minor: Int) : Comparable<DockerApiVersion> {

    override fun compareTo(other: DockerApiVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor })

    override fun toString(): String = "$major.$minor"

    val pathPrefix: String get() = "/v$this"

    companion object {

        // Engine 19.03, the oldest version Docker still supports, and the floor: a daemon that cannot serve it rejects
        // the call with an explanation of its own, which is surfaced as is.
        val OLDEST_SUPPORTED = DockerApiVersion(1, 40)

        // Engine 25.0. No single version is accepted by every supported daemon, since Engine 29.0 to 29.2 refuse
        // anything older than this, while Engine 24.0 and older know nothing newer. Every field read by the client is
        // the same across the whole range, so going higher would only risk a response format that changed since.
        val NEWEST_USED = DockerApiVersion(1, 44)

        // What a daemon that does not tell its version is tried with, in this order, as between the two of them
        // every supported daemon accepts one
        val FALLBACKS = listOf(OLDEST_SUPPORTED, NEWEST_USED)

        private val VERSION_PATTERN = Regex("""(\d+)\.(\d+)""")

        fun parse(value: String?): DockerApiVersion? =
            value?.trim()?.let(VERSION_PATTERN::matchEntire)?.destructured?.let { (major, minor) ->
                DockerApiVersion(major.toInt(), minor.toInt())
            }

        /**
         * The highest version both sides speak, the way Docker's own client negotiates.
         */
        fun negotiate(daemonVersion: DockerApiVersion): DockerApiVersion =
            daemonVersion.coerceIn(OLDEST_SUPPORTED, NEWEST_USED)
    }
}
