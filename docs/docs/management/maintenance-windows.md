# Managing maintenance windows

There are three ways to manage your maintenance windows in _Kuvasz_: through the **Web UI**, using a **YAML configuration file**, or via the **REST API**. Each method has its own advantages, and you can choose the one that best fits your workflow.

!!!note "Key concepts for all methods"

    - The **name of a maintenance window must be unique**, otherwise the creation/update will fail. If you want to update an existing window, you can use the same name, of course.
    - Every window has exactly one of three schedule types, determined by which fields you set:
        - **manual**: neither `cron` nor `start` is set,
        - **recurring**: `cron` + `duration`,
        - **one-off**: `start` + `duration`.
      Setting both `cron` and `start`, or setting a `cron`/`start` without a `duration`, is rejected.
    - The UI and the API will be in **read-only mode** if you have defined your maintenance windows via _YAML_.

## Management methods

=== "Web UI (recommended)"

    Navigate to the **Maintenance windows** page in the Web UI and click the "+ New maintenance window" button in the page header to create one. From there you can pick the schedule type, choose the affected monitors (or make the window global), select the integrations to notify, and toggle the window on and off.

=== "YAML (advanced)"

    ```yaml title="YAML maintenance windows reference"
    maintenance-windows:
      - name: "Nightly DB maintenance" # (1)!
        description: "Recurring nightly maintenance" # (2)!
        enabled: true # (3)!
        global: false # (4)!
        show-on-status-pages: true # (5)!
        cron: "0 2 * * *" # (6)!
        duration: "PT1H" # (7)!
        monitors: # (8)!
          - "http:My monitor 1"
          - "push:My backup 1"
        integrations: # (9)!
          - "slack:my-slack"
      # ... other maintenance windows
    ```

    1. The **unique name** of the maintenance window (required).
    2. An optional, human-readable description.
    3. The master on/off switch. For recurring and one-off windows this is necessary but not sufficient — the schedule still decides whether the window is currently active. A manual window (no `cron` and no `start`) is active whenever this is `true`.
    4. Whether the window applies to **every monitor**, regardless of the `monitors` list.
    5. Whether the window may be [displayed on status pages](status-pages.md).
    6. A **cron expression** for a recurring window. Mutually exclusive with `start` and requires `duration`. Standard cron syntax with extensions (`#`, `L`, `W`) is supported, and it is evaluated in the **server's time zone**.
    7. The **ISO-8601 duration** of the window (e.g. `PT1H30M`). Required for recurring and one-off windows.
    8. The set of monitors the window applies to (ignored when `global` is `true`). Reference monitors by their type and name, in the format `<type>:<name>`, where the supported types are `http`, `push`, and `icmp`, e.g. `http:My HTTP Monitor`.
    9. The set of integrations that receive the window's start and end notifications. Reference them in the format `<type>:<name>`, e.g. `slack:my-slack`.

    For a **one-off window**, replace `cron` with a `start` timestamp instead:

    ```yaml hl_lines="3"
    maintenance-windows:
      - name: "Datacenter migration"
        start: "2026-08-01T22:00:00Z" # (1)!
        duration: "PT2H"
        global: true
    ```

    1. An absolute start timestamp (may be in the past). Mutually exclusive with `cron` and requires `duration`.

    A **manual window** simply omits both `cron` and `start`:

    ```yaml
    maintenance-windows:
      - name: "Ad-hoc maintenance"
        enabled: false # toggle this on when you start the maintenance
        monitors:
          - "http:My monitor 1"
    ```

    !!!info "Consequences of describing your maintenance windows as YAML"

        Be aware that if you define your maintenance windows via _YAML_, you **cannot use the UI, or the API** to modify them, you can only view them there (read-only operations are permitted)!

        In this case _Kuvasz_ reads your YAML file on startup, compares the windows in there with the existing ones in the database, and uses the YAML file as the source of truth.

        The same applies if you **used the UI or the API before** to manage your maintenance windows, and you decide to switch to YAML: unless your YAML definition matches the existing windows by their name, existing windows **could be deleted or modified**.

    **What happens if you add one or more maintenance window to your YAML file?**

    - If there is a window in the database that is not in the YAML file, **it will be deleted**.
    - If there is a window in the YAML file that is not in the database, **it will be created** and added to the database.
    - If there is a window in both the YAML file and the database, and they have the same name, the window in the database **will be updated** with the values from the YAML file.

    **What happens if you provide an empty array for the maintenance windows in the YAML file?**

    ```yaml
    maintenance-windows: []
    ```

    In this case all maintenance windows in the database **will be deleted**.

    **What happens if you remove the relevant properties from the YAML file?**

    By that we mean that your YAML file **doesn't contain the relevant property key** (i.e. `maintenance-windows`), or it is **not explicitly set to an empty array** (see the example below).

    ```yaml
    # Watch out for the missing property value here.
    # This is considered as a missing configuration,
    # entries in the database will not be touched,
    # external write to the maintenance windows are allowed.
    maintenance-windows:
    ```

    In this case all maintenance windows in the database **will be kept** (i.e. the ones that were created before via YAML). This is especially useful if you want to **restore your maintenance windows from your exported YAML backup**, but you want to manage them on the UI in the future.

    !!!danger "Changing a maintenance window's name"

        **If you change the name** of an existing window in the YAML file, it will be treated as a new one, and the old one will be deleted.

