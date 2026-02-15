!!! tip "Before you start..."

    Make sure that you **carefully read the** [**common documentation**](managing-monitors/index.md) about managing the monitors!

## Management methods

=== "Web UI (recommended)"

    If you navigate to the Web UI of _Kuvasz_, you can create a new monitor on the **Dashboard**, or on the **Push monitors** page, by clicking the "+ New Monitor" button in the page header.

    ![Creating a monitor](../images/ui/create_push_monitor.webp)

=== "YAML (advanced)"

    ```yaml title="YAML monitor reference"
    push-monitors:
    - name: "My Push Monitor" # (1)!
      heartbeat-interval: 10 # (2)!
      grace-period: 2 # (3)!
      failure-count-threshold: 3 # (7)!
      client-secret: "d6d5a85c-82c0-4bea-9926-c3eed32de32a" # (4)!
      enabled: true # (5)!
      integrations: # (6)!
        - "slack:devops_channel"
    # ... other monitors
    ```

      1. **Name**: The name of the monitor, which must be unique.
      2. **Heartbeat interval**: The interval in seconds at which the monitor expects to receive heartbeats. Minimum 10 seconds.
      3. **Grace period**: The grace period in seconds after the heartbeat interval during which a missed heartbeat will not mark the monitor as DOWN.
      4. **Client secret**: The unique client secret, used to identify & authenticate heartbeats.
      5. **Enabled**: Whether the monitor is enabled or not. If it's disabled, it won't be checked, and **no events will be recorded** for it.
      6. **Integrations**: A list of integrations to assign to the monitor. The format is `"{integration-type}:{integration-name}"`, where `integration-type` is the type of the integration (e.g. `email`, `slack`, etc.), and `integration-name` is the name of the integration as defined in the `integrations` section of your YAML file. Example: `email:my-email-integration`.
      7. **Failure count threshold**: The number of consecutive failures that should occur before the monitor is considered down. Defaults to 1.

