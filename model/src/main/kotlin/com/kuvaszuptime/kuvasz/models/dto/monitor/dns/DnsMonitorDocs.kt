package com.kuvaszuptime.kuvasz.models.dto.monitor.dns

object DnsMonitorDocs {
    const val HOST = "The name to query (e.g. example.com)"
    const val RESOLVER_HOST =
        "Optional custom nameserver host to query. If not set, the system resolver is used."
    const val RESOLVER_PORT = "The port of the custom nameserver (1-65535, defaults to 53)"
    const val TRANSPORT =
        "The DNS transport to use: UDP (default, with automatic TCP fallback on truncated responses) or TCP (forced)"
    const val RECORD_MATCHERS =
        "The DNS assertions to evaluate. Each matcher targets a record type with a match type (EXACT, CONTAINS or " +
            "REGEX, defaulting to CONTAINS) and a value. All matchers are ANDed; a matcher passes if any record of " +
            "its type satisfies it. When empty, the check falls back to a plain A lookup and is UP if the name " +
            "resolves to anything. REGEX values are Java/Kotlin (java.util.regex) patterns, compiled " +
            "case-insensitively and matched anywhere in the record, so anchor them with ^ and $ for a whole-record " +
            "match."
    const val EXPECTED_RESPONSE_CODE =
        "The DNS response code the check expects: NOERROR (default), NXDOMAIN, SERVFAIL or REFUSED. A non-NOERROR " +
            "value requires the record matchers to be empty."
    const val DRIFT_DETECTION_ENABLED =
        "Whether to emit a notification (without changing the UP/DOWN state) when the resolved record set changes " +
            "between checks"
    const val DRIFT_RECORD_TYPES =
        "The record types drift detection watches. When empty (the default), it watches exactly the types the record " +
            "matchers cover, so it costs no extra lookups. Naming types here replaces that default with the given " +
            "list, which is how a monitor watches something it does not assert on (an NS or MX change worth a " +
            "notification but not a DOWN event); each named type that no matcher covers adds a lookup per check, " +
            "which is not counted towards the latency reading. Ignored unless driftDetectionEnabled is true."
    const val TIMEOUT_MS = "The DNS query timeout in milliseconds (1-30000)"
    const val LATENCY_THRESHOLD_MS =
        "Optional resolution-latency threshold in milliseconds. If set, the check is considered DOWN when the " +
            "resolution takes longer than this value."
    const val METRICS_HISTORY_ENABLED = "Whether metrics history is enabled for the monitor"
    const val MONITORS_405_REASON =
        "DNS monitors are in read-only mode, because they are loaded from a YAML config file"
}