=== "API (expert)"

    This section won't go into details about the API or about exact API calls, since it's **well documented and must be self-explanatory**. You can find more information about the available endpoints and their usage in the [**API documentation**](https://api-docs.kuvasz-uptime.dev){target="_blank"}.

    However, here are the available endpoints:

    - `GET /api/v2/maintenance-windows` – List all maintenance windows
    - `GET /api/v2/maintenance-windows/{id}` – Get a specific maintenance window by its ID
    - `POST /api/v2/maintenance-windows` – Create a new maintenance window
    - `PATCH /api/v2/maintenance-windows/{id}` – Update an existing maintenance window
    - `DELETE /api/v2/maintenance-windows/{id}` – Delete a maintenance window by its ID
    - `GET /api/v2/maintenance-windows/export/yaml` – Download all maintenance windows as a YAML backup

## Configuration reference

### Name

<!-- md:version 4.1.0-beta -->
<!-- md:flag required -->
<!-- md:type string -->
<!-- md:yaml_prop `name` -->

The **unique name** of the maintenance window.

### Description

<!-- md:version 4.1.0-beta -->
<!-- md:type string -->
<!-- md:yaml_prop `description` -->

An optional, human-readable description of the maintenance window.

### Enabled

<!-- md:version 4.1.0-beta -->
<!-- md:default true -->
<!-- md:type boolean -->
<!-- md:yaml_prop `enabled` -->

The **master on/off switch** of the window. For recurring and one-off windows this is necessary but not sufficient: the schedule still decides whether the window is currently active. A **manual** window (no `cron` and no `start`) is active whenever this is `true`.

### Global

<!-- md:version 4.1.0-beta -->
<!-- md:default false -->
<!-- md:type boolean -->
<!-- md:yaml_prop `global` -->

Whether the window applies to **every monitor**, regardless of the `monitors` list.

### Show on status pages

<!-- md:version 4.1.0-beta -->
<!-- md:default false -->
<!-- md:type boolean -->
<!-- md:yaml_prop `show-on-status-pages` -->

Whether the window may be [displayed on status pages](status-pages.md).

### Cron

<!-- md:version 4.1.0-beta -->
<!-- md:type string -->
<!-- md:yaml_prop `cron` -->

A **cron expression** with 5 or 6 segments for a recurring window. Standard cron syntax is supported + extensions like `#`, `L` and `W`. It is **mutually exclusive with `start`** and **requires `duration`**. Evaluated in the **server's time zone** (which honors the `TZ` environment variable).

| Field                      | Allowable values                                | Special Characters |
|:---------------------------|:------------------------------------------------|:-------------------|
| `Seconds (may be omitted)` | `0-59`                                          | `, - * /`          |
| `Minutes`                  | `0-59`                                          | `, - * /`          |
| `Hours`                    | `0-23`                                          | `, - * /`          |
| `Day of month`             | `1-31`                                          | `, - * ? / L W`    |
| `Month`                    | `1-12 or JAN-DEC (note: english abbreviations)` | `, - * /`          |
| `Day of week`              | `1-7 or MON-SUN (note: english abbreviations)`  | `, - * ? / L #`    |

#### Special extensions

- `L` Can be used on Day-of-month and Day-of-week fields. It signifies last day of the set of allowed values. In Day-of-month field it's the last day of the month (e.g.. 31 jan, 28 feb (29 in leap years), 31 march, etc.). In Day-of-week field it's Sunday. If there's a prefix, this will be subtracted (5L in Day-of-month means 5 days before last day of Month: 26 jan, 23 feb, etc.)

- `W` Can be specified in Day-of-Month field. It specifies closest weekday (monday-friday). Holidays are not accounted for. "15W" in Day-of-Month field means 'closest weekday to 15 i in given month'. If the 15th is a Saturday, it gives Friday. If 15th is a Sunday, the it gives following Monday.

- `#` Can be used in Day-of-Week field. For example: "5#3" means 'third friday in month' (day 5 = friday, #3 - the third). If the day does not exist (e.g. "5#5" - 5th friday of month) because there aren't 5 fridays in the month, then it won't match until the next month with 5 fridays.

### Start

<!-- md:version 4.1.0-beta -->
<!-- md:type string -->
<!-- md:yaml_prop `start` -->

The **start timestamp** for a single, one-off window (may be in the past), in ISO-8601 format (e.g. `2026-08-01T22:00:00Z`). It is **mutually exclusive with `cron`** and **requires `duration`**.

### Duration

<!-- md:version 4.1.0-beta -->
<!-- md:type string -->
<!-- md:yaml_prop `duration` -->

The **ISO-8601 duration** of the window (e.g. `PT1H30M`). Required for recurring and one-off windows, and must be strictly positive.

### Monitors

<!-- md:version 4.1.0-beta -->
<!-- md:default empty -->
<!-- md:type list -->
<!-- md:yaml_prop `monitors` -->

The set of **monitors the window applies to** (ignored when `global` is `true`).

If you're using YAML, or the API, the format is `"{type}:{name}"`, where `type` is the alias of the monitor's type, and `name` is the name of the monitor. The supported types are `http`, `push`, and `icmp`. Example: `http:My HTTP Monitor`, `push:My backup 1`, `icmp:My ICMP Monitor`.

### Integrations

<!-- md:version 4.1.0-beta -->
<!-- md:default empty -->
<!-- md:type list -->
<!-- md:yaml_prop `integrations` -->

The set of **integrations that receive the window's start and end notifications**.

The format is `"{type}:{name}"`, e.g. `slack:my-slack`. Only the integrations listed here are notified — globally-enabled integrations are **not** notified about maintenance events unless you assign them explicitly. See the [**Notifications**](../features/notifications.md#maintenance-events) documentation for more details.
