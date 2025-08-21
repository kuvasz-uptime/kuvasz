!!!tip "Are you looking for 1.x.x changelogs?"

    Prior to version 2.0.0, the changelogs were maintained **only in the** [**GitHub repository**](https://github.com/kuvasz-uptime/kuvasz/releases){ target="_blank" }.

## 2.5.0 <small>2025-08-22</small> { id="2.5.0" data-toc-label="2.5.0" }

### New features

- **Custom request headers**: you can now specify custom headers to be sent with the HTTP requests, allowing for more flexible and tailored monitoring setups. See the [**HTTP monitors > Request headers**](management/http-monitors.md#request-headers) section for more details.
- **Custom request body**: you can send your own JSON body to the monitored HTTP endpoint, which can be useful for testing APIs that require a specific payload. See the [**HTTP monitors > Request body**](management/http-monitors.md#request-body) section for more details.
- **Expected headers**: this is a brand new evaluation option that allows you to specify expected headers in the HTTP response. If the response does not contain the expected headers, the monitor will be marked as `DOWN`. You can find more details in the [**HTTP monitors > Expected headers**](management/http-monitors.md#expected-headers) section.
- **New HTTP request methods**: `POST`, `PUT`, `PATCH`, `DELETE` and `OPTIONS` are now supported in addition to the existing `GET` and `HEAD`. This allows you to monitor endpoints that require different HTTP methods, such as APIs that expect a `POST` request with a specific payload. See the [**HTTP monitors > Request method**](management/http-monitors.md#request-method) section for more details.
- **Revamped UI for creating and editing HTTP monitors**: the UI of the existing modal has been slightly redesigned to make it easier and more convenient to configure HTTP monitors with the new features. The fresh look is hopefully more intuitive and user-friendlier by having a **better structure**.

![Kuvasz HTTP monitor creation modal](images/features/new_monitor_modal.webp)

## 2.4.0 <small>2025-08-15</small> { id="2.4.0" data-toc-label="2.4.0" }

### New features

- **New HTTP response evaluation options**: you can now configure your HTTP monitors to:
    - accept only [**specific HTTP status codes**](management/http-monitors.md#expected-status-codes) as valid responses
    - check for a [**keyword in the response body**](management/http-monitors.md#expected-keyword), and you can make the check optionally [**case-sensitive**](management/http-monitors.md#expected-keyword-case-sensitivity), or also [**reversed**](management/http-monitors.md#expected-keyword-negation) to check for the absence of the keyword
    - **[check the response time](management/http-monitors.md#response-time-threshold)** against a **threshold** (in milliseconds) to ensure that the response is not only valid but also fast enough

![Kuvasz evaluation settings](images/features/kuvasz_new_matchers.webp)

- **More details** are persisted **about the errors** that occur during the HTTP uptime checks
- Added **French** translation, thanks to [**@waazaa-fr**](https://github.com/waazaa-fr){ target="_blank" }!
- Added **Polish** translation, thanks to [**@nkkfs**](https://github.com/nkkfs){ target="_blank" }!
- Made the **URL on details page clickable**, so you can easily open the target URL in a new tab

### Improvements

- **Re-worked the logic of the HTTP uptime check configuration & evaluation** to make it easier to introduce new configuration & evaluation options in the future
- [**@by-su**](https://github.com/by-su){ target="_blank" } **improved the validation messages** around the admin authentication configuration, and also extended the docs to clarify the usage of it, thanks for that!
- **Other validation messages** have been improved as well, to make them more user-friendly if something goes wrong during the bootstrapping of _Kuvasz_ or during an API request.
- **Client-related HTTP response errors are not retried** anymore, only the server-related ones. This means practically that 4xx responses will be evaluated as-is without retrying them, while 5xx responses will be retried up to 3 times with an exponential backoff strategy.

### Fixes

- Translated the "...[REDACTED]" string to make it internationalization friendly
- **Fixed the latency measurement logic** to not include the time spent on retrying failing HTTP requests
- **Fixed the following CVEs** by upgrading 3rd party dependencies:
    - [CVE-2025-49146](https://nvd.nist.gov/vuln/detail/CVE-2025-49146){ target="_blank" }
    - [CVE-2025-53864](https://nvd.nist.gov/vuln/detail/CVE-2025-53864){ target="_blank" }

## 2.3.1 <small>2025-07-25</small> { id="2.3.1" data-toc-label="2.3.1" }

### Fixes

- Make sure that no deadlocks can occur during the uptime checks by introducing an expiry for every single lock as a fail-safe fallback mechanism
- UI: Align the cards on the settings page better, by not having gaps between them

### Chore

- Dependencies: bumped i18n4k to 0.11.0

## 2.3.0 <small>2025-07-19</small> { id="2.3.0" data-toc-label="2.3.0" }

### Features

- **Discord integration**: Added support for sending notifications to Discord channels via webhooks. Configure Discord integrations in your YAML file and receive real-time alerts about monitor status changes directly in your Discord server.

## 2.2.0 <small>2025-07-17</small> { id="2.2.0" data-toc-label="2.2.0" }

### Features

- **Metrics exporter settings** are exposed both on the API (under `GET /api/v1/settings`) and on the UI, so you can easily get an overview of the effective configuration.
- A **live demo** is available at [**demo.kuvasz-uptime.dev**](https://demo.kuvasz-uptime.dev){ target="blank" } where you can try out the latest features of _Kuvasz_ without setting up your own instance. Further details, credentials [**here**](demo.md).

### Fixes

- Fixed the glitch on the UI regarding read-only mode in case the underlying logic was initialized before YAML monitors were loaded. This caused the UI to not show the read-only mode correctly, even though the backend was working as expected.

## 2.1.0 <small>2025-07-11</small> { id="2.1.0" data-toc-label="2.1.0" }

### Features

- **Metrics exporters**: Added support for exporting metrics to _OpenTelemetry_ and _Prometheus_. See the [**Metrics exporters**](management/metrics-exporters.md) section for more details. With this feature, you easily integrate _Kuvasz_ with your existing observability stack. Currently exposed metrics are:
    - Uptime status
    - Latest latency
    - SSL status
    - SSL expiry date
- **Bearer token authentication**: Added support for [**Bearer token authentication on the API**](features/api.md#authentication), along with the existing API key authentication. This allows you to use the same authentication mechanism as other modern APIs, making it easier to integrate with your existing systems.

## 2.0.0 <small>2025-07-02</small> { id="2.0.0" data-toc-label="2.0.0" }

### Breaking changes

- **Native image build logic has been removed**: Native images will not be supported in the future due to their higher level of unpredictability, and the achieved performance gain/resource saving in exchange is not so significant.
- **PostgreSQL 12+** is the minimum supported DB version.
- **The HTTP communication log has been removed**, because it was an unnecessary overhead in the network pipeline, and a built-in solution is also available now.
- **Authentication** has been fully reworked, read the [**Authentication**](setup/configuration.md#authentication) section for more details.
- The **latency data** (avg, p95, p99) of the monitors are not returned under `MonitorDetailsDto`, because a new endpoint is introduced for metrics like this under `/api/v1/monitors/{monitorId}/stats`
- (might be breaking, but not necessarily): SSL validation now takes **intermediate certs** into account
- The `DELETE /monitors/{monitorId}/pagerduty-integration-key` and `PUT /monitors/{monitorId}/pagerduty-integration-key` endpoints are gone, because the PATCH endpoint is now flexible enough to support both use-cases.

### Features

- **New Monitor attributes** (every default value also applies for the existing monitors):
    * `requestMethod`: `GET` or `HEAD`. The latter is generally faster, but be aware that certain targets might not support it (default `GET`)
    * `latencyHistoryEnabled`: `true` or `false`. If set to `false` latency will be not logged or returned in the monitors' metrics -> Better for a snappier experience on a slow machine (default `true`)
    * `forceNoCache`: `true` or `false`. If set to `true`, a `Cache-Control: no-cache` header will be sent with the request (default `true`)
    * `followRedirects`: `true` or `false`. If set to `true`, Kuvasz will follow redirects during uptime checks, and the last, non-redirected URL will be evaluated (default `true`)
    * `sslExpiryThreshold`: The number of days before the SSL certificate expires when a notification should be sent.
- **Option to disable authentication** (useful in a home-lab, for example) via `ENABLE_AUTH`. `true` or `false`, default `true`
- **Optimization of the check scheduling logic**: the first uptime check will be scheduled randomly between 1 second and the configured interval of the monitor to prevent hitting the HTTP client with a lot of requests right after the startup.
- **Optimization of the uptime checker**:
    * Made the error handling more robust by handling exceptions that come from invalid response format (e.g. invalid status code)
    * Increased the client's read timeout to 30s
    * Added support for non-absolute redirect URLs (a redirect location of `/path/something-else` on `https://example.com` will be resolved as `https://example.com/path/something-else`
    * Detect and avoid redirect loops
- **Improvement of the `DOWN` event's error formatting** both for showing and saving it. Non-visible/printable characters, and long response errors are now sanitised and might be redacted. If the uptime check fails with a standard HTTP status code and a standard error response, then the HTTP status and its name will be the error's "label" (e.g. `403 Forbidden`)
- Initial (i.e. **if there is no previous state for a given monitor**) UP & VALID states of uptime & SSL checks are not sent to RTC & SMPT event handlers to prevent sending irrelevant notifications upon the first startup
- The **latency metrics** calculation logic **has been optimized** to handle large datasets efficiently
- ✨A brand-new **Web UI** has been introduced, which is more modern, responsive, and user-friendly
- Monitors are configurable via a **YAML file** besides the UI and the API ("_infrastructure as code_" way)
- Exposed `nextUptimeCheck` and `nextSSLCheck` on the API
- Made `sslValidUntil` persisted on the SSL events, and exposed it on the API
- `sslExpiryThreshold` is configurable now on a per monitor basis
- Uptime & latency data retention are separetely configurable now
- Made the whole project translatable (the only language set up is English, as of now, but future translations are already super-easy)
- Monitor filtering on the API was greatly improved with new filters: `enabled: Boolean?`, `sslCheckEnabeld: Boolean?`, `uptimeStatus: UptimeStatus[]?` and `sslStatus: SSLStatus[]?`
- App and integration settings are exposed now both on the UI and on the API (under `GET /api/v1/settings`)
- Added more latency metrics to `GET /api/v1/monitors/{monitorId}/stats`: min, max and p90
- The integration setup has been completely reworked, making it smarter and more flexible. From now on, you can set up **multiple integrations per type** (Slack, E-mail, etc.) in your YAML config. Then you can make them global (that is in effect for all your monitors without further configuration), or **you can assign them on a per monitor basis**.

### Chore
- Simplified and streamlined the things around jOOQ
- Simplified the logging configuration by moving it to Micronaut's own config file
- Changed the base image to `liberica-runtime-container:jre-17` and reduced the compressed image size by ~23%
- Build `arm64` images too
- Bumped the 3rd party dependencies to their latest versions
- Use Java 21
- Switched to a multi-module project layout (should have done it at the beginning 🤦 )
- Switched to kover from JaCoCo

The **full changelog** is available on [**GitHub**](https://github.com/kuvasz-uptime/kuvasz/releases/tag/2.0.0)
