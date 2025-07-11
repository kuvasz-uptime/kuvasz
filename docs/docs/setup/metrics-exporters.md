_Kuvasz_ supports exporting metrics to allow you to **integrate with your existing monitoring and alerting systems**.
This way you can use
_Kuvasz_ alongside your preferred observability stack, so you can introduce it to your infrastructure step by step.

## Enabling the export

<!-- md:version 2.1.0 -->
<!-- md:default `false` -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    micronaut.metrics.enabled: true
    ```

=== "ENV"

    ```bash
    ENABLE_METRICS_EXPORT=true
    ```

The metrics export is **disabled by default**, to enable it, you need to change either your _YAML_ configuration or your environment variables.
_Don't forget to restart the container in both cases!_

## Exported metrics

Currently the following metrics are exported, but all of them is **disabled by default**, you can enable them one by one, tailored to your needs.

Every metric has the following **labels/tags**, that you can use to filter/group them in your monitoring backend:

- `name`: the name of the monitor
- `url`: the URL that is monitored

### Uptime status

<!-- md:version 2.1.0 -->
<!-- md:default `false` -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    metrics-exports.uptime-status: true
    ```

=== "ENV"

    ```bash
    ENABLE_UPTIME_STATUS_EXPORT=true
    ```

This metric is exported as a **gauge** and indicates the current uptime status of the monitored services.

| Status                                | Gauge value |
|---------------------------------------|-------------|
| UP                                    | 1           |
| DOWN                                  | 0           |

### Latest latency

<!-- md:version 2.1.0 -->
<!-- md:default `false` -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    metrics-exports.latest-latency: true
    ```

=== "ENV"

    ```bash
    ENABLE_LATEST_LATENCY_EXPORT=true
    ```

This metric is exported as a **gauge** and reports the latest recorded latency of the monitored endpoint, in milliseconds.

### SSL status

<!-- md:version 2.1.0 -->
<!-- md:default `false` -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    metrics-exports.ssl-status: true
    ```

=== "ENV"

    ```bash
    ENABLE_SSL_STATUS_EXPORT=true
    ```

This metric is exported as a **gauge** and indicates the current status of the SSL certificate of the monitored endpoint (only if SSL checks are enabled).

| Status                                | Gauge value |
|---------------------------------------|-------------|
| VALID, WILL_EXPIRE                    | 1           |
| INVALID                               | 0           |

### SSL expiry

<!-- md:version 2.1.0 -->
<!-- md:default `false` -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    metrics-exports.ssl-expiry: true
    ```

=== "ENV"

    ```bash
    ENABLE_SSL_EXPIRY_EXPORT=true
    ```

This metric is exported as a **gauge** and reports the expiry date (as a Unix timestamp) of the SSL certificate of the monitored endpoint (only if SSL checks are enabled).

## Prometheus

The _Prometheus_ exporter is a built-in exporter that allows you to **expose your metrics** in a format that **can be scraped** by _Prometheus_. It supports the standard _Prometheus_ text format, which is widely used for monitoring and alerting.

### Settings

#### Enabling the exporter

<!-- md:version 2.1.0 -->
<!-- md:default `false` -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    micronaut.metrics.export.prometheus.enabled: true
    ```

=== "ENV"

    ```bash
    ENABLE_PROMETHEUS_EXPORT=true
    ```

#### Descriptions

