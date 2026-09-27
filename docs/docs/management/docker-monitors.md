!!! tip "Before you start..."

    Make sure that you **carefully read the** [**common documentation**](managing-monitors/index.md) about managing the monitors, and that you have at least one [**Docker host**](docker-hosts.md) configured!

## Management methods

=== "Web UI (recommended)"

    If you navigate to the Web UI of _Kuvasz_, you can create a new monitor on the **Dashboard**, or on the **Docker monitors** page, by clicking the "+ New Monitor" button in the page header.

    The **Docker host** can be picked from the configured ones, and the **container** from the ones the selected host reports (including the stopped ones), together with their image and state. The list is fetched when the dialog opens, and whenever you pick another host. If the daemon can't be reached, or it doesn't allow listing the containers, you can still **type the name of the container** yourself.

=== "YAML (advanced)"

    ```yaml title="YAML monitor reference"
    docker-monitors:
    - name: "My Docker Monitor" # (1)!
      category: "Core services" # (10)!
      docker-host: "local" # (2)!
      container: "my-app" # (3)!
      uptime-check-interval: 60 # (4)!
      timeout-ms: 5000 # (5)!
      failure-count-threshold: 1 # (6)!
      ignore-connectivity-check: true # (11)!
      enabled: true # (7)!
      metrics-history-enabled: false # (8)!
      integrations: # (9)!
        - "slack:devops_channel"
    # ... other monitors
    ```

      1. **Name**: The name of the monitor, which must be unique across all Docker monitors.
      2. **Docker host**: The name of the [Docker host](docker-hosts.md) the container runs on.
      3. **Container**: The name or the ID of the container to check.
      4. **Uptime check interval**: The interval in seconds at which the uptime checks will be performed. The **minimum value is 5 seconds**.
      5. **Timeout (ms)**: The timeout of the Docker API requests in milliseconds. Must be between 1 and 30000. Defaults to 5000.
      6. **Failure count threshold**: The number of consecutive failures that should occur before the monitor is considered down. Defaults to 1.
      7. **Enabled**: Whether the monitor is enabled or not. If it's disabled, it won't be checked, and **no events will be recorded** for it.
      8. **Metrics history enabled**: Whether the CPU and memory usage of the container is sampled and recorded. Defaults to false.
      9. **Integrations**: A list of integrations to assign to the monitor. The format is `"{integration-type}:{integration-name}"`, where `integration-type` is the type of the integration (e.g. `email`, `slack`, etc.), and `integration-name` is the name of the integration as defined in the `integrations` section of your YAML file. Example: `email:my-email-integration`.
      10. **Category**: An optional, free-form category to group the monitor on the status pages (e.g. a product or service name).
      11. **Ignore connectivity check**: Whether the monitor should keep being checked even while _Kuvasz_ considers its own outbound connectivity lost. Defaults to **true**, unlike on the other monitor types.

