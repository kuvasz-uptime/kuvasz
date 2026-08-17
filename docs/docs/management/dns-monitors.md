!!! tip "Before you start..."

    Make sure that you **carefully read the** [**common documentation**](managing-monitors/index.md) about managing the monitors!

## Management methods

=== "Web UI (recommended)"

    If you navigate to the Web UI of _Kuvasz_, you can create a new monitor on the **Dashboard**, or on the **DNS monitors** page, by clicking the "+ New Monitor" button in the page header.

=== "YAML (advanced)"

    ```yaml title="YAML monitor reference"
    dns-monitors:
    - name: "My DNS Monitor" # (1)!
      category: "Core services" # (17)!
      host: "example.com" # (2)!
      uptime-check-interval: 60 # (3)!
      resolver-host: "1.1.1.1" # (4)!
      resolver-port: 53 # (5)!
      transport: "UDP" # (6)!
      record-matchers: # (7)!
        - record-type: "A"
          match-type: "EXACT"
          value: "93.184.216.34"
        - record-type: "TXT"
          match-type: "CONTAINS"
          value: "v=spf1"
      expected-response-code: "NOERROR" # (8)!
      drift-detection-enabled: false # (9)!
      drift-record-types: # (10)!
        - "NS"
        - "MX"
      timeout-ms: 5000 # (11)!
      latency-threshold-ms: 1000 # (12)!
      failure-count-threshold: 1 # (13)!
      enabled: true # (14)!
      metrics-history-enabled: true # (15)!
      integrations: # (16)!
        - "slack:devops_channel"
    # ... other monitors
    ```

      1. **Name**: The name of the monitor, which must be unique across all DNS monitors.
      2. **Host**: The domain name to query (e.g. `example.com`).
      3. **Uptime check interval**: The interval in seconds at which the uptime checks will be performed. The **minimum value is 5 seconds**.
      4. **Resolver host**: Optional. The nameserver to send the queries to. If unset, the **system resolver** is used.
      5. **Resolver port**: The port of the nameserver. Must be between 1 and 65535. Defaults to 53.
      6. **Transport**: `UDP` (the default, with an automatic TCP fallback on truncated responses) or `TCP` (forced for every query).
      7. **Record matchers**: The assertions to evaluate against the resolved records. Each matcher has a `record-type`, a `match-type` (`EXACT`, `CONTAINS` or `REGEX`, defaults to `CONTAINS`) and a `value`. **All matchers must pass**, and a single matcher passes if **any** record of its type satisfies it. If you leave it empty, the check falls back to a plain `A` lookup and the up/down status is decided by the response code alone.
      8. **Expected response code**: The response code the resolver is expected to return: `NOERROR` (default), `NXDOMAIN`, `SERVFAIL` or `REFUSED`. Anything other than `NOERROR` requires the record matchers to be **empty**.
      9. **Drift detection enabled**: Whether a **dedicated notification** is sent when the resolved records change between two checks, without affecting the up/down status. Defaults to false.
      10. **Drift record types**: Optional. The record types drift detection watches. When empty, it watches exactly the types your record matchers cover; naming types here **replaces** that default with the listed ones. Ignored when drift detection is disabled.
      11. **Timeout (ms)**: The timeout of a whole check in milliseconds, shared as a budget by every query it makes. Must be between 1 and 30000. Defaults to 5000.
      12. **Latency threshold (ms)**: Optional. If set, the check is considered DOWN when resolving the name takes longer than this value. Leave it unset to only alert on failed resolutions.
      13. **Failure count threshold**: The number of consecutive failures that should occur before the monitor is considered down. Defaults to 1.
      14. **Enabled**: Whether the monitor is enabled or not. If it's disabled, it won't be checked, and **no events will be recorded** for it.
      15. **Metrics history enabled**: Whether metrics history (resolution latency) is recorded for the monitor. Defaults to true.
      16. **Integrations**: A list of integrations to assign to the monitor. The format is `"{integration-type}:{integration-name}"`, where `integration-type` is the type of the integration (e.g. `email`, `slack`, etc.), and `integration-name` is the name of the integration as defined in the `integrations` section of your YAML file. Example: `email:my-email-integration`.
      17. **Category**: An optional, free-form category to group the monitor on the status pages (e.g. a product or service name).

