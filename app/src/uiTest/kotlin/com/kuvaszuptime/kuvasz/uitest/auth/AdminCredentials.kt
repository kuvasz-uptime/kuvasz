package com.kuvaszuptime.kuvasz.uitest.auth

/**
 * The admin credentials configured in `application-ui-test.yml`. Kept in one place so the login page object, the
 * `storageState` bootstrap and the login spec all agree on them.
 */
object AdminCredentials {
    const val USERNAME = "e2e-admin"
    const val PASSWORD = "e2e-admin-password-123"
}
