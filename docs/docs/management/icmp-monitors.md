!!! tip "Before you start..."

    Make sure that you **carefully read the** [**common documentation**](managing-monitors/index.md) about managing the monitors!

## Management methods

=== "Web UI (recommended)"

    If you navigate to the Web UI of _Kuvasz_, you can create a new monitor on the **Dashboard**, or on the **Ping (ICMP) monitors** page, by clicking the "+ New Monitor" button in the page header.

=== "YAML (advanced)"

    ```yaml title="YAML monitor reference"
    icmp-monitors:
    - name: "My ICMP Monitor" # (1)!
      host: "example.com" # (2)!
      uptime-check-interval: 60 # (3)!
      packet-count: 3 # (4)!
      timeout-seconds: 5 # (5)!
      packet-loss-threshold: 100 # (6)!
      failure-count-threshold: 1 # (7)!
      enabled: true # (8)!
      metrics-history-enabled: true # (9)!
      integrations: # (10)!
        - "slack:devops_channel"
    # ... other monitors
    ```

      1. **Name**: The name of the monitor, which must be unique across all ICMP monitors.
      2. **Host**: The hostname or IP address to ping.
      3. **Uptime check interval**: The interval in seconds at which the uptime checks will be performed. The **minimum value is 5 seconds**.
      4. **Packet count**: The number of ICMP packets to send per check. Must be between 1 and 10. Defaults to 3.
      5. **Timeout seconds**: The per-ping timeout in seconds. Must be between 1 and 30. Defaults to 5.
      6. **Packet loss threshold**: The packet loss percentage (1-100) at or above which the check is considered DOWN. The default value of 100 means all packets must fail to trigger a DOWN event.
      7. **Failure count threshold**: The number of consecutive failures that should occur before the monitor is considered down. Defaults to 1.
      8. **Enabled**: Whether the monitor is enabled or not. If it's disabled, it won't be checked, and **no events will be recorded** for it.
      9. **Metrics history enabled**: Whether metrics history (latency, packet loss) is recorded for the monitor. Defaults to true.
      10. **Integrations**: A list of integrations to assign to the monitor. The format is `"{integration-type}:{integration-name}"`, where `integration-type` is the type of the integration (e.g. `email`, `slack`, etc.), and `integration-name` is the name of the integration as defined in the `integrations` section of your YAML file. Example: `email:my-email-integration`.

