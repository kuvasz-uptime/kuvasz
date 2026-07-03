# Managing monitors

There are three ways to manage your monitors in _Kuvasz_: through the **Web UI**, using a **YAML configuration file**, or via the **REST API**. Each method has its own advantages, and you can choose the one that best fits your workflow.

!!!note "Key concepts for all methods"

    - The **name of the monitor must be unique** per monitor type, otherwise the creation/update will fail. If you want to update an existing monitor, you can use the same name, of course.
    - The UI and the API will be in **read-only mode** if you have defined your monitors via _YAML_

## Management methods - good to know

=== "Web UI (recommended)"

    The Web UI should be intuitive and user-friendly enough to make it straightforward to create, edit, and manage your monitors. You can access the UI by **navigating to the root of your _Kuvasz_ instance** (e.g. `http://0.0.0.0:8080` if you're running it with the default port setup).

=== "YAML (advanced)"

    !!!info "Consequences of describing your monitors as YAML"

        Be aware that if you define your monitors via _YAML_, you **cannot use the UI, or the API** to modify them, you can only view them there (read-only operations are permitted)!
    
        In this case _Kuvasz_ reads your YAML file on startup, compares the monitors in there with the existing ones in the database, and uses the YAML file as the source of truth. 
    
        The same applies if you **used the UI or the API before** to manage your monitors, and you decide to switch to YAML: unless your YAML definition matches the existing monitors by their name, existing monitors **could be deleted or modified**.

    **Restoring from a YAML backup**

    You can export your monitors from the UI under **Settings → Backup & Restore → Export monitors (YAML)**, or via `GET /api/v2/monitors/export/yaml`. To restore that backup later, use **Settings → Backup & Restore → Import monitors (YAML)** or the `POST /api/v2/monitors/import/yaml` API endpoint.

    !!!warning "Restoring a backup is destructive"

        The import follows the same semantics as YAML configuration on startup:

        - Monitors with the same name and type will be **updated** with the values from the backup.
        - Monitors that exist in the database but are **not in the backup** will be **deleted**.
        - Monitors in the backup that do not exist will be **created**.
        - The import **does not** switch the monitors to read-only mode; you can keep managing them through the UI and API afterwards.
        - If any monitor type is currently managed via YAML (read-only mode), the import **will be rejected** with a `405 Method Not Allowed` response until you remove that type from your YAML configuration.

        Before importing, you can enable **Simulate only (dry run)** in the UI or pass `dryRun=true` to the API. This will run the import in a rolled-back transaction and return the number of monitors that would be received, imported/updated, and deleted.

    **What happens if you add one or more monitor to your YAML file?**

    - If there is a monitor in the database that is not in the YAML file, **it will be deleted**.
    - If there is a monitor in the YAML file that is not in the database, **it will be created** and added to the database.
    - If there is a monitor in both the YAML file and the database, and they have the same name, the monitor in the database **will be updated** with the values from the YAML file.

    **What happens if you provide an empty array for a monitor type in the YAML file?**

    ```yaml
    http-monitors: []
    # or
    push-monitors: []
    # or
    icmp-monitors: []
    ```

    In this case all monitors of that type in the database **will be deleted**.

    **What happens if you remove the relevant properties from the YAML file?**

    By that we mean that your YAML file **doesn't contain the relevant property keys** (i.e. `push-monitors`, `http-monitors`, `icmp-monitors`, etc.), or they are **not explicitly set to an empty array** (see the example below).

    ```yaml
    # Watch out for the missing property values here. 
    # This is considered as a missing configuration, 
    # entries in the database will not be touched, 
    # external write to the monitors are allowed.
    http-monitors:
    # or
    push-monitors:
    # or
    icmp-monitors:
    ```

    In this case all monitors in the database **will be kept** (i.e. the ones that were created before via YAML). This is especially useful if you want to **restore your monitors from your exported YAML backup**, but you want to manage them on the UI in the future.

    !!!danger "Changing a monitor's name"

        **If you change the name** of an existing monitor in the YAML file, it will be treated as a new monitor, and the old one will be deleted. This also means that all of your previously recorded events and metrics (i.e. latency history, uptime checks, etc.) **will be lost for that monitor**.

=== "API (expert)"
 
    It might be beneficial to use the REST API of _Kuvasz_ if you would like to **build a low-level integration**.

    This section won't go into details about the API or about exact API calls, since it's **well documented and must be self-explanatory**. You can find more information about the available endpoints and their usage in the [**API documentation**](https://api-docs.kuvasz-uptime.dev){target="_blank"}.

## Available monitor types

<div class="grid cards" markdown>

-   :earth_africa:{ .lg .card-header-icon } __HTTP & SSL monitors__

    ---

    With flexible configuration, adjustable intervals, headers, keyword matching, expected response status codes, response time checks and more.

    [:octicons-arrow-right-24: HTTP & SSL monitors](../http-monitors.md)

-   :heartbeat:{ .lg .card-header-icon } __Push (a.k.a. "cron") monitors__

    ---

    Monitor your services that are not accessible via HTTP, for example backups, cron jobs, etc.

    [:octicons-arrow-right-24: Push monitors](../push-monitors.md)

-   :ping_pong:{ .lg .card-header-icon } __Ping (ICMP) monitors__

    ---

    Check the reachability and latency of any host by sending ICMP echo requests (pings) to it.

    [:octicons-arrow-right-24: ICMP monitors](../icmp-monitors.md)

-   :construction:{ .lg .card-header-icon } __More to come...__

    ---

    Planned new monitor types:

    - **TCP** port monitoring
