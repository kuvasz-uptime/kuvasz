# Incidents

<!-- md:version 4.5.0 -->

An **incident** is every period while a monitor was **down** (or, for HTTP monitors, while its **SSL certificate was invalid**). _Kuvasz_ opens an incident when the monitor goes down, and resolves it as soon as the monitor is healthy again. You can list the incidents of every monitor on the _Incidents_ page of the Web UI, on the details page of a monitor, or via the [**API**](api.md).

## Incident details

Every incident has its own **details page**, which tells the story of the incident on a timeline:

1. **When it started**, and what the error was.
2. Which **integrations were notified** about it.
3. The **notes** you've added to the incident (see below).
4. **When it was resolved**, if it was resolved already, and how long it lasted.
5. Which **integrations were notified** about the resolution.

!!! info "Notified integrations"

    _Kuvasz_ records the integrations every notification was sent to at the time it was sent: the integrations assigned to the monitor, plus the [**global**](notifications.md) ones, except the ones that are disabled or [**exclude**](../management/integrations.md#excluded-events) the given event. Changing the integrations later doesn't rewrite the history of the past incidents. The incidents that were opened before upgrading to 4.5.0 have no recorded integrations.

The details of an incident are available via the [**API**](api.md) and the [**MCP server**](mcp-server.md#incidents) as well. An incident is identified by its **type** (e.g. `HTTP`, `SSL`, `DOCKER`) and its **event ID**, both of which are returned by the incident lists.

## Incident notes

You can add any number of **notes** to an incident to keep track of the investigation, e.g. what you've found out so far, and what you've done to fix it. Notes can be added, edited, and deleted at any time from the details page of the incident, or via the [**API**](api.md). AI assistants can add and delete them through the [**MCP server**](mcp-server.md#incidents).

A note has the following properties:

- **Status**: the state of the investigation the note describes, one of `INVESTIGATING`, `IDENTIFIED`, or `RESOLVED`. It's independent of the state of the incident itself, which is always decided by the checks of the monitor.
- **Content**: free-form plain text.
- **Timestamp** (optional): overrides the creation date of the note wherever it's displayed, and orders the notes of the incident. Useful when you add a note after the fact, e.g. to record when the root cause was actually identified. Without a timestamp, the notes are ordered by their creation date, and editing a note never changes its position.
- **Public**: whether the note is shown on the [**status pages**](status-pages.md) that contain the monitor, so your users can follow what's going on. Notes are **private by default**. The incidents of SSL certificates are never displayed on status pages, so their notes are always private, effectively.

!!! warning "Retention"

    The notes are part of their incidents, so they're deleted together with them, i.e. when the [**event data retention**](../setup/configuration.md#event-data-retention) period is over, or when the monitor is deleted.
