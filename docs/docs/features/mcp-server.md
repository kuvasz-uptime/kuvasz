<!-- md:version 4.0.0 -->
<!-- md:flag experimental -->

_Kuvasz_ ships with a built-in [**Model Context Protocol (MCP)**](https://modelcontextprotocol.io/){ target="_blank" } server, which exposes your monitoring data and management operations as **tools that AI assistants can call directly**. This lets you query monitor status, view incidents, and create or toggle monitors through natural language — right inside Claude, Cursor, or any MCP-compatible client.

!!! warning "Experimental feature"

    The MCP server is available as an **experimental feature**! The API surface (tool names and schemas) may change in future releases.

## Enabling the MCP server

The MCP server is **disabled by default**. To enable it, refer to the relevant [configuration](../setup/configuration.md#mcp-server).

Once enabled, the server is available at the `/mcp` endpoint of your _Kuvasz_ instance (e.g. `https://your.kuvasz.host/mcp`). It uses the **Streamable HTTP transport** defined by the MCP specification.

## Connecting AI clients

_Kuvasz_'s MCP server speaks **Streamable HTTP**. Depending on your AI client's capabilities, there are two ways to connect it to the MCP server:

=== "With streamable HTTP support"

    Just make sure that your client is configured to use the `/mcp` endpoint of your _Kuvasz_ instance as an MCP server, and pass the API key for authentication (see below).

=== "Without streamable HTTP support"

    Clients using the **STDIO transport** (like Claude) can still use the MCP server, but require a local bridge to translate between Streamable HTTP and STDIO. The easiest way to achieve this is to use the [`mcp-remote`](https://www.npmjs.com/package/mcp-remote){ target="_blank" } bridge (installed via `npx`, no global install required):
    
    ```json
    {
      "mcpServers": {
        "kuvasz-uptime": {
          "command": "npx",
          "args": [
            "mcp-remote",
            "https://your.kuvasz.host/mcp",
            "--header",
            "X-API-KEY: {{ Your API key }} "
          ]
        }
      }
    }
    ```

### Authentication

The `/mcp` endpoint is protected by a dedicated [**MCP API key**](../setup/configuration.md#mcp-api-key), which is **separate** from the [REST API key](../setup/configuration.md#api-key). The REST API key (or a web UI / OIDC session) does **not** grant access to the MCP endpoint — you must configure the MCP API key to use the server. Pass it in one of the two supported ways:

- **`X-API-KEY` header**: `X-API-KEY: YourMcpApiKey`
- **Bearer token**: `Authorization: Bearer YourMcpApiKey`

!!! info

    If you have [disabled authentication](../setup/configuration.md#toggling-authentication) entirely, the MCP endpoint is also unauthenticated.

## Available tools

!!! note "Read-only mode"

    If you have configured your monitors via YAML, the corresponding `create-*`, `toggle-*`, and `delete-*` tools will return an error when called. The same applies to the `create-maintenance-window`, `toggle-maintenance-window`, and `delete-maintenance-window` tools when maintenance windows are configured via YAML. Additionally, `delete-*` tools will fail if the monitor is referenced by a read-only status page. The `list-*`, `get-*-details`, `get-*-stats`, `list-incidents`, and `list-integrations` tools are always available regardless of read-only mode.
    
    This is the **expected behavior**, and works exactly the same as if you were to perform these operations via the REST API or dashboard.

The MCP server exposes the following tools to connected clients:

### HTTP monitors

<!-- md:version 4.0.0 -->

| Tool                       | Description                                                                                       |
|----------------------------|---------------------------------------------------------------------------------------------------|
| `list-http-monitors`       | List all HTTP monitors with their current uptime and SSL status                                   |
| `get-http-monitor-details` | Get detailed information about an HTTP monitor by ID                                              |
| `get-http-monitor-stats`   | Get latency and uptime statistics (configurable look-back window)                                 |
| `create-http-monitor`      | Create a new HTTP monitor                                                                         |
| `toggle-http-monitor`      | Enable or disable an HTTP monitor (disabling also implicitly disables its SSL checks)             |
| `delete-http-monitor`      | Permanently delete an HTTP monitor by ID, including all its history and events                    |

### ICMP (ping) monitors

<!-- md:version 4.0.0 -->

| Tool                       | Description                                                                    |
|----------------------------|--------------------------------------------------------------------------------|
| `list-icmp-monitors`       | List all ICMP monitors with their current uptime status                        |
| `get-icmp-monitor-details` | Get detailed information about an ICMP monitor by ID                           |
| `get-icmp-monitor-stats`   | Get latency and packet-loss statistics (configurable look-back window)         |
| `create-icmp-monitor`      | Create a new ICMP monitor                                                      |
| `toggle-icmp-monitor`      | Enable or disable an ICMP monitor                                              |
| `delete-icmp-monitor`      | Permanently delete an ICMP monitor by ID, including all its history and events |

### Push (heartbeat) monitors

<!-- md:version 4.0.0 -->

| Tool                       | Description                                                                         |
|----------------------------|-------------------------------------------------------------------------------------|
| `list-push-monitors`       | List all push monitors with their current uptime status                             |
| `get-push-monitor-details` | Get detailed information about a push monitor by ID                                 |
| `get-push-monitor-stats`   | Get uptime statistics (configurable look-back window)                               |
| `create-push-monitor`      | Create a new push monitor                                                           |
| `toggle-push-monitor`      | Enable or disable a push monitor                                                    |
| `delete-push-monitor`      | Permanently delete a push monitor by ID, including all its history and events       |

### Status pages

<!-- md:version 4.0.0 -->

| Tool                      | Description                                                                                                             |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `list-status-pages`       | List all status pages with their basic configuration (title, slug, visibility, monitor count)                           |
| `get-status-page-details` | Get full details of a specific status page including per-monitor uptime status, uptime ratio, and 30-day uptime history |

### Maintenance windows

<!-- md:version 4.1.0-beta -->

| Tool                             | Description                                                                                                                          |
|----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `list-maintenance-windows`       | List all maintenance windows with their schedule (manual, recurring or one-off) and current active state                             |
| `get-maintenance-window-details` | Get full details of a specific maintenance window, including affected monitors, notified integrations, and resolved next start / end |
| `create-maintenance-window`      | Create a new maintenance window (manual, `cron` + `duration` recurring, or `start` + `duration` one-off)                             |
| `toggle-maintenance-window`      | Enable or disable a maintenance window via its master `enabled` switch                                                               |
| `delete-maintenance-window`      | Permanently delete a maintenance window by ID                                                                                        |

The `maintenanceWindows` affecting a monitor (and whether it is currently `inMaintenance`) are also surfaced on the `get-*-monitor-details` tool outputs.

### Integrations

<!-- md:version 4.0.0 -->

| Tool                | Description                                                                                                         |
|---------------------|---------------------------------------------------------------------------------------------------------------------|
| `list-integrations` | List all configured integrations (Slack, Discord, Email, PagerDuty, Telegram, Webhook) with their type and settings |

### Application settings

<!-- md:version 4.0.0 -->

| Tool               | Description                                                                                                                   |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `get-app-settings` | Get the current application settings (authentication, data retention, language, metrics export, MCP server, and version info) |

### Incidents

<!-- md:version 4.0.0 -->

| Tool             | Description                                                                                                                               |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `list-incidents` | List incidents across all monitor types, optionally filtered by monitor ID, with a configurable look-back window and resolved-flag filter |