<!-- md:version 2.1.0 -->
<!-- md:default `true` -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    micronaut.metrics.export.prometheus.descriptions: true
    ```

=== "ENV"

    ```bash
    ENABLE_PROMETHEUS_DESCRIPTIONS=true
    ```

Whether **meter descriptions** should be exposed to _Prometheus_. Disable **to minimize the amount of data** sent on each scrape.

### Scraping the metrics

The metrics are scrapeable through the **`/api/v1/prometheus`** endpoint, and you'll need to **use** [**your API key**](../features/api.md#authentication) by default to access it, just like on any other API endpoint. In case you have disabled the authentication, the endpoint will be available without authentication, of course.

### Example output

```text
kuvasz_monitor_uptime_status{name="nytimes.com",url="https://www.nytimes.com"} 1.0
kuvasz_monitor_latency_latest_milliseconds{name="nytimes.com",url="https://www.nytimes.com"} 29.0
kuvasz_monitor_ssl_status{name="nytimes.com",url="https://www.nytimes.com"} 1.0
kuvasz_monitor_ssl_expiry_seconds{name="nytimes.com",url="https://www.nytimes.com"} 1.758828296E9
```

### Example config

=== "YAML"

    ```yaml
    micronaut:
      metrics:
        enabled: true
        export:
          prometheus:
            enabled: true
            descriptions: true
    ---
    metrics-exports:
        uptime-status: true
        latest-latency: true
        ssl-status: true
        ssl-expiry: true
    ```

=== "ENV"

    ```bash
    ENABLE_METRICS_EXPORT=true
    ENABLE_PROMETHEUS_EXPORT=true
    ENABLE_PROMETHEUS_DESCRIPTIONS=true
    # Enable the individual metrics
    ENABLE_UPTIME_STATUS_EXPORT=true
    ENABLE_LATEST_LATENCY_EXPORT=true
    ENABLE_SSL_STATUS_EXPORT=true
    ENABLE_SSL_EXPIRY_EXPORT=true
    ```

## OpenTelemetry

The _OpenTelemetry_ exporter is a built-in exporter that allows you to **export your metrics to any compatible tool**, that supports the _OpenTelemetry Protocol (OTLP)_. [**OpenTelemetry**](https://opentelemetry.io){ target="blank" } is a vendor-neutral standard for collecting and exporting telemetry data, and a lot of modern observability tools support it (e.g. _Datadog_, _New Relic_, etc.).

!!!info

    _Kuvasz_ will **automatically report the metrics** to the configured [**endpoint**](#url) at the specified [**frequency**](#step).

### Settings

#### Enabling the exporter

<!-- md:version 2.1.0 -->
<!-- md:default `false` -->
<!-- md:type `boolean` -->

=== "YAML"

    ```yaml
    micronaut.metrics.export.otlp.enabled: true
    ```

=== "ENV"

    ```bash
    ENABLE_OTLP_EXPORT=true
    ```

#### URL

<!-- md:version 2.1.0 -->
<!-- md:flag required -->

=== "YAML"

    ```yaml
    micronaut.metrics.export.otlp.url: https://example.host:4318/v1/metrics
    ```

=== "ENV"

    ```bash
    OTLP_EXPORT_URL=https://example.host:4318/v1/metrics
    ```

The **URL** of the _OpenTelemetry_ endpoint to which the metrics will be reported. It is mandatory to set this, if you've enabled the exporter.

#### Headers

<!-- md:version 2.1.0 -->
<!-- md:type `string` -->

=== "YAML"

    ```yaml
    micronaut.metrics.export.otlp.headers: 'Authorization=Bearer Your-collectors-API-token,key2=value'
    ```

=== "ENV"

    ```bash
    OTLP_EXPORT_HEADERS='Authorization=Bearer Your-collectors-API-token,key2=value'
    ```

The **headers** to be sent with the metrics export request. This is useful for authentication or other custom headers required by your _OpenTelemetry_ collector. Multiple headers can be specified as a comma-separated list, in the format of `key1=value1,key2=value2`.

#### Step

<!-- md:version 2.1.0 -->
<!-- md:default `PT1M` -->
<!-- md:type `ISO-8601 duration` -->

=== "YAML"

    ```yaml
    micronaut.metrics.export.otlp.step: PT1M
    ```

=== "ENV"

    ```bash
    OTLP_EXPORT_STEP=PT1M
    ```

The **frequency** of exporting the metrics to _OpenTelemetry_. The default is **1 minute**. More about ISO-8601 durations [**here**](https://en.wikipedia.org/wiki/ISO_8601#Durations){ target="_blank" }.

### Example output

```text
kuvasz.monitor.ssl.status{name=weather.com,url=https://weather.com} 1
kuvasz.monitor.latency.latest.milliseconds{name=samsung.com,url=https://www.samsung.com} 183
kuvasz.monitor.uptime.status{name=google.com,url=https://www.google.com} 1
kuvasz.monitor.ssl.expiry.seconds{name=bbc.com,url=https://www.bbc.com} 1.785147977e+09
```

### Example config

=== "YAML"

    ```yaml
    micronaut:
      metrics:
        enabled: true
        export:
          otlp:
            enabled: true
            url: https://example.host:4318/v1/metrics
            headers: 'Authorization=Bearer Your-collectors-API-token,key2=value'
            step: PT1M
    ---
    metrics-exports:
      uptime-status: true
      latest-latency: true
      ssl-status: true
      ssl-expiry: true
    ```

=== "ENV"

    ```bash
    ENABLE_METRICS_EXPORT=true
    ENABLE_OTLP_EXPORT=true
    OTLP_EXPORT_URL=https://example.host:4318/v1/metrics
    OTLP_EXPORT_HEADERS='Authorization=Bearer Your-collectors-API-token,key2=value'
    OTLP_EXPORT_STEP=PT1M
    # Enable the individual metrics
    ENABLE_UPTIME_STATUS_EXPORT=true
    ENABLE_LATEST_LATENCY_EXPORT=true
    ENABLE_SSL_STATUS_EXPORT=true
    ENABLE_SSL_EXPIRY_EXPORT=true
    ```
