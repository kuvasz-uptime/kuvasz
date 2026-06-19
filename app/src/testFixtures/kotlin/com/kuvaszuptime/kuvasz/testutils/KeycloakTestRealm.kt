package com.kuvaszuptime.kuvasz.testutils

import dasniko.testcontainers.keycloak.KeycloakContainer

/**
 * Shared definition of the Keycloak realm used by the OIDC end-to-end tests (raw-HTTP in `test`, browser-driven in
 * `uiTest`). The values mirror `src/testFixtures/resources/keycloak/kuvasz-realm.json`.
 */
object KeycloakTestRealm {

    const val IMAGE = "quay.io/keycloak/keycloak:26.6"
    const val REALM_IMPORT_FILE = "/keycloak/kuvasz-realm.json"

    const val REALM = "kuvasz"
    const val CLIENT_ID = "kuvasz"
    const val CLIENT_SECRET = "kuvasz-client-secret"

    // A user with a verified email that is on the allowlist
    const val USERNAME = "kuvasz-user"
    const val PASSWORD = "kuvasz-password"
    const val EMAIL = "kuvasz-user@example.com"

    // A user with a verified email that is NOT on the allowlist
    const val STRANGER_USERNAME = "stranger-user"
    const val STRANGER_PASSWORD = "stranger-password"

    // A user whose allow-listed email has NOT been verified
    const val UNVERIFIED_USERNAME = "unverified-user"
    const val UNVERIFIED_PASSWORD = "unverified-password"
    const val UNVERIFIED_EMAIL = "unverified@example.com"

    // Issuer URL for this realm given a running Keycloak's base auth-server URL
    fun issuerUrl(authServerUrl: String): String = "$authServerUrl/realms/$REALM"

    fun newContainer(): KeycloakContainer =
        KeycloakContainer(IMAGE).withRealmImportFile(REALM_IMPORT_FILE)
}