=== "API (expert)"

    This section won't go into details about the API or about exact API calls, since it's **well documented and must be self-explanatory**. You can find more information about the available endpoints and their usage in the [**API documentation**](https://api-docs.kuvasz-uptime.dev){target="_blank"}.

    However, here are **few of the most important** endpoints:

    - `GET /api/v2/icmp-monitors` - List all ICMP monitors
    - `GET /api/v2/icmp-monitors/{id}` - Get a specific ICMP monitor by its ID
    - `POST /api/v2/icmp-monitors` - Create a new ICMP monitor
    - `PATCH /api/v2/icmp-monitors/{id}` - Update an existing ICMP monitor
    - `DELETE /api/v2/icmp-monitors/{id}` - Delete an ICMP monitor

## Settings

### Name

<!-- md:version 3.10.0 -->
<!-- md:flag required -->
<!-- md:type string -->
<!-- md:yaml_prop `name` -->

The name of the monitor, which **must be unique** across all ICMP monitors.

### Host

<!-- md:version 3.10.0 -->
<!-- md:flag required -->
<!-- md:type string -->
<!-- md:yaml_prop `host` -->

The **hostname or IP address** to ping. Can be a domain name (e.g. `example.com`) or an IPv4/IPv6 address.

### Uptime check interval

<!-- md:version 3.10.0 -->
<!-- md:flag required -->
<!-- md:type number -->
<!-- md:yaml_prop `uptime-check-interval` -->

The interval **in seconds** at which the uptime checks will be performed. The **minimum value is 5 seconds**.

### Enabled

<!-- md:version 3.10.0 -->
<!-- md:default `true` -->
<!-- md:type boolean -->
<!-- md:yaml_prop `enabled` -->

Whether the monitor is enabled or not. If it's disabled, it won't be checked, and **no events will be recorded** for it.

### Packet count

<!-- md:version 3.10.0 -->
<!-- md:default `3` -->
<!-- md:type number -->
<!-- md:yaml_prop `packet-count` -->

The **number of ICMP packets to send** per check. Must be between 1 and 10. A higher value gives a more accurate picture of connectivity, but each check will take longer.

### Timeout seconds

<!-- md:version 3.10.0 -->
<!-- md:default `5` -->
<!-- md:type number -->
<!-- md:yaml_prop `timeout-seconds` -->

The **per-ping timeout in seconds**. Must be between 1 and 30. If an individual ICMP packet does not receive a reply within this time it is considered lost.

### Packet loss threshold

<!-- md:version 3.10.0 -->
<!-- md:default `100` -->
<!-- md:type number -->
<!-- md:yaml_prop `packet-loss-threshold` -->

The **packet loss percentage** (1-100) at or above which the check is considered DOWN. The default value of `100` means that _all_ packets must be lost for the monitor to be marked as DOWN. Lower values make the check more sensitive - for example, a value of `50` will trigger a DOWN event if half of the sent packets are lost.

### Failure count threshold

<!-- md:version 3.10.0 -->
<!-- md:default `1` -->
<!-- md:type number -->
<!-- md:yaml_prop `failure-count-threshold` -->

The number of **consecutive failures** that should occur before the monitor is considered down. Defaults to 1, which means that the monitor will be considered down after the first failure. If you set it to a higher value, for example 3, the monitor will be considered down only after 3 consecutive failures, which can help to reduce false positives in case of temporary network issues or other transient problems.

### Metrics history enabled

<!-- md:version 3.10.0 -->
<!-- md:default `true` -->
<!-- md:type boolean -->
<!-- md:yaml_prop `metrics-history-enabled` -->

Whether the metrics history (latency and packet loss over time) is enabled or not. If it's disabled, the monitor **won't record the measured metrics**. If you disable it on a monitor that has already recorded metrics history, the **existing history will be deleted**.

### Integrations <!-- md:config ../management/integrations.md -->

<!-- md:version 3.10.0 -->
<!-- md:default empty -->
<!-- md:type list -->
<!-- md:yaml_prop `integrations` -->

A list of **integrations to assign** to the monitor.

If you're using YAML, or the API, the format is `"{type}:{name}"`, where `type` is the alias of the integration (e.g. `email`, `slack`, etc.), and `name` is the name of the integration as defined in the [**`integrations` section of your YAML file**](../management/integrations.md). Example: `email:my-email-integration`.

!!!tip

    You can add/keep **disabled integrations in the list**, but they will not be used for the monitor. This is useful if you want to enable them later without modifying the monitor's configuration.

    **Global integrations** can be explicitly added too, which is handy if you're about to **make them non-global later**, but you want to make sure that they will be assigned to certain monitors even after the change.

## Common operations

### Toggling a monitor

You can **enable or disable a monitor** at any time, which is useful if you want to temporarily stop monitoring a specific host without deleting it.

Disabled monitors **won't be counted in the cumulated metrics**, like uptime ratio.

=== "Web UI (recommended)"

    Look for the **toggle switches** with the :material-pause: sign.

=== "YAML (advanced)"

    Set the `enabled` field to `true` or `false` in your YAML file.

    ```yaml hl_lines="4"
    icmp-monitors:
    - name: "My ICMP Monitor"
      host: "example.com"
      enabled: false
    ```

=== "API (expert)"

    Use the `PATCH /api/v2/icmp-monitors/{id}` endpoint to update the `enabled` field of the monitor.

### Deleting a monitor

If you delete a monitor, it will be **removed** from the database, and **all of its recorded events and metrics** (i.e. metrics history, uptime checks, etc.) will be deleted as well. This is a **destructive operation**, so make sure you really want to delete the monitor.

=== "Web UI (recommended)"

    Look for the **delete button** with the :material-trash-can: sign next to the monitor you want to delete.

=== "YAML (advanced)"

    **Remove the monitor from your YAML** file, and then restart _Kuvasz_ to apply the changes.

=== "API (expert)"

    Use the `DELETE /api/v2/icmp-monitors/{id}` endpoint to delete the monitor by its ID.

### Modifying the assigned integrations

=== "Web UI (recommended)"

    You can modify the assigned integrations of a monitor by clicking on the **configure button** with the :material-cog: sign on the **monitor's detail page** (look for the _Integrations_ block), where you can add or remove integrations as needed.

=== "YAML (advanced)"

    Modify the `integrations` property of your affected monitor, by adding or removing list items, and then restart _Kuvasz_ to apply the changes.

    ```yaml hl_lines="6"
    icmp-monitors:
    - name: "My ICMP Monitor"
      host: "example.com"
      uptime-check-interval: 60
      integrations:
        - "slack:devops_channel"
        - "email:my-email-integration"
    ```

=== "API (expert)"

    Use the `PATCH /api/v2/icmp-monitors/{id}` endpoint to update the `integrations` field of the monitor. You can add or remove integrations as needed.

    ```json
    {
      "integrations": [
        "email:my-email-integration",
        "slack:my-slack-integration"
      ]
    }
    ```