=== "API (expert)"

    This section won't go into details about the API or about exact API calls, since it's **well documented and must be self-explanatory**. You can find more information about the available endpoints and their usage in the [**API documentation**](https://api-docs.kuvasz-uptime.dev).

    However, here are **few of the most important** endpoints:

    - `GET /api/v2/docker-monitors` - List all Docker monitors
    - `GET /api/v2/docker-monitors/{id}` - Get a specific Docker monitor by its ID
    - `POST /api/v2/docker-monitors` - Create a new Docker monitor
    - `PATCH /api/v2/docker-monitors/{id}` - Update an existing Docker monitor
    - `DELETE /api/v2/docker-monitors/{id}` - Delete a Docker monitor
    - `GET /api/v2/docker-hosts` - List the configured Docker hosts

## Settings

### Name

<!-- md:version 4.5.0 -->
<!-- md:flag required -->
<!-- md:type string -->
<!-- md:yaml_prop `name` -->

The name of the monitor, which **must be unique** across all Docker monitors.

### Category

<!-- md:version 4.5.0 -->
<!-- md:type string -->
<!-- md:yaml_prop `category` -->

An optional, free-form category (up to 100 characters), e.g. the name of a product or a service, that is used to group the monitor on the [status pages](../features/status-pages.md). Monitors that share the same category are displayed together in a dedicated section there, with an aggregated status per category. The default is `null`, which means that the monitor is not categorized.

A category is more than a label: a [**status page**](status-pages.md#categories) and a [**maintenance window**](maintenance-windows.md#categories) can select their monitors by it, so tagging a monitor is enough to put it on the right page and into the right maintenance window.

On the _Web UI_ the field offers the categories that are already in use, so you can pick an existing one instead of re-typing it. Typing a category that doesn't exist yet creates it right there.

### Docker host <!-- md:config docker-hosts.md -->

<!-- md:version 4.5.0 -->
<!-- md:flag required -->
<!-- md:type string -->
<!-- md:yaml_prop `docker-host` -->

The **name of the** [**Docker host**](docker-hosts.md) the container runs on, as defined in the `docker-hosts` section of your configuration file.

The host **must be configured when the monitor is created**, otherwise the creation is rejected. If the host is removed from the configuration later, the monitor is kept, but its checks fail until the host is added back, or the monitor is pointed to another one. See [**Removing or renaming a host**](docker-hosts.md#removing-or-renaming-a-host) for the details.

### Container

<!-- md:version 4.5.0 -->
<!-- md:flag required -->
<!-- md:type string -->
<!-- md:yaml_prop `container` -->

The **name or the ID** of the container to check, as the Docker daemon knows it (e.g. `my-app`, or the name _Docker Compose_ generated, like `my-stack-my-app-1`). A leading slash, the way the Engine API itself reports the names, is accepted too.

!!!tip "Prefer the name over the ID"

    The ID of a container changes every time it's **re-created** (e.g. by `docker compose up` after an image update), while its name stays the same. A monitor that refers to an ID would report the re-created container as missing.

### Uptime check interval

<!-- md:version 4.5.0 -->
<!-- md:flag required -->
<!-- md:type number -->
<!-- md:yaml_prop `uptime-check-interval` -->

The interval **in seconds** at which the uptime checks will be performed. The **minimum value is 5 seconds**.

### Enabled

<!-- md:version 4.5.0 -->
<!-- md:default `true` -->
<!-- md:type boolean -->
<!-- md:yaml_prop `enabled` -->

Whether the monitor is enabled or not. If it's disabled, it won't be checked, and **no events will be recorded** for it.

### Timeout

<!-- md:version 4.5.0 -->
<!-- md:default `5000` -->
<!-- md:type number -->
<!-- md:yaml_prop `timeout-ms` -->

The **timeout of the Docker API requests in milliseconds**. Must be between 1 and 30000. If the daemon doesn't answer within this time, the check is considered a failure.

The timeout applies to **every request** of a check separately: the inspection of the container, the resource sampling (when [metrics history](#metrics-history-enabled) is enabled), and each of their [retries](docker-hosts.md#how-the-daemon-is-called). Keep in mind that the resource sampling itself takes about a second, so a timeout below that makes every sampling fail (the uptime check is not affected by that, though).

### Failure count threshold

<!-- md:version 4.5.0 -->
<!-- md:default `1` -->
<!-- md:type number -->
<!-- md:yaml_prop `failure-count-threshold` -->

The number of **consecutive failures** that should occur before the monitor is considered down. Defaults to 1, which means that the monitor will be considered down after the first failure. If you set it to a higher value, for example 3, the monitor will be considered down only after 3 consecutive failures, which can help to reduce false positives, e.g. while a container is being re-created during a deployment.

### Metrics history enabled

<!-- md:version 4.5.0 -->
<!-- md:default `false` -->
<!-- md:type boolean -->
<!-- md:yaml_prop `metrics-history-enabled` -->

Whether the **CPU and memory usage** of the container is sampled and recorded on every check. The samples are charted on the monitor's details page, and exported as [**metrics**](metrics-exporters.md#docker-container-cpu-usage) too. If you disable it on a monitor that has already recorded metrics history, the **existing history will be deleted**.

Unlike on the other monitor types, it's **disabled by default**, because every sample costs an **extra Docker API call**, and the daemon takes about **a second** to answer it. The samples are only taken while the container is running.

### Integrations <!-- md:config ../management/integrations.md -->

<!-- md:version 4.5.0 -->
<!-- md:default empty -->
<!-- md:type list -->
<!-- md:yaml_prop `integrations` -->

A list of **integrations to assign** to the monitor.

If you're using YAML, or the API, the format is `"{type}:{name}"`, where `type` is the alias of the integration (e.g. `email`, `slack`, etc.), and `name` is the name of the integration as defined in the [**`integrations` section of your YAML file**](../management/integrations.md). Example: `email:my-email-integration`.

!!!tip

    You can add/keep **disabled integrations in the list**, but they will not be used for the monitor. This is useful if you want to enable them later without modifying the monitor's configuration.

    **Global integrations** can be explicitly added too, which is handy if you're about to **make them non-global later**, but you want to make sure that they will be assigned to certain monitors even after the change.

### Ignore connectivity check

<!-- md:version 4.5.0 -->
<!-- md:default `true` -->
<!-- md:type boolean -->
<!-- md:yaml_prop `ignore-connectivity-check` -->

Whether the monitor should be checked **even while the** [**connectivity check**](../features/connectivity-check.md) **considers _Kuvasz_ to be disconnected**.

Unlike on every other monitor type, it **defaults to true** here: a container reached through a local socket, or on the same LAN, doesn't care whether _Kuvasz_ has internet access, and suspending its checks during an outage would only **hide a real failure** from you. Disable it for monitors whose Docker host is only reachable over the internet.

!!!info

    This flag has **no effect at all** when the [connectivity check](../setup/configuration.md#connectivity-check) is disabled, which is the default.

## Common operations

### Toggling a monitor

You can **enable or disable a monitor** at any time, which is useful if you want to temporarily stop monitoring a specific container without deleting it.

Disabled monitors **won't be counted in the cumulated metrics**, like uptime ratio.

=== "Web UI (recommended)"

    Look for the **toggle switches** with the :material-pause: sign.

=== "YAML (advanced)"

    Set the `enabled` field to `true` or `false` in your YAML file.

    ```yaml hl_lines="5"
    docker-monitors:
    - name: "My Docker Monitor"
      docker-host: "local"
      container: "my-app"
      enabled: false
    ```

=== "API (expert)"

    Use the `PATCH /api/v2/docker-monitors/{id}` endpoint to update the `enabled` field of the monitor.

### Deleting a monitor

If you delete a monitor, it will be **removed** from the database, and **all of its recorded events and metrics** (i.e. metrics history, uptime checks, etc.) will be deleted as well. This is a **destructive operation**, so make sure you really want to delete the monitor.

=== "Web UI (recommended)"

    Look for the **delete button** with the :material-trash-can: sign next to the monitor you want to delete.

=== "YAML (advanced)"

    **Remove the monitor from your YAML** file, and then restart _Kuvasz_ to apply the changes.

=== "API (expert)"

    Use the `DELETE /api/v2/docker-monitors/{id}` endpoint to delete the monitor by its ID.

### Modifying the assigned integrations

=== "Web UI (recommended)"

    You can modify the assigned integrations of a monitor by clicking on the **configure button** with the :material-cog: sign on the **monitor's detail page** (look for the _Integrations_ block), where you can add or remove integrations as needed.

=== "YAML (advanced)"

    Modify the `integrations` property of your affected monitor, by adding or removing list items, and then restart _Kuvasz_ to apply the changes.

    ```yaml hl_lines="7"
    docker-monitors:
    - name: "My Docker Monitor"
      docker-host: "local"
      container: "my-app"
      uptime-check-interval: 60
      integrations:
        - "slack:devops_channel"
        - "email:my-email-integration"
    ```

=== "API (expert)"

    Use the `PATCH /api/v2/docker-monitors/{id}` endpoint to update the `integrations` field of the monitor. You can add or remove integrations as needed.

    ```json
    {
      "integrations": [
        "email:my-email-integration",
        "slack:my-slack-integration"
      ]
    }
    ```
