_Kuvasz_ can be configured in two ways: via a **_YAML_ configuration file, or via environment variables**. Most of the configuration options are available in both formats, but some are only available in one of them.

!!! info

    The _YAML_ configuration file **should be mounted as a volume** in the _Docker_ container under `/config/kuvasz.yml`.

    === "Docker Compose"

        ```yaml
        services:
          kuvasz:
            # ...
            volumes:
              - /path/to/your/kuvasz.yml:/config/kuvasz.yml
        ```

!!! tip

    If you modify your configuration (via _YAML_ or _ENV_, it doesn't matter), you need to restart the _Kuvasz_ container for the changes to take effect.

## Authentication

### Toggling authentication

<!-- md:version 2.0.0 -->
<!-- md:default `true` -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    micronaut.security.enabled: true
    ```

=== "ENV"

    ```bash
    ENABLE_AUTH=true
    ```

If it's set to `false`, the authentication will be completely disabled, and you can access the web UI or the API without any credentials.

!!! warning "Disabling authentication"
    If you disable authentication, **anyone can access your Kuvasz instance** (assuming that it's exposed), so make sure to secure it by other means, like a firewall or a reverse proxy with authentication.

    If you're running it locally, or on your private network, it might be fine, but be aware of the risks.

### Authentication max age

<!-- md:version 2.0.0 -->
<!-- md:default `86400` -->
<!-- md:type `number` -->

=== "YAML"

    ```yaml
    micronaut.security.token.generator.access-token.expiration: 86400
    ```

=== "ENV"

    ```bash
    AUTH_MAX_AGE=86400
    ```

The **maximum age of the authentication token** in seconds, default is 24 hours (86400 seconds). After this time, the admin user will need to log in again on the web UI.

### Credentials

#### Admin username

<!-- md:required_if auth enabled -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    admin-auth.username: YourSuperSecretUsername
    ```

=== "ENV"

    ```bash
    ADMIN_USER=YourSuperSecretUsername
    ```

The username for the admin user.

#### Admin password

<!-- md:required_if auth enabled -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    admin-auth.password: YourSuperSecretPassword 
    ```

=== "ENV"

    ```bash
    ADMIN_PASSWORD=YourSuperSecretPassword
    ```

The password for the admin user, **minimum 12 characters long, can't be the same as the username**.


#### API key

<!-- md:version 2.0.0 -->
<!-- md:required_if auth enabled -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    admin-auth.api-key: ThisShouldBeVeryVerySecureToo
    ```

=== "ENV"

    ```bash
    ADMIN_API_KEY=ThisShouldBeVeryVerySecureToo
    ```

The API key for the [REST API](../features/api.md), minimum 16 characters long.

## Database

_Kuvasz_ uses a _PostgreSQL_ database to store its data. You can use an existing _PostgreSQL_ instance, and point _Kuvasz_ to a separate database in it, the only thing you should watch out for is that **the database user has the necessary permissions** to create schemas and tables.

The database connection can be configured **only via environment variables**.

!!! info

    The **minimum, tested version of _PostgreSQL_ is 12**, `alpine` distributions are supported.

### Host

<!-- md:default localhost -->
<!-- md:type `string` -->

=== "ENV"

    ```bash
    DATABASE_HOST=localhost
    ```

A hostname or IP address of the database server.

### Port

<!-- md:default 5432 -->
<!-- md:type `number` -->

=== "ENV"

    ```bash
    DATABASE_PORT=5432
    ```

### Database name

<!-- md:default postgres -->
<!-- md:type `string` -->

=== "ENV"

    ```bash
    DATABASE_NAME=postgres
    ```

The name of the database to connect to. _Kuvasz_ will create a schema called `kuvasz` in the database, and will use it to store all the data it needs.

### User

<!-- md:flag required -->
<!-- md:type `string` -->

=== "ENV"

    ```bash
    DATABASE_USER=change_me
    ```

The username to connect to the database. The user **must have the necessary permissions** to create schemas and tables.

### Password

<!-- md:flag required -->
<!-- md:type `string` -->

=== "ENV"

    ```bash
    DATABASE_PASSWORD=change_me
    ```

The password of the user above.

## SMTP

!!! tip

    Using an SMTP server is **optional**, it's only needed if you want to use the [**email integration**](../features/notifications.md#email) to send notifications about events.

    The SMTP configuration can be set **only via _YAML_**.

    Be aware that if you include `smtp-config` in your configuration, then you'll **need to provide all the required** properties of it, otherwise _Kuvasz_ won't start.

### Host

<!-- md:version 2.0.0 -->
<!-- md:required_if `smtp-config` is present -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    smtp-config.host: 'your.smtp.server'
    ```

The **hostname or IP address** of the SMTP server you want to use for sending emails.

### Port

<!-- md:version 2.0.0 -->
<!-- md:required_if `smtp-config` is present -->
<!-- md:type `number` -->

=== "YAML"

    ```yaml
    smtp-config.port: 465
    ```

The **port** of the SMTP server you want to use for sending emails.

### Username

<!-- md:version 2.0.0 -->
<!-- md:default `null` -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    smtp-config.username: YourSMTPUsername
    ```

The **username** to authenticate with the SMTP server. If your SMTP server doesn't require authentication, you can omit specifying this property, or set it explicitly to `null`.

### Password

<!-- md:version 2.0.0 -->
<!-- md:default `null` -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    smtp-config.password: YourSMTPPassword
    ```

The **password** to authenticate with the SMTP server. If your SMTP server doesn't require authentication, you can omit specifying this property, or set it explicitly to `null`.

### Transport strategy

<!-- md:version 2.0.0 -->
<!-- md:default `SMTP_TLS` -->
<!-- md:type enum: `SMTP_TLS`, `SMTPS`, `SMTP` -->

=== "YAML"

    ```yaml
    smtp-config.transport-strategy: SMTP_TLS
    ```

The **transport strategy** to use for sending emails. The default is `SMTP_TLS`, which uses TLS encryption for the connection. You can also use `SMTPS` (implicit SSL) or `SMTP` (no encryption).

## Miscellaneous

### Timezone

<!-- md:default UTC -->
<!-- md:type `string` -->

=== "ENV"

    ```bash
    TZ=UTC
    ```

The timezone to use for the application. It's recommended to set it to your **local timezone**.

### Event data retention

<!-- md:version 2.0.0 -->
<!-- md:default 365 -->
<!-- md:type `number` -->

=== "YAML"

    ```yaml
    app-config.event-data-retention-days: 365
    ```

=== "ENV"

    ```bash
    EVENT_DATA_RETENTION_DAYS=365
    ```

The number of days to keep the finished events in the database. After this time, finished uptime & SSL **events will be cleaned up** to spare space in the database. The **minimum is 1 day**.

### Latency data retention

<!-- md:version 2.0.0 -->
<!-- md:default 7 -->
<!-- md:type `number` -->

=== "YAML"

    ```yaml
    app-config.latency-data-retention-days: 7
    ```

=== "ENV"

    ```bash
    LATENCY_DATA_RETENTION_DAYS=7
    ```

The number of days to keep the latency data in the database. After this time, **latency data will be cleaned up** to spare space in the database. The **minimum is 1 day**.

### Event logging

<!-- md:default true -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    app-config.log-event-handler: true
    ```

=== "ENV"

    ```bash
    ENABLE_LOG_EVENT_HANDLER=true
    ```

Toggles the log based event handler, which **writes the uptime & SSL events** coming from monitors (i.e. UP/DOWN/redirect/etc.) **to the STDOUT** of the container.

**Examples**:

- `✅ Your monitor "test_up" (https://test.com) is UP (200). Latency was: 1826ms.`
- `🚨 Your monitor "test_down" (https://test2.com) is DOWN. Reason: Connect Error: Connection refused: test2.com`
- `ℹ Request to "test_redirected" (https://redirected.com) has been redirected`
- `🔒️ Your site "test_good_ssl" (https://good-ssl.com) has a VALID certificate`
- `🚨 Your site "test_bad_ssl" (https://no-subject.badssl.com/) has an INVALID certificate. Reason: PKIX path validation failed`

### Language

<!-- md:version 2.0.0 -->
<!-- md:default en -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    app-config.language: en
    ```

=== "ENV"

    ```bash
    APP_LANGUAGE=en
    ```

The language to use. Currently, **only `en` (English) is supported**, but more languages will be added in the future. See the [**Localization**](../localization.md) section for more details.

## Full configuration example

You can find the full configuration example below, which includes all the options mentioned above. You can use it as a starting point for your own configuration.

=== "YAML"

    ```yaml
    micronaut.security.enabled: true
    micronaut.security.token.generator.access-token.expiration: 86400 # 24 hours
    ---
    admin-auth:
      username: YourSuperSecretUsername
      password: YourSuperSecretPassword
      api-key: ThisShouldBeVeryVerySecureToo
    ---
    app-config:
      event-data-retention-days: 365 # 1 year
      latency-data-retention-days: 7 # 1 week
      log-event-handler: true
      language: en
    ---
    smtp-config:
      host: 'your.smtp.server'
      port: 465
      transport-strategy: SMTP_TLS
      username: YourSMTPUsername
      password: YourSMTPPassword
    ```

=== "ENV"

    ```bash
    ENABLE_AUTH=true
    AUTH_MAX_AGE=86400 # 24 hours
    ADMIN_USER=YourSuperSecretUsername
    ADMIN_PASSWORD=YourSuperSecretPassword
    ADMIN_API_KEY=ThisShouldBeVeryVerySecureToo
    DATABASE_HOST=localhost
    DATABASE_PORT=5432
    DATABASE_NAME=postgres
    DATABASE_USER=change_me
    DATABASE_PASSWORD=change_me
    EVENT_DATA_RETENTION_DAYS=365 # 1 year
    LATENCY_DATA_RETENTION_DAYS=7 # 1 week
    ENABLE_LOG_EVENT_HANDLER=true
    APP_LANGUAGE=en
    TZ=UTC
    ```