=== "API (expert)"

    This section won't go into details about the API or about exact API calls, since it's **well documented and must be self-explanatory**. You can find more information about the available endpoints and their usage in the [**API documentation**](https://api-docs.kuvasz-uptime.dev){target="_blank"}.

    However, here are **few of the most important** endpoints:

    - `GET /api/v2/push-monitors` – List all push monitors
    - `GET /api/v2/push-monitors/{id}` – Get a specific push monitor by its ID
    - `POST /api/v2/push-monitors` – Create a new push monitor
    - `PATCH /api/v2/push-monitors/{id}` – Update an existing push monitor
    - `DELETE /api/v2/push-monitors/{id}` – Delete a push monitor

## Settings

### Name

<!-- md:version 3.2.0 -->
<!-- md:flag required -->
<!-- md:type string -->
<!-- md:yaml_prop `name` -->

The name of the monitor, which **must be unique** across all push monitors.

### Enabled

<!-- md:version 3.2.0 -->
<!-- md:default `true` -->
<!-- md:type boolean -->
<!-- md:yaml_prop `enabled` -->

Whether the monitor is enabled or not. If it's disabled, it won't be checked, and **no events will be recorded** for it.

### Heartbeat interval

<!-- md:version 3.2.0 -->
<!-- md:flag required -->
<!-- md:type number -->
<!-- md:yaml_prop `heartbeat-interval` -->

The interval in seconds at which the monitor expects to receive heartbeats. The **minimum is 10 seconds**.

### Grace period

<!-- md:version 3.2.0 -->
<!-- md:default `0` -->
<!-- md:type number -->
<!-- md:yaml_prop `grace-period` -->

The grace **period in seconds after the heartbeat interval** during which a missed heartbeat will not mark the monitor as DOWN.

### Failure count threshold

<!-- md:version 3.5.0 -->
<!-- md:default `1` -->
<!-- md:type number -->
<!-- md:yaml_prop `failure-count-threshold` -->

The number of **consecutive failures** that should occur before the monitor is considered down. Defaults to 1, which means that the monitor will be considered down after the first failure. If you set it to a higher value, for example 3, the monitor will be considered down only after 3 consecutive failures, which can help to reduce false positives in case of temporary network issues or other transient problems.

### Client secret

<!-- md:version 3.2.0 -->
<!-- md:flag required -->
<!-- md:type string -->
<!-- md:yaml_prop `client-secret` -->

The **unique** client secret, used to identify & authenticate heartbeats. Needs to be **at least 36 characters long**. 

### Integrations <!-- md:config ../management/integrations.md -->

<!-- md:version 3.2.0 -->
<!-- md:default empty -->
<!-- md:type list -->
<!-- md:yaml_prop `integrations` -->

A list of **integrations to assign** to the monitor. 

If you're using YAML, or the API, the format is `"{type}:{name}"`, where `type` is the alias of the integration (e.g. `email`, `slack`, etc.), and `name` is the name of the integration as defined in the [**`integrations` section of your YAML file**](../management/integrations.md). Example: `email:my-email-integration`.

!!!tip
    
    You can add/keep **disabled integrations in the list**, but they will not be used for the monitor. This is useful if you want to enable them later without modifying the monitor's configuration.

    **Global integrations** can be explicitly added too, which is handy if you're about to **make them non-global later**, but you want to make sure that they will be assigned to certain monitors even after the change.

## Sending heartbeats / signaling failures

You need to **fire HTTP requests** against your _Kuvasz_ instance in order to **send a heartbeat** and you can optionally **signal a failure** of your monitor too. 

Heartbeats will make your monitors "UP", and manual failures will immediately make them "DOWN" (besides the, automatic background checks of course that will mark it as "DOWN" anyway if a heartbeat hasn't been received for the configured period).

These endpoints are not protected by any authentication mechanism, the client secret is not just an identifier for the monitor, but also something that implicitly authenticates the request.

### Heartbeat

You can use either a `POST` or a `GET` request on `/api/v2/push-monitors/heartbeats/{clientSecret}` where `clientSecret` is the one that you set on your monitor. The request body on `POST` is not parsed, so you can omit it.

```bash
curl '[YOUR_HOST]/api/v2/push-monitors/heartbeats/[CLIENT_SECRET]'
```

### Failure

Just like for the heartbeats, you can also use either a `POST` or a `GET` request on `/api/v2/push-monitors/heartbeats/{clientSecret}/failure`. The two differences are, that:

- sending failures are not necessary, monitors with missed heartbeats will be marked as "DOWN" automatically
- the POST request's **body can contain an optional `error` field** in case you would like to provide more details about the failure (e.g. the cause of the failure that is only known by the client).

```bash
curl '[YOUR_HOST]/api/v2/push-monitors/heartbeats/[CLIENT_SECRET]/failure' \
--header 'Content-Type: application/json' \
--data '{
    "error": "failed to execute pgdump"
}'
```

!!!tip

    You can also find these details in the [**API documentation**](https://api-docs.kuvasz-uptime.dev){target="_blank"}.

## Common operations

### Toggling a monitor

You can **enable or disable a monitor** at any time, which is useful if you want to temporarily stop monitoring a specific service without deleting it.

Disabled monitors **won't be counted in the cumulated metrics**, like uptime ratio.

=== "Web UI (recommended)"

    Look for the **toggle switches** with the :material-pause: sign.

=== "YAML (advanced)"

    Set the `enabled` field to `true` or `false` in your YAML file.

    ```yaml hl_lines="6"
    push-monitors:
    - name: "My Push Monitor"
      heartbeat-interval: 10
      grace-period: 2
      client-secret: "d6d5a85c-82c0-4bea-9926-c3eed32de32a"
      enabled: true
      integrations:
        - "slack:devops_channel"
        - "slack:general_channel"
    ```

=== "API (expert)"

    Use the `PATCH /api/v2/push-monitors/{id}` endpoint to update the `enabled` field of the monitor.

### Deleting a monitor

If you delete a monitor, it will be **removed** from the database, and **all of its recorded events and metrics** (i.e. uptime checks, etc.) will be deleted as well. This is a **destructive operation**, so make sure you really want to delete the monitor.

=== "Web UI (recommended)"

    Look for the **delete button** with the :material-trash-can: sign next to the monitor you want to delete.

=== "YAML (advanced)"

    **Remove the monitor from your YAML** file, and then restart _Kuvasz_ to apply the changes.

=== "API (expert)"

    Use the `DELETE /api/v2/push-monitors/{id}` endpoint to delete the monitor by its ID.

### Modifying the assigned integrations

=== "Web UI (recommended)"

    You can modify the assigned integrations of a monitor by clicking on the **configure button** with the :material-cog: sign on the **monitor's detail page** (look for the _Integrations_ block), where you can add or remove integrations as needed.

=== "YAML (advanced)"

    Modify the `integrations` property of your affected monitor, by adding or removing list items, and then restart _Kuvasz_ to apply the changes.

    ```yaml hl_lines="7"
    push-monitors:
    - name: "My Push Monitor"
      heartbeat-interval: 10
      grace-period: 2
      client-secret: "d6d5a85c-82c0-4bea-9926-c3eed32de32a"
      enabled: true
      integrations:
        - "slack:devops_channel"
        - "slack:general_channel"
    ```

=== "API (expert)"

    Use the `PATCH /api/v2/push-monitors/{id}` endpoint to update the `integrations` field of the monitor. You can add or remove integrations as needed.

    ```json
    {
      "integrations": [
        "email:my-email-integration",
        "slack:my-slack-integration"
      ]
    }
    ```