=== "API (expert)"

    This section won't go into details about the API or about exact API calls, since it's **well documented and must be self-explanatory**. You can find more information about the available endpoints and their usage in the [**API documentation**](https://api-docs.kuvasz-uptime.dev){target="_blank"}.

    However, here are **few of the most important** endpoints:

    - `GET /api/v2/dns-monitors` - List all DNS monitors
    - `GET /api/v2/dns-monitors/{id}` - Get a specific DNS monitor by its ID
    - `POST /api/v2/dns-monitors` - Create a new DNS monitor
    - `PATCH /api/v2/dns-monitors/{id}` - Update an existing DNS monitor
    - `DELETE /api/v2/dns-monitors/{id}` - Delete a DNS monitor

## Settings

### Name

<!-- md:version 4.2.0 -->
<!-- md:flag required -->
<!-- md:type string -->
<!-- md:yaml_prop `name` -->

The name of the monitor, which **must be unique** across all DNS monitors.

### Category

<!-- md:version 4.3.0 -->
<!-- md:type string -->
<!-- md:yaml_prop `category` -->

An optional, free-form category (up to 100 characters), e.g. the name of a product or a service, that is used to group the monitor on the [status pages](../features/status-pages.md). Monitors that share the same category are displayed together in a dedicated section there, with an aggregated status per category, and the visitors of the page can filter the monitors by their categories. The default is `null`, which means that the monitor is not categorized.

### Host

<!-- md:version 4.2.0 -->
<!-- md:flag required -->
<!-- md:type string -->
<!-- md:yaml_prop `host` -->

The **domain name to query** (e.g. `example.com`). This is the name the queries are sent for, not the nameserver that answers them - see the [custom resolver](#custom-resolver) for that.

### Uptime check interval

<!-- md:version 4.2.0 -->
<!-- md:flag required -->
<!-- md:type number -->
<!-- md:yaml_prop `uptime-check-interval` -->

The interval **in seconds** at which the uptime checks will be performed. The **minimum value is 5 seconds**.

### Enabled

<!-- md:version 4.2.0 -->
<!-- md:default `true` -->
<!-- md:type boolean -->
<!-- md:yaml_prop `enabled` -->

Whether the monitor is enabled or not. If it's disabled, it won't be checked, and **no events will be recorded** for it.

### Failure count threshold

<!-- md:version 4.2.0 -->
<!-- md:default `1` -->
<!-- md:type number -->
<!-- md:yaml_prop `failure-count-threshold` -->

The number of **consecutive failures** that should occur before the monitor is considered down. Defaults to 1, which means that the monitor will be considered down after the first failure. If you set it to a higher value, for example 3, the monitor will be considered down only after 3 consecutive failures, which can help to reduce false positives in case of temporary network issues or other transient problems.

### Metrics history enabled

<!-- md:version 4.2.0 -->
<!-- md:default `true` -->
<!-- md:type boolean -->
<!-- md:yaml_prop `metrics-history-enabled` -->

Whether the metrics history (resolution latency over time) is enabled or not. If it's disabled, the monitor **won't record the measured metrics**. If you disable it on a monitor that has already recorded metrics history, the **existing history will be deleted**.

### Integrations <!-- md:config ../management/integrations.md -->

<!-- md:version 4.2.0 -->
<!-- md:default empty -->
<!-- md:type list -->
<!-- md:yaml_prop `integrations` -->

A list of **integrations to assign** to the monitor.

If you're using YAML, or the API, the format is `"{type}:{name}"`, where `type` is the alias of the integration (e.g. `email`, `slack`, etc.), and `name` is the name of the integration as defined in the [**`integrations` section of your YAML file**](../management/integrations.md). Example: `email:my-email-integration`.

!!!tip

    You can add/keep **disabled integrations in the list**, but they will not be used for the monitor. This is useful if you want to enable them later without modifying the monitor's configuration.

    **Global integrations** can be explicitly added too, which is handy if you're about to **make them non-global later**, but you want to make sure that they will be assigned to certain monitors even after the change.

## Resolver settings

### Custom resolver

<!-- md:version 4.2.0 -->
<!-- md:default empty -->
<!-- md:type string -->
<!-- md:yaml_prop `resolver-host` -->

The **nameserver to send the queries to**, either as a hostname or as an IP address (e.g. `1.1.1.1`). If you leave it unset, the **system resolver** of the container is used, which is the right choice when you want to monitor the name the way your infrastructure sees it.

Setting it explicitly is useful when you want to **verify a specific nameserver**: your authoritative servers, your internal DNS, or a public resolver like `1.1.1.1` or `8.8.8.8` to see what the outside world gets.

!!! tip "Monitoring multiple resolvers"

    Since a monitor queries exactly one resolver, the way to **compare resolvers** is to create one monitor per resolver with the same host and the same matchers. That also gives you separate incidents, latency history and notifications per resolver.

!!! warning "Prefer an IP address over a hostname"

    A hostname has to be **resolved first**, before the check can send a single query to it - and that lookup goes through the **system resolver**, on **every check**. Two consequences worth knowing about:

    - It is **not covered by the [timeout](#timeout)**. The timeout budget applies to the queries the check sends to your nameserver, not to finding that nameserver in the first place. If the system resolver is slow or unreachable, the check can take considerably longer than `timeout-ms` before it gives up.
    - When it does give up, the monitor goes DOWN with a **timeout or network error from the queries**, not with one naming the resolver - so a broken `resolver-host` looks like a broken monitored name.

    Giving an **IP address** (`1.1.1.1`, `10.0.0.53`, ...) skips the lookup entirely and keeps the whole check inside its budget. Use a hostname only when the address genuinely isn't stable, and keep in mind that you are then also monitoring the system resolver's ability to resolve it.

### Resolver port

<!-- md:version 4.2.0 -->
<!-- md:default `53` -->
<!-- md:type number -->
<!-- md:yaml_prop `resolver-port` -->

The **port of the nameserver**. Must be between 1 and 65535. You only need to change it if your nameserver listens on a non-standard port (e.g. a local resolver on `5353`).

### Transport

<!-- md:version 4.2.0 -->
<!-- md:default `UDP` -->
<!-- md:type enum -->
<!-- md:yaml_prop `transport` -->

The **transport used for the queries**:

| Value | Behavior                                                                                                                |
|-------|-------------------------------------------------------------------------------------------------------------------------|
| `UDP` | Queries are sent over UDP, and are **automatically retried over TCP** when the response comes back truncated. The default, and what a normal resolver does. |
| `TCP` | **Every** query is sent over TCP. Useful when you want to verify that your nameserver accepts TCP queries at all - which it is required to, even though it is often overlooked. |

!!! info "Encrypted transports"

    DNS over TLS/HTTPS/QUIC (`DoT` / `DoH` / `DoQ`) is **not supported yet**.

### Timeout

<!-- md:version 4.2.0 -->
<!-- md:default `5000` -->
<!-- md:type number -->
<!-- md:yaml_prop `timeout-ms` -->

The **timeout of a whole check in milliseconds**. Must be between 1 and 30000. If the resolver doesn't answer within this time, the check is considered a failure.

Note that this is a **budget shared by every query the check makes**, not a per-query limit. A monitor with matchers on three different record types sends three queries, and each of them gets whatever the previous ones left over - so **asserting on more record types never extends the time a check may take**. The [lookups made only for drift detection](#drift-record-types) draw from the same budget, and are made last: a slow assertion phase starves them, which skips the drift comparison for that round instead of letting the check run longer.

The budget covers the queries sent to your nameserver. It does **not** cover looking up the nameserver itself when [`resolver-host`](#custom-resolver) is given as a hostname rather than an IP address - see the warning there.

### Latency threshold

<!-- md:version 4.2.0 -->
<!-- md:default empty -->
<!-- md:type number -->
<!-- md:yaml_prop `latency-threshold-ms` -->

An **optional resolution-latency threshold in milliseconds**. If set, the check is considered DOWN when resolving the name takes longer than this value, even if the answer itself is correct. Leave it unset to only alert when the resolution fails.

This works **together with** the timeout, not instead of it. The [timeout](#timeout) is the hard ceiling that decides whether the name **resolves at all**, while the latency threshold is a lower bar that flags resolutions which succeed but are **too slow**. For example, with `timeout-ms: 5000` and `latency-threshold-ms: 500`, a resolution that takes 800 ms still returns a valid answer, but it breaches your latency SLA, so the monitor is marked DOWN with a dedicated latency error.

The measured latency is the **total wall-clock time of the check's assertion queries**: a monitor asserting on three record types measures all three lookups together, so adding a matcher on a type you didn't query before raises the reading even though nothing got slower. Revisit the threshold when you add matchers. Lookups performed only for [drift detection](#drift-record-types) are deliberately **excluded** from the reading, so widening your drift watch list can never push a monitor over its latency threshold.

!!! warning "Keep it well below the timeout"

    Since the [timeout](#timeout) is a budget for the **whole check**, a check can never measure more latency than the timeout allows: once the budget is gone, the next query fails and the monitor goes DOWN with a **timeout error** rather than a latency error.

    A `latency-threshold-ms` set at or above `timeout-ms` therefore **never fires**. Leave the threshold a comfortable margin below the timeout - and remember that the margin is shared by every record type you assert on.

## Evaluation settings

### Record matchers

<!-- md:version 4.2.0 -->
<!-- md:default empty -->
<!-- md:type list -->
<!-- md:yaml_prop `record-matchers` -->

The **assertions to evaluate** against the resolved records. Each matcher consists of three fields:

| Field         | Values                                                              | Description                                                                               |
|---------------|---------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `record-type` | `A`, `AAAA`, `CNAME`, `MX`, `NS`, `TXT`, `SOA`, `SRV`, `CAA`, `PTR` | The record type the matcher is evaluated against                                          |
| `match-type`  | `EXACT`, `CONTAINS` (default), `REGEX`                              | How the value is compared to the records                                                  |
| `value`       | any non-blank string                                                | The expected value. For `REGEX` it must be a valid [Java / Kotlin regex](#regex-patterns) |

The evaluation rules are the following:

- **Every matcher must pass** - they are ANDed.
- A **single matcher passes if at least one record** of its type satisfies it. This is what makes multi-value answers (round-robin `A` records, multiple `MX` or `NS` entries) work naturally: asserting on one of them doesn't require you to enumerate the rest.
- A matcher whose record type returned **no records at all** fails, and the resulting DOWN event names the failing matchers.
- Only the record types your matchers cover are **actually queried**, one query per distinct type. (An explicit [drift watch list](#drift-record-types) can add further lookups.)
- If the list is **empty**, the check falls back to a plain `A` lookup, and the up/down status is decided by the [response code](#expected-response-code) alone.

!!! warning "An empty answer is not a failure on its own"

    With no matchers, only the **response code** is evaluated. A name that exists but has **no `A` record** answers `NOERROR` with an empty answer section, so such a monitor is **UP** even though nothing was resolved. A name that doesn't exist answers `NXDOMAIN` and is correctly marked DOWN.

    If you want to require that the answer actually contains something, add a matcher that any record satisfies - a `REGEX` matcher with the value `.` is the shortest way, since it matches any non-empty record and fails when there are none.

!!! tip "Requiring *all* of several values"

    Because a matcher passes when *any* record satisfies it, *"the answer must contain **both** `1.2.3.4` and `5.6.7.8`"* is expressed as **two `EXACT` matchers**, one per required value. Each of them has to find its own record, and since all matchers are ANDed, both values must be present.

    This does **not** assert that *nothing else* is in the answer - use [drift detection](#drift-detection) if you want to know about records you didn't expect.

#### Normalization

The returned records and the values of your `EXACT` and `CONTAINS` matchers go through the **same normalization** before they are compared, so you can paste `dig` output verbatim without worrying about formatting details. (A `REGEX` pattern is deliberately left untouched - see [regex patterns](#regex-patterns).) The normalization:

- Records are rendered in their **canonical `dig` presentation format**, which for the multi-field types means:
    - `MX` → `{priority} {exchange}`, e.g. `10 mail.example.com`
    - `SRV` → `{priority} {weight} {port} {target}`, e.g. `10 5 5060 sip.example.com`
    - `CAA` → `{flags} {tag} {value}`, e.g. `0 issue letsencrypt.org`
    - `SOA` → `{mname} {rname} {serial} {refresh} {retry} {expire} {minimum}`
- A **trailing dot is stripped** from the end of the value, so `mail.example.com.` and `mail.example.com` are the same thing. Note that this only applies to the very end: in a multi-field type where a name is *not* the last field - the `mname` and `rname` of an `SOA` - the dot after the name stays, so match those with `CONTAINS` rather than spelling out the whole record.
- Everything is **lowercased**, since DNS names are case-insensitive.
- **Double quotes are removed** and **internal whitespace is collapsed** to single spaces.
- `TXT` records are **rejoined from their 255-byte chunks** into the single logical value they represent. This matters for long records: a DKIM key or a long SPF record is split into chunks on the wire, and comparing them chunk by chunk would make any assertion on them impossible. In _Kuvasz_ you simply assert on the whole value.

#### Regex patterns

`REGEX` matchers use the **Java / Kotlin regular expression syntax** ([`java.util.regex.Pattern`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html){target="_blank"}), which is the Perl-style flavor you know from most languages. Everything that dialect offers is available, including:

- shorthand classes (`\d`, `\w`, `\s`, `\b`) and Unicode properties (`\p{L}`)
- greedy, lazy and possessive quantifiers (`a+`, `a+?`, `a++`) and atomic groups (`(?>…)`)
- alternation, grouping, backreferences and named groups (`(?<label>…)`)
- lookahead and lookbehind (`(?=…)`, `(?<!…)`)

Two dialects it is **not**:

- It is not **POSIX**, and this one bites silently: a bracket expression like `[[:alpha:]]` **does not fail**, it is read as a character class containing the literal characters `:`, `a`, `l`, `p` and `h`. Write `\p{Alpha}` or `[a-z]` instead.
- It is not **PCRE**. PCRE-only extensions such as recursion (`(?R)`) or `\K` don't exist and are rejected by the server when you save the monitor.

The way a pattern is evaluated:

- It is matched **against the [normalized](#normalization) record**, but the pattern itself is **used as-is** - so write it against the normalized form: no trailing dot, no surrounding quotes, single spaces between fields.
- It only has to match **somewhere inside** the record, like a "contains" match. Anchor it with `^` and `$` when you mean the whole record. Multiline mode is off, so those anchors refer to the beginning and the end of the whole record value - which is a single line anyway, since normalization collapses every whitespace character into a space.
- It is always compiled **case-insensitively**, so there is no need for an inline `(?i)` flag. Records are lowercased by the normalization anyway.

!!! warning "Escaping backslashes"

    A regex is a plain string in YAML and JSON, so the quoting style decides whether your backslashes survive:

    - **single-quoted YAML** (recommended) passes them through as-is: `'^[a-z0-9-]+\.example\.com$'`
    - **double-quoted YAML** and **JSON** (the REST API) process escape sequences, so every backslash must be doubled: `"^[a-z0-9-]+\\.example\\.com$"`

    An unescaped `\.` in a double-quoted string is not a literal dot - it may be rejected as an invalid escape or silently become something else.

!!! info "Invalid patterns are rejected when you save"

    A pattern that doesn't compile is refused by every write path (Web UI, REST API, YAML import and YAML bootstrap config), so a typo can never silently turn into an assertion that matches nothing.

    The Web UI additionally pre-checks your pattern **in the browser**, using the JavaScript regex engine, to give you an inline error while you type. The two dialects are nearly identical, but they disagree at the edges in **both directions**:

    - Java-only constructs - possessive quantifiers like `a*+`, atomic groups `(?>…)` - are **valid on the server** while the in-browser check rejects them. Configure those through the API or YAML if you need them.
    - Patterns that JavaScript accepts but Java doesn't - an empty character class `[]`, or a PCRE-ism like `\K` - pass the in-browser check and are then **rejected by the server**, so you get the error on save rather than while typing.

#### Examples

```yaml title="The name must resolve to a specific pair of IPs"
record-matchers:
  - record-type: "A"
    match-type: "EXACT"
    value: "93.184.216.34"
  - record-type: "A"
    match-type: "EXACT"
    value: "93.184.216.35"
```

```yaml title="Mail is intact: the MX points to us and SPF is published"
record-matchers:
  - record-type: "MX"
    match-type: "EXACT"
    value: "10 mail.example.com"
  - record-type: "TXT"
    match-type: "CONTAINS"
    value: "v=spf1"
```

```yaml title="The CNAME still points somewhere inside our CDN account"
record-matchers:
  - record-type: "CNAME"
    match-type: "REGEX"
    # Single-quoted, so the backslashes are taken literally. Anchored, so the
    # whole record must match, not just a part of it.
    value: '^[a-z0-9-]+\.our-cdn\.net$'
```

```yaml title="The same pattern, double-quoted: every backslash has to be doubled"
record-matchers:
  - record-type: "CNAME"
    match-type: "REGEX"
    value: "^[a-z0-9-]+\\.our-cdn\\.net$"
```

```yaml title="Unanchored: the DKIM record must contain an RSA key of any length"
record-matchers:
  - record-type: "TXT"
    match-type: "REGEX"
    value: 'v=dkim1;.*p=[a-z0-9+/]{100,}={0,2}'
```

### Expected response code

<!-- md:version 4.2.0 -->
<!-- md:default `NOERROR` -->
<!-- md:type enum -->
<!-- md:yaml_prop `expected-response-code` -->

The **DNS response code** the resolver is expected to return. The monitor is DOWN whenever the observed code differs from it.

| Value      | Meaning                                                          |
|------------|------------------------------------------------------------------|
| `NOERROR`  | The query was answered successfully. The default.                 |
| `NXDOMAIN` | The name **does not exist**                                       |
| `SERVFAIL` | The nameserver **failed to process** the query                    |
| `REFUSED`  | The nameserver **refused** to answer the query                    |

!!! info "Other response codes"

    Any response code not listed above (`NOTIMP`, `FORMERR`, etc.) is reported as `SERVFAIL`, since from a monitor's point of view they are all the same kind of server-side failure.

!!! warning "Non-`NOERROR` codes require empty matchers"

    Setting this to anything other than `NOERROR` requires the [record matchers](#record-matchers) to be **empty**, and the monitor will be rejected otherwise: you cannot assert on the records of a name that you expect not to resolve.

    This is what makes **negative monitoring** possible: point a monitor at a name that must *not* exist (a decommissioned host, or a wildcard that shouldn't be there) with `expected-response-code: "NXDOMAIN"`, and you'll be notified as soon as it starts resolving.

## Drift detection

### Drift detection enabled

<!-- md:version 4.2.0 -->
<!-- md:default `false` -->
<!-- md:type boolean -->
<!-- md:yaml_prop `drift-detection-enabled` -->

Whether _Kuvasz_ should notify you when the **resolved records change** between two checks.

When enabled, the monitor keeps the **last resolved answer set** as a baseline, and whenever a check returns a different one, a dedicated **`DNS_RECORDS_CHANGED`** notification is sent to the assigned integrations, and the baseline is replaced with the new answer. The stored baseline is also shown on the monitor's detail page in the Web UI, under _Resolved records_.

Some important properties of this mechanism:

- Drift is evaluated **independently of the up/down status**. A monitor can be perfectly UP and still report drift - which is exactly the point, since a hijacked delegation or an accidental record change resolves just fine.
- Drift **never marks a monitor DOWN** and **never creates an incident**. It is a notification-only event, similar in spirit to the HTTP redirect notification.
- The **first successful check is silent**: it only seeds the baseline, since there is nothing to compare against yet.
- A check that **failed or returned a non-`NOERROR` response** is never compared, because an empty answer section would otherwise look like every watched record having disappeared at once. This means drift detection is effectively inert on a monitor whose [expected response code](#expected-response-code) is not `NOERROR`: such a monitor is UP exactly when there are no records to compare.
- Record order **doesn't matter**: answer sets are sorted before comparison, so a resolver rotating its round-robin answers won't trigger a notification.

!!! question "Why it's off by default?"

    Plenty of answer sets **legitimately rotate** - CDN endpoints, short-TTL load balancers, cloud provider IPs. Enabling drift detection on those would only produce noise, so it's an explicit, per-monitor decision.

### Drift record types

<!-- md:version 4.2.0 -->
<!-- md:default empty -->
<!-- md:type list -->
<!-- md:yaml_prop `drift-record-types` -->

The **record types drift detection watches**. Ignored unless [drift detection](#drift-detection-enabled) is enabled.

- When **empty** (the default), drift detection watches exactly the record types your [record matchers](#record-matchers) cover (or `A`, when there are no matchers). This costs **no extra lookups**, since those types are queried anyway.
- When you **name types explicitly**, they **replace** that default: exactly the listed types are watched, whether or not your matchers cover them. This is how a monitor watches something it doesn't assert on - watching `NS` and `MX`, for example, gets you a notification when your delegation or your mail routing changes, without making either of them a reason to mark the monitor DOWN.

Each named type that isn't already covered by your matchers **adds one query per check**. These extra lookups are **not counted** towards the measured latency or the [latency threshold](#latency-threshold), and if one of them fails, only the drift comparison is skipped for that round - the up/down evaluation is unaffected.

They do, however, draw from the check's [timeout budget](#timeout), and they run **after** the assertion queries. A watch list wide enough to exhaust what the assertions left over doesn't slow the check down or endanger its status - it simply means the round ends without a drift comparison. If you see drift notifications go quiet on a monitor with many watched types, raise the timeout.

## Common operations

### Toggling a monitor

You can **enable or disable a monitor** at any time, which is useful if you want to temporarily stop monitoring a specific name without deleting it.

Disabled monitors **won't be counted in the cumulated metrics**, like uptime ratio.

=== "Web UI (recommended)"

    Look for the **toggle switches** with the :material-pause: sign.

=== "YAML (advanced)"

    Set the `enabled` field to `true` or `false` in your YAML file.

    ```yaml hl_lines="4"
    dns-monitors:
    - name: "My DNS Monitor"
      host: "example.com"
      enabled: false
    ```

=== "API (expert)"

    Use the `PATCH /api/v2/dns-monitors/{id}` endpoint to update the `enabled` field of the monitor.

### Deleting a monitor

If you delete a monitor, it will be **removed** from the database, and **all of its recorded events and metrics** (i.e. metrics history, uptime checks, the stored drift baseline, etc.) will be deleted as well. This is a **destructive operation**, so make sure you really want to delete the monitor.

=== "Web UI (recommended)"

    Look for the **delete button** with the :material-trash-can: sign next to the monitor you want to delete.

=== "YAML (advanced)"

    **Remove the monitor from your YAML** file, and then restart _Kuvasz_ to apply the changes.

=== "API (expert)"

    Use the `DELETE /api/v2/dns-monitors/{id}` endpoint to delete the monitor by its ID.

### Modifying the assigned integrations

=== "Web UI (recommended)"

    You can modify the assigned integrations of a monitor by clicking on the **configure button** with the :material-cog: sign on the **monitor's detail page** (look for the _Integrations_ block), where you can add or remove integrations as needed.

=== "YAML (advanced)"

    Modify the `integrations` property of your affected monitor, by adding or removing list items, and then restart _Kuvasz_ to apply the changes.

    ```yaml hl_lines="6"
    dns-monitors:
    - name: "My DNS Monitor"
      host: "example.com"
      uptime-check-interval: 60
      integrations:
        - "slack:devops_channel"
        - "email:my-email-integration"
    ```

=== "API (expert)"

    Use the `PATCH /api/v2/dns-monitors/{id}` endpoint to update the `integrations` field of the monitor. You can add or remove integrations as needed.

    ```json
    {
      "integrations": [
        "email:my-email-integration",
        "slack:my-slack-integration"
      ]
    }
    ```
