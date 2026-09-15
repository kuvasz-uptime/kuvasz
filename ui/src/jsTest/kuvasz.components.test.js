const test = require('node:test');
const assert = require('node:assert/strict');

// kuvasz.js registers a single DOMContentLoaded listener at load time; stub the DOM calls it needs.
// getElementById returns null so resetTomSelectState (called from the HTTP form's populateFrom) no-ops.
globalThis.document = {addEventListener() {}, getElementById() { return null; }};
// resetTomSelectState references TomSelect in an instanceof check; a stub keeps that expression from throwing.
globalThis.TomSelect = class TomSelect {};

const {
    MAINTENANCE_WINDOW_TYPES,
    escapeHtml,
    buildToastMarkup,
    fetchCategories,
    resetCategorySelect,
    resetCategoryMultiSelect,
    monitorListItem,
    statusPageListItem,
    maintenanceWindowListItem,
    upsertHttpMonitorForm,
    upsertPushMonitorForm,
    upsertIcmpMonitorForm,
    upsertTcpMonitorForm,
    upsertDnsMonitorForm,
    upsertStatusPageForm,
    upsertMaintenanceWindowForm,
    httpMetricsBlock,
    icmpMetricsBlock,
    tcpMetricsBlock,
    dnsMetricsBlock,
    buildIncidentAnnotations,
    formatChartTimestamp,
} = require('../main/resources/js/kuvasz.js');

// --------- #1: isValidHttpHeaderName (regex) ---------

test('isValidHttpHeaderName', () => {
    const form = upsertHttpMonitorForm(null, {}, 'category-select', false, 'select', [], 0);
    // Empty / nullish is treated as "no error"
    assert.equal(form.isValidHttpHeaderName(''), true);
    assert.equal(form.isValidHttpHeaderName(null), true);
    assert.equal(form.isValidHttpHeaderName(undefined), true);
    // Valid RFC token characters
    assert.equal(form.isValidHttpHeaderName('Content-Type'), true);
    assert.equal(form.isValidHttpHeaderName('X_Custom-Header123'), true);
    assert.equal(form.isValidHttpHeaderName("X-Weird!#$'*+.^`|~_&%"), true);
    // Spaces, colons and other separators are rejected
    assert.equal(form.isValidHttpHeaderName('Bad Header'), false);
    assert.equal(form.isValidHttpHeaderName('Has:Colon'), false);
    assert.equal(form.isValidHttpHeaderName('semi;colon'), false);
});

// --------- #2: buildRequestBody type -> field mapping (maintenance windows) ---------

test('buildRequestBody nulls out non-MANUAL fields for a MANUAL window', () => {
    const form = upsertMaintenanceWindowForm(null, {}, 'select', []);
    Object.assign(form, {
        type: MAINTENANCE_WINDOW_TYPES.MANUAL,
        name: 'MW', description: 'desc', enabled: true, global: false, showOnStatusPages: true,
        cron: '0 0 * * *', start: '2024-01-01T10:00', duration: 'PT1H',
        selectedMonitors: ['http:1'], integrations: ['slack'],
    });
    const body = form.buildRequestBody();
    assert.equal(body.cron, null);
    assert.equal(body.start, null);
    assert.equal(body.duration, null);
    // Non-type-specific fields are preserved
    assert.equal(body.name, 'MW');
    assert.equal(body.description, 'desc');
    assert.equal(body.showOnStatusPages, true);
    assert.deepEqual(body.monitors, ['http:1']);
    assert.deepEqual(body.integrations, ['slack']);
});

test('buildRequestBody keeps cron and duration but drops start for a CRON window', () => {
    const form = upsertMaintenanceWindowForm(null, {}, 'select', []);
    Object.assign(form, {
        type: MAINTENANCE_WINDOW_TYPES.CRON,
        name: 'MW', cron: '0 0 * * *', start: '2024-01-01T10:00', duration: 'PT2H',
        selectedMonitors: [], integrations: [],
    });
    const body = form.buildRequestBody();
    assert.equal(body.cron, '0 0 * * *');
    assert.equal(body.start, null);
    assert.equal(body.duration, 'PT2H');
});

test('buildRequestBody converts start to ISO and drops cron for a SINGLE window', () => {
    const form = upsertMaintenanceWindowForm(null, {}, 'select', []);
    Object.assign(form, {
        type: MAINTENANCE_WINDOW_TYPES.SINGLE,
        name: 'MW', cron: '0 0 * * *', start: '2024-01-01T10:00', duration: 'PT30M',
        selectedMonitors: [], integrations: [],
    });
    const body = form.buildRequestBody();
    assert.equal(body.cron, null);
    assert.equal(body.duration, 'PT30M');
    // A datetime-local value is converted to an absolute ISO instant
    assert.equal(body.start, new Date('2024-01-01T10:00').toISOString());
});

test('buildRequestBody leaves start null for a SINGLE window without a value', () => {
    const form = upsertMaintenanceWindowForm(null, {}, 'select', []);
    Object.assign(form, {
        type: MAINTENANCE_WINDOW_TYPES.SINGLE, name: 'MW', start: '', duration: 'PT30M',
        selectedMonitors: [], integrations: [],
    });
    assert.equal(form.buildRequestBody().start, null);
});

// --------- #3: metrics blocks ---------

const CHART_LABELS = {
    noData: 'no data',
    incidentStarted: 'Incident started',
    incidentResolved: 'Incident resolved',
    latency: 'Latency',
    packetLoss: 'Packet loss',
};
const MARKER_COLORS = {started: 'red', resolved: 'green'};
const METRICS_LOGS_OF_AN_HOUR = [
    {createdAt: '2024-01-01T00:00:00Z', latencyInMs: 10},
    {createdAt: '2024-01-01T01:00:00Z', latencyInMs: 20},
];
const NOW = Date.parse('2024-01-01T02:00:00Z');
const ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000;

// A metrics block that sees the given moment as the current time
const metricsBlockAt = (factory, now) => {
    const block = factory(1, true, 60, CHART_LABELS, 'PT24H');
    block.markerColors = MARKER_COLORS;
    block.now = () => now;
    return block;
};

const jsonResponse = (body) => ({ok: true, json: async () => body});

// Replaces the global fetch for the duration of a single test
const stubFetch = (t, implementation) => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = implementation;
    t.after(() => {
        globalThis.fetch = originalFetch;
    });
};

test('httpMetricsBlock.transformData maps latency logs to a single series', () => {
    const block = httpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    const result = block.transformData({
        latencyLogs: [
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: '123'},
            {createdAt: '2024-01-01T00:01:00Z', latencyInMs: 200},
        ],
    }, []);
    assert.equal(result.labels.length, 2);
    assert.equal(result.series.length, 1);
    assert.equal(result.series[0].name, 'Latency');
    assert.deepEqual(result.series[0].data, [123, 200]);
    assert.deepEqual(result.annotations, {xaxis: [], points: []});
});

test('icmpMetricsBlock.transformData merges latency and packet loss into a single chart', () => {
    const block = icmpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    const result = block.transformData({
        metricsLogs: [
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: null, packetLossPercentage: '50'},
            {createdAt: '2024-01-01T00:01:00Z', latencyInMs: '80', packetLossPercentage: 0},
        ],
    }, []);
    assert.equal(result.labels.length, 2);
    // Both series share the same labels, but are rendered differently, on an axis of their own
    assert.deepEqual(result.series.map(series => series.name), ['Latency', 'Packet loss']);
    assert.deepEqual(result.series.map(series => series.type), ['area', 'line']);
    // Null latency must stay null (a gap in the chart), not become NaN
    assert.deepEqual(result.series[0].data, [null, 80]);
    assert.deepEqual(result.series[1].data, [50, 0]);
});

test('tcpMetricsBlock.transformData preserves null latency and has no packet-loss series', () => {
    const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    const result = block.transformData({
        metricsLogs: [
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: null},
            {createdAt: '2024-01-01T00:01:00Z', latencyInMs: '80'},
        ],
    }, []);
    // Null latency must stay null (a gap in the chart), not become NaN
    assert.deepEqual(result.series[0].data, [null, 80]);
    assert.equal(result.labels.length, 2);
    // TCP monitors track latency only - there is no packet-loss series
    assert.equal(result.series.length, 1);
});

test('transformData marks the incidents of the whole selected period, not only of the range of the logs', () => {
    const block = metricsBlockAt(tcpMetricsBlock, NOW);
    const result = block.transformData({metricsLogs: METRICS_LOGS_OF_AN_HOUR}, [
        {incidentType: 'TCP', startedAt: '2024-01-01T00:10:00Z', endedAt: '2024-01-01T00:20:00Z', details: null},
        // Resolved before the first log
        {incidentType: 'TCP', startedAt: '2023-12-31T23:00:00Z', endedAt: '2023-12-31T23:30:00Z', details: null},
        // Started after the last log, and still ongoing
        {incidentType: 'TCP', startedAt: '2024-01-01T01:00:00.004Z', endedAt: null, details: null},
        // Started before the selected period, so only its end is marked
        {incidentType: 'TCP', startedAt: '2023-12-31T01:00:00Z', endedAt: '2024-01-01T00:05:00Z', details: null},
    ]);
    assert.deepEqual(result.range, {start: NOW - ONE_DAY_IN_MILLIS, end: NOW});
    assert.deepEqual(
        result.annotations.xaxis.map(annotation => [annotation.x, annotation.borderColor]),
        [
            [Date.parse('2024-01-01T00:10:00Z'), 'red'],
            [Date.parse('2024-01-01T00:20:00Z'), 'green'],
            [Date.parse('2023-12-31T23:00:00Z'), 'red'],
            [Date.parse('2023-12-31T23:30:00Z'), 'green'],
            [Date.parse('2024-01-01T01:00:00.004Z'), 'red'],
            [Date.parse('2024-01-01T00:05:00Z'), 'green'],
        ],
    );
});

test('transformData marks the incidents of an HTTP monitor without any latency logs in the selected period', () => {
    // An HTTP monitor only logs the latency of its successful checks, so there are no logs while it's down
    const block = metricsBlockAt(httpMetricsBlock, NOW);
    const result = block.transformData({latencyLogs: []}, [
        {incidentType: 'HTTP', startedAt: '2023-12-31T12:00:00Z', endedAt: null, details: 'Connection refused'},
    ]);
    assert.deepEqual(result.series[0].data, []);
    assert.deepEqual(result.range, {start: NOW - ONE_DAY_IN_MILLIS, end: NOW});
    assert.deepEqual(result.annotations.points.map(point => point.x), [Date.parse('2023-12-31T12:00:00Z')]);
});

test('transformData extends the range to the data newer than the local clock', () => {
    const block = metricsBlockAt(dnsMetricsBlock, NOW);
    const result = block.transformData({metricsLogs: [{createdAt: '2024-01-01T02:00:01Z', latencyInMs: 5}]}, [
        {incidentType: 'DNS', startedAt: '2024-01-01T02:00:01.004Z', endedAt: '2024-01-01T02:00:02Z', details: null},
    ]);
    assert.deepEqual(result.range, {start: NOW - ONE_DAY_IN_MILLIS, end: Date.parse('2024-01-01T02:00:02Z')});
    assert.equal(result.annotations.points.length, 2);
});

test('transformData displays the selected period up to the current time', () => {
    const block = metricsBlockAt(icmpMetricsBlock, NOW);
    block.period = 'PT720H';
    const result = block.transformData({metricsLogs: []}, []);
    assert.deepEqual(result.range, {start: NOW - 30 * ONE_DAY_IN_MILLIS, end: NOW});
});

test('updateChart spans the time axis over the displayed range', () => {
    const block = metricsBlockAt(tcpMetricsBlock, NOW);
    const updates = [];
    block.chart = {updateOptions: (options) => updates.push(options)};
    const data = block.transformData({metricsLogs: METRICS_LOGS_OF_AN_HOUR}, []);

    block.updateChart(data);

    assert.deepEqual(updates, [{
        labels: data.labels,
        series: data.series,
        annotations: data.annotations,
        xaxis: {min: NOW - ONE_DAY_IN_MILLIS, max: NOW},
    }]);
});

const RANGE_START = Date.parse('2024-01-01T00:00:00Z');
const RANGE_END = Date.parse('2024-01-01T12:00:00Z');
const buildAnnotations = (incidents) =>
    buildIncidentAnnotations(incidents, RANGE_START, RANGE_END, CHART_LABELS, MARKER_COLORS);
const anIncident = (overrides) => ({
    incidentType: 'HTTP',
    startedAt: '2024-01-01T01:00:00Z',
    endedAt: '2024-01-01T02:00:00Z',
    details: null,
    ...overrides,
});

test('buildIncidentAnnotations marks both ends of an incident within the range', () => {
    const result = buildAnnotations([anIncident({details: 'Connection timed out'})]);
    assert.equal(result.xaxis.length, 2);
    assert.equal(result.points.length, 2);

    const [start, end] = result.points;
    // The vertical line and the point carrying the tooltip belong to the same moment
    assert.equal(result.xaxis[0].x, start.x);
    assert.equal(start.x, Date.parse('2024-01-01T01:00:00Z'));
    // The points sit on the zero line of the first axis
    assert.equal(start.y, 0);
    assert.equal(start.yAxisIndex, 0);
    assert.equal(start.marker.fillColor, 'red');
    assert.equal(start.tooltip.enabled, true);
    assert.match(start.tooltip.text, /Incident started/);
    assert.match(start.tooltip.text, /Connection timed out/);

    assert.equal(end.x, Date.parse('2024-01-01T02:00:00Z'));
    assert.equal(end.marker.fillColor, 'green');
    assert.match(end.tooltip.text, /Incident resolved/);
    assert.match(end.tooltip.text, /Connection timed out/);
});

test('buildIncidentAnnotations only marks the end of an incident that started before the range', () => {
    const result = buildAnnotations([anIncident({startedAt: '2023-12-31T23:00:00Z'})]);
    assert.equal(result.points.length, 1);
    assert.equal(result.points[0].marker.fillColor, 'green');
});

test('buildIncidentAnnotations only marks the start of an ongoing incident', () => {
    const result = buildAnnotations([anIncident({endedAt: null})]);
    assert.equal(result.points.length, 1);
    assert.equal(result.points[0].marker.fillColor, 'red');
});

test('buildIncidentAnnotations ignores the incidents outside of the range', () => {
    const result = buildAnnotations([
        anIncident({startedAt: '2024-01-01T13:00:00Z', endedAt: null}),
        anIncident({startedAt: '2023-12-31T10:00:00Z', endedAt: '2023-12-31T11:00:00Z'}),
    ]);
    assert.deepEqual(result, {xaxis: [], points: []});
});

test('buildIncidentAnnotations ignores SSL incidents', () => {
    assert.deepEqual(buildAnnotations([anIncident({incidentType: 'SSL'})]), {xaxis: [], points: []});
});

test('buildIncidentAnnotations escapes the details of an incident in the tooltip', () => {
    const [start] = buildAnnotations([anIncident({details: '<img src=x onerror=alert(1)>'})]).points;
    assert.ok(!start.tooltip.text.includes('<img'));
    assert.ok(start.tooltip.text.includes('&lt;img src=x onerror=alert(1)&gt;'));
});

test('buildIncidentAnnotations leaves the details out of the tooltip of an incident without any', () => {
    const [start] = buildAnnotations([anIncident({details: null})]).points;
    assert.equal(start.tooltip.text.split('<div>').length - 1, 1);
});

test('formatChartTimestamp pads every part of the date to two digits', () => {
    assert.equal(formatChartTimestamp(new Date(2024, 0, 2, 3, 4, 5)), '2024/01/02 03:04:05');
});

test('the metrics blocks fetch their stats and incidents for the selected period', () => {
    const cases = [
        [httpMetricsBlock, 'http-monitors'],
        [icmpMetricsBlock, 'icmp-monitors'],
        [tcpMetricsBlock, 'tcp-monitors'],
        [dnsMetricsBlock, 'dns-monitors'],
    ];
    for (const [factory, statsPath] of cases) {
        const block = factory(42, true, 60, CHART_LABELS, 'PT24H');
        assert.equal(block.statsUrl(), `/api/v2/${statsPath}/42/stats?period=PT24H`);
        assert.equal(block.incidentsUrl(), '/api/v2/incidents?monitorId=42&period=PT24H&includeResolved=true');

        block.period = 'PT168H';
        assert.equal(block.statsUrl(), `/api/v2/${statsPath}/42/stats?period=PT168H`);
        assert.equal(block.incidentsUrl(), '/api/v2/incidents?monitorId=42&period=PT168H&includeResolved=true');
    }
});

test('changing the period drops the previous data and polls the metrics right away', () => {
    const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    const watchers = {};
    let polls = 0;
    Object.assign(block, {
        $watch: (property, callback) => {
            watchers[property] = callback;
        },
        initializeChart() {},
        pollEndpoint() {
            polls++;
        },
    });
    block.init();
    const pollsAfterInit = polls;

    block.previousData = {labels: []};
    block.period = 'PT168H';
    watchers.period();

    assert.equal(block.previousData, null);
    assert.equal(block.isPeriodLoading, true);
    assert.equal(polls, pollsAfterInit + 1);
});

test('pollEndpoint renders the fetched metrics along with the incidents, but only once they change', async (t) => {
    let now = NOW;
    const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    block.now = () => now;
    const rendered = [];
    block.updateChart = (data) => rendered.push(data);
    const stats = {metricsLogs: METRICS_LOGS_OF_AN_HOUR};
    stubFetch(t, async (url) => url.startsWith('/api/v2/incidents')
        ? jsonResponse([{incidentType: 'TCP', startedAt: '2024-01-01T00:30:00Z', endedAt: null, details: null}])
        : jsonResponse(stats));

    await block.pollEndpoint();
    assert.equal(block.lastResponse, stats);
    assert.equal(rendered.length, 1);
    assert.equal(rendered[0].annotations.points.length, 1);

    // The displayed range follows the clock, but that alone doesn't re-render the same data
    now += 60 * 1000;
    await block.pollEndpoint();
    assert.equal(rendered.length, 1);
});

test('pollEndpoint still renders the metrics when the incidents cannot be fetched', async (t) => {
    t.mock.method(console, 'error', () => {});
    const stats = {metricsLogs: METRICS_LOGS_OF_AN_HOUR};
    const failures = [
        async () => ({ok: false, status: 500}),
        async () => {
            throw new Error('network down');
        },
    ];
    for (const failure of failures) {
        const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
        const rendered = [];
        block.updateChart = (data) => rendered.push(data);
        stubFetch(t, async (url) => url.startsWith('/api/v2/incidents') ? failure() : jsonResponse(stats));

        await block.pollEndpoint();

        assert.equal(block.lastResponse, stats);
        assert.equal(rendered.length, 1);
        assert.deepEqual(rendered[0].annotations, {xaxis: [], points: []});
    }
});

test('pollEndpoint renders nothing when the stats cannot be fetched', async (t) => {
    t.mock.method(console, 'error', () => {});
    const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    const rendered = [];
    block.updateChart = (data) => rendered.push(data);
    stubFetch(t, async (url) => url.startsWith('/api/v2/incidents') ? jsonResponse([]) : {ok: false, status: 500});

    await block.pollEndpoint();

    assert.equal(block.lastResponse, null);
    assert.equal(rendered.length, 0);
});

test('pollEndpoint discards the response of a period that has been changed in the meantime', async (t) => {
    const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    const rendered = [];
    block.updateChart = (data) => rendered.push(data);
    let resolveStats;
    stubFetch(t, (url) => url.startsWith('/api/v2/incidents')
        ? Promise.resolve(jsonResponse([]))
        : new Promise(resolve => {
            resolveStats = resolve;
        }));

    const polling = block.pollEndpoint();
    // What the watcher of the period does
    block.period = 'PT168H';
    block.isPeriodLoading = true;
    resolveStats(jsonResponse({metricsLogs: METRICS_LOGS_OF_AN_HOUR}));
    await polling;

    // The loading of the newly selected period is still in progress
    assert.equal(block.isPeriodLoading, true);

    assert.equal(block.lastResponse, null);
    assert.equal(rendered.length, 0);
});

test('changing the period shows the loading state until the response of the new period is handled', async (t) => {
    t.mock.method(console, 'error', () => {});
    const cases = [
        [jsonResponse({metricsLogs: METRICS_LOGS_OF_AN_HOUR}), 1],
        // A failed request must not leave the block loading forever
        [{ok: false, status: 500}, 0],
    ];
    for (const [statsResponse, expectedRenderCount] of cases) {
        const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
        const rendered = [];
        block.updateChart = (data) => rendered.push(data);
        let resolveStats;
        stubFetch(t, (url) => url.startsWith('/api/v2/incidents')
            ? Promise.resolve(jsonResponse([]))
            : new Promise(resolve => {
                resolveStats = resolve;
            }));

        block.period = 'PT168H';
        const refreshing = block.refreshPeriod();
        assert.equal(block.isPeriodLoading, true);

        resolveStats(statsResponse);
        await refreshing;

        assert.equal(block.isPeriodLoading, false);
        assert.equal(rendered.length, expectedRenderCount);
    }
});

test('the auto-refresh never shows the loading state', async (t) => {
    const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    block.updateChart = () => {};
    const loadingStatesDuringRequests = [];
    stubFetch(t, async (url) => {
        loadingStatesDuringRequests.push(block.isPeriodLoading);
        return url.startsWith('/api/v2/incidents')
            ? jsonResponse([])
            : jsonResponse({metricsLogs: METRICS_LOGS_OF_AN_HOUR});
    });

    await block.pollEndpoint();

    assert.deepEqual(loadingStatesDuringRequests, [false, false]);
    assert.equal(block.isPeriodLoading, false);
});

// --------- #4: numeric-boundary validators ---------

// Runs a single validator over a set of [value, expectedError] cases against a freshly built form
const assertValidatorBoundaries = (buildForm, field, method, errorMessage, cases) => {
    for (const [value, expectedError] of cases) {
        const form = buildForm();
        form.errors = {};
        form[field] = value;
        form[method]();
        assert.equal(
            form.errors[field],
            expectedError ? errorMessage : null,
            `${method} with ${field}=${JSON.stringify(value)} expected ${expectedError ? 'error' : 'ok'}`
        );
    }
};

test('ICMP validators enforce their numeric ranges', () => {
    const msgs = {
        packetCountInvalid: 'PC', timeoutSecondsInvalid: 'TS', packetLossThresholdInvalid: 'PL',
    };
    const buildForm = () => upsertIcmpMonitorForm(null, msgs, 'category-select', false, 0);

    // packetCount: valid 1..10
    assertValidatorBoundaries(buildForm, 'packetCount', 'validatePacketCount', 'PC', [
        [0, true], [1, false], [10, false], [11, true], ['', true],
    ]);
    // timeoutSeconds: valid 1..30
    assertValidatorBoundaries(buildForm, 'timeoutSeconds', 'validateTimeoutSeconds', 'TS', [
        [0, true], [1, false], [30, false], [31, true],
    ]);
    // packetLossThreshold: valid 1..100
    assertValidatorBoundaries(buildForm, 'packetLossThreshold', 'validatePacketLossThreshold', 'PL', [
        [0, true], [1, false], [100, false], [101, true],
    ]);
});

test('TCP validators enforce port, timeout and the optional latency threshold', () => {
    const msgs = {
        portInvalid: 'PORT', timeoutMsInvalid: 'TS', latencyThresholdInvalid: 'LT',
    };
    const buildForm = () => upsertTcpMonitorForm(null, msgs, 'category-select', false, 0);

    // port: valid 1..65535
    assertValidatorBoundaries(buildForm, 'port', 'validatePort', 'PORT', [
        [0, true], [1, false], [65535, false], [65536, true], ['', true],
    ]);
    // timeoutMs: valid 1..30000
    assertValidatorBoundaries(buildForm, 'timeoutMs', 'validateTimeoutMs', 'TS', [
        [0, true], [1, false], [30000, false], [30001, true],
    ]);
    // latencyThresholdMs: optional - blank/null is OK, otherwise it must be a positive number
    assertValidatorBoundaries(buildForm, 'latencyThresholdMs', 'validateLatencyThreshold', 'LT', [
        ['', false], [null, false], [0, true], [1, false], [500, false], [-1, true],
    ]);
});

test('Push validators enforce interval, grace period and client secret rules', () => {
    const msgs = {
        heartbeatIntervalInvalid: 'HB', gracePeriodInvalid: 'GP', clientSecretInvalid: 'CS',
    };
    const buildForm = () => upsertPushMonitorForm(null, msgs, 'category-select', false, 0);

    // heartbeatInterval: minimum 10
    assertValidatorBoundaries(buildForm, 'heartbeatInterval', 'validateHeartbeatInterval', 'HB', [
        [9, true], [10, false], [60, false], [0, true],
    ]);
    // gracePeriod: 0 is valid (unlike the other fields), negatives and blanks are not
    assertValidatorBoundaries(buildForm, 'gracePeriod', 'validateGracePeriod', 'GP', [
        [0, false], [5, false], [-1, true], ['', true], [undefined, true],
    ]);
    // clientSecret: at least 36 characters
    assertValidatorBoundaries(buildForm, 'clientSecret', 'validateClientSecret', 'CS', [
        ['x'.repeat(35), true], ['x'.repeat(36), false], [null, true],
    ]);
});

// --------- #5: populateFrom field mapping (shared by reset & clone) ---------

test('HTTP populateFrom copies a source and falls back to defaults', () => {
    const form = upsertHttpMonitorForm(null, {}, 'category-select', false, 'select', [], 0);

    form.populateFrom({
        name: 'Src', url: 'https://example.com', sensitiveUrl: true, sslExpiryThreshold: 14,
        failureCountThreshold: 4, uptimeCheckInterval: 120, sslCheckEnabled: true,
        latencyHistoryEnabled: false, forceNoCache: false, followRedirects: false,
        crossOriginHeaderPropagation: true,
        requestMethod: 'POST', integrations: ['slack'], expectedStatusCodes: [200, 301],
        expectedKeyword: 'ok', expectedKeywordCaseSensitive: true, expectedKeywordNegated: true,
        responseTimeThresholdMillis: 500, requestHeaders: {'X-A': '1'}, expectedHeaders: {'X-B': '2'},
        requestBody: '{"a":1}',
    });
    assert.equal(form.name, 'Src');
    assert.equal(form.url, 'https://example.com');
    assert.equal(form.sensitiveUrl, true);
    assert.equal(form.sslExpiryThreshold, 14);
    assert.equal(form.failureCountThreshold, 4);
    assert.equal(form.uptimeCheckInterval, 120);
    assert.equal(form.sslCheckEnabled, true);
    assert.equal(form.latencyHistoryEnabled, false);
    assert.equal(form.forceNoCache, false);
    assert.equal(form.followRedirects, false);
    assert.equal(form.crossOriginHeaderPropagation, true);
    assert.equal(form.requestMethod, 'POST');
    assert.deepEqual(form.integrations, ['slack']);
    // Status codes are stringified for the TomSelect widget
    assert.deepEqual(form.selectedHttpStatusCodes, ['200', '301']);
    assert.equal(form.expectedKeyword, 'ok');
    assert.equal(form.expectedKeywordCaseSensitive, true);
    assert.equal(form.expectedKeywordNegated, true);
    assert.equal(form.responseTimeThresholdMillis, 500);
    assert.deepEqual(form.requestHeaders, {'X-A': '1'});
    assert.deepEqual(form.expectedHeaders, {'X-B': '2'});
    assert.equal(form.requestBody, '{"a":1}');

    form.populateFrom(null);
    assert.equal(form.name, '');
    assert.equal(form.url, '');
    assert.equal(form.sensitiveUrl, false);
    assert.equal(form.sslExpiryThreshold, 30);
    assert.equal(form.failureCountThreshold, 1);
    assert.equal(form.uptimeCheckInterval, 60);
    assert.equal(form.sslCheckEnabled, false);
    assert.equal(form.latencyHistoryEnabled, true);
    assert.equal(form.forceNoCache, true);
    assert.equal(form.followRedirects, true);
    assert.equal(form.crossOriginHeaderPropagation, false);
    assert.equal(form.requestMethod, 'GET');
    assert.deepEqual(form.integrations, []);
    assert.deepEqual(form.selectedHttpStatusCodes, []);
    assert.equal(form.expectedKeyword, null);
    assert.equal(form.expectedKeywordCaseSensitive, false);
    assert.equal(form.expectedKeywordNegated, false);
    assert.equal(form.responseTimeThresholdMillis, null);
    assert.deepEqual(form.requestHeaders, {});
    assert.deepEqual(form.expectedHeaders, {});
    assert.equal(form.requestBody, null);
});

test('Push populateFrom copies a source and falls back to defaults', () => {
    const form = upsertPushMonitorForm(null, {}, 'category-select', false, 0);

    form.populateFrom({
        name: 'Src', heartbeatInterval: 30, gracePeriod: 5,
        failureCountThreshold: 3, clientSecret: 'secret-value', integrations: ['slack'],
    });
    assert.equal(form.name, 'Src');
    assert.equal(form.heartbeatInterval, 30);
    assert.equal(form.gracePeriod, 5);
    assert.equal(form.failureCountThreshold, 3);
    assert.equal(form.clientSecret, 'secret-value');
    assert.deepEqual(form.integrations, ['slack']);

    // A null source resets to the create-mode defaults; the client secret gets a fresh value
    form.populateFrom(null);
    assert.equal(form.name, '');
    assert.equal(form.heartbeatInterval, 10);
    assert.equal(form.gracePeriod, 0);
    assert.equal(form.failureCountThreshold, 1);
    assert.deepEqual(form.integrations, []);
    assert.equal(typeof form.clientSecret, 'string');
    assert.ok(form.clientSecret.length >= 36);
});

test('ICMP populateFrom copies a source and falls back to defaults', () => {
    const form = upsertIcmpMonitorForm(null, {}, 'category-select', false, 0);

    form.populateFrom({
        name: 'Src', host: 'example.com', uptimeCheckInterval: 120, packetCount: 5,
        timeoutSeconds: 10, packetLossThreshold: 50, failureCountThreshold: 2,
        integrations: ['discord'], metricsHistoryEnabled: false,
    });
    assert.equal(form.name, 'Src');
    assert.equal(form.host, 'example.com');
    assert.equal(form.uptimeCheckInterval, 120);
    assert.equal(form.packetCount, 5);
    assert.equal(form.timeoutSeconds, 10);
    assert.equal(form.packetLossThreshold, 50);
    assert.equal(form.failureCountThreshold, 2);
    assert.deepEqual(form.integrations, ['discord']);
    assert.equal(form.metricsHistoryEnabled, false);

    form.populateFrom(null);
    assert.equal(form.name, '');
    assert.equal(form.host, '');
    assert.equal(form.uptimeCheckInterval, 60);
    assert.equal(form.packetCount, 3);
    assert.equal(form.timeoutSeconds, 5);
    assert.equal(form.packetLossThreshold, 100);
    assert.equal(form.failureCountThreshold, 1);
    assert.deepEqual(form.integrations, []);
    assert.equal(form.metricsHistoryEnabled, true);
});

test('TCP populateFrom copies a source and falls back to defaults', () => {
    const form = upsertTcpMonitorForm(null, {}, 'category-select', false, 0);

    form.populateFrom({
        name: 'Src', host: 'example.com', port: 5432, uptimeCheckInterval: 120,
        timeoutMs: 10000, latencyThresholdMs: 250, failureCountThreshold: 2,
        integrations: ['discord'], metricsHistoryEnabled: false,
    });
    assert.equal(form.name, 'Src');
    assert.equal(form.host, 'example.com');
    assert.equal(form.port, 5432);
    assert.equal(form.uptimeCheckInterval, 120);
    assert.equal(form.timeoutMs, 10000);
    assert.equal(form.latencyThresholdMs, 250);
    assert.equal(form.failureCountThreshold, 2);
    assert.deepEqual(form.integrations, ['discord']);
    assert.equal(form.metricsHistoryEnabled, false);

    form.populateFrom(null);
    assert.equal(form.name, '');
    assert.equal(form.host, '');
    assert.equal(form.port, '');
    assert.equal(form.uptimeCheckInterval, 60);
    assert.equal(form.timeoutMs, 5000);
    // The optional latency threshold falls back to an empty string, not a number
    assert.equal(form.latencyThresholdMs, '');
    assert.equal(form.failureCountThreshold, 1);
    assert.deepEqual(form.integrations, []);
    assert.equal(form.metricsHistoryEnabled, true);
});

// --------- DNS: metrics block ---------

test('dnsMetricsBlock.transformData preserves null latency and has no packet-loss series', () => {
    const block = dnsMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    const result = block.transformData({
        metricsLogs: [
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: null},
            {createdAt: '2024-01-01T00:01:00Z', latencyInMs: '42'},
        ],
    }, []);
    // Null latency must stay null (a gap in the chart), not become NaN
    assert.deepEqual(result.series[0].data, [null, 42]);
    assert.equal(result.labels.length, 2);
    // DNS monitors track latency only - there is no packet-loss series
    assert.equal(result.series.length, 1);
});

// --------- DNS: numeric-boundary validators ---------

test('DNS validators enforce resolver port, timeout and the optional latency threshold', () => {
    const msgs = {
        resolverPortInvalid: 'PORT', timeoutMsInvalid: 'TS', latencyThresholdInvalid: 'LT',
    };
    const buildForm = () => upsertDnsMonitorForm(null, msgs, 'category-select', false, 0);

    // resolverPort: valid 1..65535
    assertValidatorBoundaries(buildForm, 'resolverPort', 'validateResolverPort', 'PORT', [
        [0, true], [1, false], [65535, false], [65536, true], ['', true],
    ]);
    // timeoutMs: valid 1..30000
    assertValidatorBoundaries(buildForm, 'timeoutMs', 'validateTimeoutMs', 'TS', [
        [0, true], [1, false], [30000, false], [30001, true],
    ]);
    // latencyThresholdMs: optional - blank/null is OK, otherwise it must be a positive number
    assertValidatorBoundaries(buildForm, 'latencyThresholdMs', 'validateLatencyThreshold', 'LT', [
        ['', false], [null, false], [0, true], [1, false], [500, false], [-1, true],
    ]);
});

// --------- DNS: record matcher add/remove + regex validation ---------

test('DNS validateNewMatcher rejects blank values and invalid regex, gates isMatcherAddable', () => {
    const form = upsertDnsMonitorForm(null, {recordMatcherInvalid: 'RM'}, 'category-select', false, 0);
    form.init();

    // Blank value is not addable, but not an error either
    form.newMatcherValue = '   ';
    form.validateNewMatcher();
    assert.equal(form.isMatcherAddable, false);
    assert.equal(form.errors.newMatcher, null);

    // A non-blank CONTAINS value is addable
    form.newMatcherMatchType = 'CONTAINS';
    form.newMatcherValue = '1.2.3.4';
    form.validateNewMatcher();
    assert.equal(form.isMatcherAddable, true);
    assert.equal(form.errors.newMatcher, null);

    // An invalid REGEX value is flagged and not addable
    form.newMatcherMatchType = 'REGEX';
    form.newMatcherValue = '([';
    form.validateNewMatcher();
    assert.equal(form.isMatcherAddable, false);
    assert.equal(form.errors.newMatcher, 'RM');

    // A valid REGEX value is addable again
    form.newMatcherValue = '^mail\\..*';
    form.validateNewMatcher();
    assert.equal(form.isMatcherAddable, true);
    assert.equal(form.errors.newMatcher, null);
});

test('DNS addMatcher/removeMatcher mutate the recordMatchers list', () => {
    const form = upsertDnsMonitorForm(null, {}, 'category-select', false, 0);
    form.init();

    form.newMatcherRecordType = 'A';
    form.newMatcherMatchType = 'EXACT';
    form.newMatcherValue = ' 1.2.3.4 ';
    form.addMatcher();

    assert.equal(form.recordMatchers.length, 1);
    assert.deepEqual(form.recordMatchers[0], {recordType: 'A', matchType: 'EXACT', value: '1.2.3.4'});
    // The add-row value is cleared after a successful add
    assert.equal(form.newMatcherValue, '');
    assert.equal(form.isMatcherAddable, false);

    // A blank value cannot be added
    form.newMatcherValue = '';
    form.addMatcher();
    assert.equal(form.recordMatchers.length, 1);

    form.removeMatcher(0);
    assert.equal(form.recordMatchers.length, 0);
});

test('DNS addMatcher ignores a matcher that is already in the list', () => {
    const form = upsertDnsMonitorForm(null, {}, 'category-select', false, 0);
    form.init();

    const add = (recordType, matchType, value) => {
        form.newMatcherRecordType = recordType;
        form.newMatcherMatchType = matchType;
        form.newMatcherValue = value;
        form.addMatcher();
    };

    add('A', 'EXACT', '1.2.3.4');
    // The very same matcher would only be evaluated twice, and the server drops it anyway
    add('A', 'EXACT', '1.2.3.4');
    assert.equal(form.recordMatchers.length, 1);
    // The entry row is cleared either way, so the form does not look stuck
    assert.equal(form.newMatcherValue, '');

    // Differing in any single field still makes it a new matcher
    add('A', 'CONTAINS', '1.2.3.4');
    add('AAAA', 'EXACT', '1.2.3.4');
    add('A', 'EXACT', '5.6.7.8');
    assert.equal(form.recordMatchers.length, 4);
});

test('DNS validateResponseCodeMatchers conflicts when a non-NOERROR code has matchers', () => {
    const form = upsertDnsMonitorForm(null, {responseCodeMatchersConflict: 'CONFLICT'}, 'category-select', false, 0);
    form.init();

    // NOERROR + matchers is fine
    form.expectedResponseCode = 'NOERROR';
    form.recordMatchers = [{recordType: 'A', matchType: 'EXACT', value: '1.2.3.4'}];
    form.validateResponseCodeMatchers();
    assert.equal(form.errors.recordMatchers, null);

    // NXDOMAIN + matchers is a conflict
    form.expectedResponseCode = 'NXDOMAIN';
    form.validateResponseCodeMatchers();
    assert.equal(form.errors.recordMatchers, 'CONFLICT');

    // NXDOMAIN without matchers is fine
    form.recordMatchers = [];
    form.validateResponseCodeMatchers();
    assert.equal(form.errors.recordMatchers, null);
});

// --------- DNS: populateFrom field mapping ---------

test('DNS populateFrom copies a source and falls back to defaults', () => {
    const form = upsertDnsMonitorForm(null, {}, 'category-select', false, 0);

    form.populateFrom({
        name: 'Src', host: 'example.com', resolverHost: '8.8.8.8', resolverPort: 5353,
        transport: 'TCP', recordMatchers: [{recordType: 'A', matchType: 'EXACT', value: '1.2.3.4'}],
        expectedResponseCode: 'NXDOMAIN', driftDetectionEnabled: true, driftRecordTypes: ['A', 'MX'],
        uptimeCheckInterval: 120, timeoutMs: 10000, latencyThresholdMs: 250, failureCountThreshold: 2,
        integrations: ['discord'], metricsHistoryEnabled: false,
    });
    assert.equal(form.name, 'Src');
    assert.equal(form.host, 'example.com');
    assert.equal(form.resolverHost, '8.8.8.8');
    assert.equal(form.resolverPort, 5353);
    assert.equal(form.transport, 'TCP');
    assert.deepEqual(form.recordMatchers, [{recordType: 'A', matchType: 'EXACT', value: '1.2.3.4'}]);
    assert.equal(form.expectedResponseCode, 'NXDOMAIN');
    assert.equal(form.driftDetectionEnabled, true);
    assert.deepEqual(form.driftRecordTypes, ['A', 'MX']);
    assert.equal(form.uptimeCheckInterval, 120);
    assert.equal(form.timeoutMs, 10000);
    assert.equal(form.latencyThresholdMs, 250);
    assert.equal(form.failureCountThreshold, 2);
    assert.deepEqual(form.integrations, ['discord']);
    assert.equal(form.metricsHistoryEnabled, false);

    // recordMatchers is deep-copied, not aliased to the source array
    form.recordMatchers.push({recordType: 'AAAA', matchType: 'CONTAINS', value: '::1'});
    form.populateFrom(null);
    assert.equal(form.name, '');
    assert.equal(form.host, '');
    assert.equal(form.resolverHost, '');
    assert.equal(form.resolverPort, 53);
    assert.equal(form.transport, 'UDP');
    assert.deepEqual(form.recordMatchers, []);
    assert.equal(form.expectedResponseCode, 'NOERROR');
    assert.equal(form.driftDetectionEnabled, false);
    assert.deepEqual(form.driftRecordTypes, []);
    assert.equal(form.uptimeCheckInterval, 60);
    assert.equal(form.timeoutMs, 5000);
    assert.equal(form.latencyThresholdMs, '');
    assert.equal(form.failureCountThreshold, 1);
    assert.deepEqual(form.integrations, []);
    assert.equal(form.metricsHistoryEnabled, true);
});

// --------- buildToastMarkup ---------

test('buildToastMarkup renders a plain Tabler toast carrying its state in a status dot', () => {
    const markup = buildToastMarkup('slack:alpha', 'Test notification sent', 'status-green', true);

    // Tabler only styles the plain toast, so no Bootstrap background utility may leak into the markup
    assert.match(markup, /class="toast fade"/);
    assert.ok(!markup.includes('bg-success'));
    assert.ok(!markup.includes('bg-danger'));
    // The outcome is carried by the status dot instead
    assert.match(markup, /<span class="status-dot status-green me-2"><\/span>/);
    assert.match(markup, /<strong class="me-auto">slack:alpha<\/strong>/);
    assert.match(markup, /<div class="toast-body">Test notification sent<\/div>/);
});

test('buildToastMarkup only auto-dismisses when asked to', () => {
    const autoHiding = buildToastMarkup('h', 'c', 'status-green', true);
    assert.match(autoHiding, /data-bs-autohide="true"/);
    assert.match(autoHiding, /data-bs-delay="3000"/);

    // Errors stay on screen until dismissed, so they carry no delay
    const sticky = buildToastMarkup('h', 'c', 'status-red', false);
    assert.match(sticky, /data-bs-autohide="false"/);
    assert.ok(!sticky.includes('data-bs-delay'));
    assert.match(sticky, /status-dot status-red/);
});

test('buildToastMarkup escapes the remote content it renders', () => {
    // The body of a failed integration test carries the error of the remote endpoint, so it must not be able to
    // inject markup into the toast that ends up in innerHTML
    const markup = buildToastMarkup(
        '<img src=x onerror="alert(1)">',
        "Unexpected response: <script>alert('xss')</script>",
        'status-red',
        false,
    );

    assert.ok(!markup.includes('<img'));
    assert.ok(!markup.includes('<script>'));
    assert.match(markup, /&lt;img src=x onerror=&quot;alert\(1\)&quot;&gt;/);
    assert.match(markup, /&lt;script&gt;alert\(&#39;xss&#39;\)&lt;\/script&gt;/);
});

// --------- escapeHtml ---------

test('escapeHtml escapes every HTML special character and handles missing values', () => {
    assert.equal(escapeHtml(`&<>"'`), '&amp;&lt;&gt;&quot;&#39;');
    // Ampersands are escaped first, so an already escaped entity is not double-decoded on render
    assert.equal(escapeHtml('a &amp; b'), 'a &amp;amp; b');
    assert.equal(escapeHtml('plain text'), 'plain text');
    assert.equal(escapeHtml(null), '');
    assert.equal(escapeHtml(undefined), '');
    assert.equal(escapeHtml(42), '42');
});

// --------- Category on the monitor upsert forms ---------

test('monitor forms populate and reset the category', () => {
    const forms = [
        upsertHttpMonitorForm(null, {}, 'category-select', false, 'select', [], 0),
        upsertPushMonitorForm(null, {}, 'category-select', false, 0),
        upsertIcmpMonitorForm(null, {}, 'category-select', false, 0),
        upsertTcpMonitorForm(null, {}, 'category-select', false, 0),
        upsertDnsMonitorForm(null, {}, 'category-select', false, 0),
    ];
    forms.forEach((form) => {
        form.populateFrom({category: 'Drive storage'});
        assert.equal(form.category, 'Drive storage');
        form.populateFrom(null);
        assert.equal(form.category, null);
    });
});

test('validateCategory flags categories longer than 100 characters', () => {
    const form = upsertHttpMonitorForm(null, {categoryTooLong: 'too long'}, 'category-select', false, 'select', [], 0);
    form.populateFrom(null);
    form.category = 'a'.repeat(101);
    form.validateCategory();
    assert.equal(form.errors.category, 'too long');
    form.category = 'a'.repeat(100);
    form.validateCategory();
    assert.equal(form.errors.category, null);
    form.category = null;
    form.validateCategory();
    assert.equal(form.errors.category, null);
});

// --------- The category select (TomSelect) ---------

// A TomSelect stub recording what the helpers do to it, standing in for the real widget Node has no DOM for.
const fakeTomSelect = () => {
    const instance = new globalThis.TomSelect();
    instance.options = [];
    instance.value = null;
    instance.cleared = 0;
    instance.clear = () => {
        instance.cleared += 1;
        instance.value = null;
    };
    instance.items = [];
    instance.addOption = (option) => instance.options.push(option);
    instance.setValue = (value) => {
        instance.value = value;
    };
    instance.addItem = (value) => instance.items.push(value);
    return instance;
};

// Runs [body] with `document.getElementById` returning an element carrying [tomSelectInstance].
const withCategorySelect = (tomSelectInstance, body) => {
    const originalGetElementById = globalThis.document.getElementById;
    globalThis.document.getElementById = () => ({tomselect: tomSelectInstance});
    try {
        body();
    } finally {
        globalThis.document.getElementById = originalGetElementById;
    }
};

test('resetCategorySelect re-adds and selects the category of the form', () => {
    const tomSelect = fakeTomSelect();
    withCategorySelect(tomSelect, () => resetCategorySelect('category-select', 'Drive storage'));
    assert.equal(tomSelect.cleared, 1);
    // The option has to be re-added, a non-persisted category is dropped when the selection is cleared
    assert.deepEqual(tomSelect.options, [{value: 'Drive storage', text: 'Drive storage'}]);
    assert.equal(tomSelect.value, 'Drive storage');
});

test('resetCategorySelect only clears when the form has no category', () => {
    const tomSelect = fakeTomSelect();
    withCategorySelect(tomSelect, () => resetCategorySelect('category-select', null));
    assert.equal(tomSelect.cleared, 1);
    assert.deepEqual(tomSelect.options, []);
    assert.equal(tomSelect.value, null);
});

test('resetCategorySelect no-ops before TomSelect is initialized', () => {
    // `document.getElementById` returns null in this harness, which is what an unrendered modal looks like
    assert.doesNotThrow(() => resetCategorySelect('category-select', 'Drive storage'));
});

test('resetCategoryMultiSelect re-adds and selects every category of the form', () => {
    const tomSelect = fakeTomSelect();
    withCategorySelect(tomSelect, () => resetCategoryMultiSelect('categories-select', ['Payments', 'Search']));
    assert.equal(tomSelect.cleared, 1);
    // Same reason as above: an option the endpoint does not offer is dropped when the selection is cleared
    assert.deepEqual(tomSelect.options, [
        {value: 'Payments', text: 'Payments'},
        {value: 'Search', text: 'Search'},
    ]);
    assert.deepEqual(tomSelect.items, ['Payments', 'Search']);
});

test('resetCategoryMultiSelect only clears when the form has no categories', () => {
    const tomSelect = fakeTomSelect();
    withCategorySelect(tomSelect, () => resetCategoryMultiSelect('categories-select', []));
    assert.equal(tomSelect.cleared, 1);
    assert.deepEqual(tomSelect.options, []);
    assert.deepEqual(tomSelect.items, []);
});

test('resetCategoryMultiSelect tolerates a missing category list', () => {
    const tomSelect = fakeTomSelect();
    withCategorySelect(tomSelect, () => resetCategoryMultiSelect('categories-select', undefined));
    assert.equal(tomSelect.cleared, 1);
    assert.deepEqual(tomSelect.items, []);
});

// Runs [body] with a stubbed global `fetch`, restoring the original afterwards.
const withFetch = async (stub, body) => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = stub;
    try {
        return await body();
    } finally {
        globalThis.fetch = originalFetch;
    }
};

test('fetchCategories returns the categories of a successful response', async () => {
    const categories = await withFetch(
        async () => ({ok: true, json: async () => ['alerting', 'Payments']}),
        () => fetchCategories(),
    );
    assert.deepEqual(categories, ['alerting', 'Payments']);
});

test('fetchCategories fails open on an error response', async () => {
    const categories = await withFetch(
        async () => ({ok: false, json: async () => ['alerting']}),
        () => fetchCategories(),
    );
    assert.deepEqual(categories, []);
});

test('fetchCategories fails open when the request throws', async () => {
    const originalConsoleError = console.error;
    console.error = () => {};
    try {
        const categories = await withFetch(
            async () => { throw new Error('network down'); },
            () => fetchCategories(),
        );
        assert.deepEqual(categories, []);
    } finally {
        console.error = originalConsoleError;
    }
});

// --------- Upsert forms ---------

// Stubs the browser globals of a submitted form, recording the navigation and the alerts
const stubBrowser = (t) => {
    const browser = {location: {href: null, reloaded: false, reload() { this.reloaded = true; }}, alerts: []};
    const originalWindow = globalThis.window;
    const originalAlert = globalThis.alert;
    globalThis.window = browser;
    globalThis.alert = (message) => browser.alerts.push(message);
    t.mock.method(console, 'error', () => {});
    t.after(() => {
        globalThis.window = originalWindow;
        globalThis.alert = originalAlert;
    });
    return browser;
};

// Stubs fetch with the given responses (or errors to throw) in order, recording the requests
const stubRequests = (t, ...responses) => {
    const requests = [];
    stubFetch(t, async (url, init) => {
        requests.push({url, method: init.method, body: init.body && JSON.parse(init.body)});
        const response = responses.shift();
        if (response instanceof Error) throw response;
        return response;
    });
    return requests;
};

// Stubs fetch with requests that stay pending until the test resolves them
const stubPendingRequests = (t) => {
    const pending = [];
    stubFetch(t, (url, init) => new Promise(resolve => pending.push({url, method: init.method, resolve})));
    return pending;
};

const errorResponse = (status, body) => ({ok: false, status, statusText: 'Error', json: async () => body});

test('upsert creates a monitor and redirects to its page', async (t) => {
    const browser = stubBrowser(t);
    const requests = stubRequests(t, jsonResponse({id: 42}));
    const form = upsertIcmpMonitorForm(null, {}, 'category-select', false, 0);
    form.populateFrom({name: 'ping', host: 'example.com', category: ' '});

    await form.upsert();

    assert.equal(requests.length, 1);
    assert.equal(requests[0].url, '/api/v2/icmp-monitors');
    assert.equal(requests[0].method, 'POST');
    assert.deepEqual(requests[0].body, {
        name: 'ping', failureCountThreshold: 1, integrations: [], category: null, host: 'example.com',
        uptimeCheckInterval: 60, packetCount: 3, timeoutSeconds: 5, packetLossThreshold: 100,
        metricsHistoryEnabled: true, enabled: true,
    });
    assert.equal(browser.location.href, '/icmp-monitors/42');
    assert.equal(form.isRequestLoading, false);
});

test('upsert updates an existing monitor without touching its enabled state and reloads the page', async (t) => {
    const browser = stubBrowser(t);
    let isBodyRead = false;
    const requests = stubRequests(t, {ok: true, json: async () => { isBodyRead = true; return {id: 7}; }});
    const form = upsertTcpMonitorForm({id: 7, name: 'db', host: 'db.local', port: '5432'}, {}, 'category-select', false, 0);
    form.resetState();

    await form.upsert();

    assert.equal(requests[0].url, '/api/v2/tcp-monitors/7');
    assert.equal(requests[0].method, 'PATCH');
    assert.equal('enabled' in requests[0].body, false);
    assert.equal(requests[0].body.port, 5432);
    assert.equal(requests[0].body.latencyThresholdMs, null);
    assert.equal(isBodyRead, true);
    assert.equal(browser.location.reloaded, true);
    assert.equal(browser.location.href, null);
});

test('upsert flags the conflicting fields of each entity', async (t) => {
    stubBrowser(t);
    stubRequests(t, errorResponse(409), errorResponse(409), errorResponse(409));
    const messages = {
        nameAlreadyExists: 'NAME', nameOrClientSecretAlreadyExists: 'NAME_OR_SECRET', slugAlreadyExists: 'SLUG',
    };
    const dnsForm = upsertDnsMonitorForm(null, messages, 'category-select', false, 0);
    const pushForm = upsertPushMonitorForm(null, messages, 'category-select', false, 0);
    const statusPageForm = upsertStatusPageForm(null, messages, 'monitor-select', [], 'categories-select');

    for (const form of [dnsForm, pushForm, statusPageForm]) {
        form.resetState();
        await form.upsert();
        assert.equal(form.isRequestLoading, false);
    }

    assert.deepEqual(dnsForm.errors, {name: 'NAME'});
    assert.deepEqual(pushForm.errors, {name: 'NAME_OR_SECRET', clientSecret: 'NAME_OR_SECRET'});
    assert.deepEqual(statusPageForm.errors, {slug: 'SLUG'});
});

test('upsert shows the rejected name change of a monitor on its field, every other bad request on the form', async (t) => {
    stubBrowser(t);
    stubRequests(
        t,
        errorResponse(400, {errorCode: 'MONITOR_NAME_CANNOT_BE_CHANGED', message: 'immutable'}),
        errorResponse(400, {errorCode: 'VALIDATION_ERROR', message: 'invalid monitor'}),
        errorResponse(400, {message: 'invalid window'}),
    );
    const messages = {nameCannotBeChanged: 'IMMUTABLE'};
    const monitorForm = upsertHttpMonitorForm({id: 1, name: 'site'}, messages, 'category-select', false, 'select', [], 0);
    const maintenanceWindowForm = upsertMaintenanceWindowForm(null, messages, 'select', []);

    monitorForm.resetState();
    await monitorForm.upsert();
    assert.deepEqual(monitorForm.errors, {name: 'IMMUTABLE'});
    assert.equal(monitorForm.formError, null);

    monitorForm.resetState();
    await monitorForm.upsert();
    assert.deepEqual(monitorForm.errors, {});
    assert.equal(monitorForm.formError, 'invalid monitor');

    maintenanceWindowForm.resetState();
    await maintenanceWindowForm.upsert();
    assert.deepEqual(maintenanceWindowForm.errors, {});
    assert.equal(maintenanceWindowForm.formError, 'invalid window');
});

test('upsert alerts on an unexpected response and on a failed request', async (t) => {
    const browser = stubBrowser(t);
    stubRequests(t, errorResponse(500), new Error('network down'));
    const form = upsertStatusPageForm({id: 2, title: 'Status', slug: 'status'}, {}, 'monitor-select', [], 'categories-select');
    form.resetState();

    await form.upsert();
    await form.upsert();

    assert.equal(browser.alerts.length, 2);
    assert.ok(browser.alerts.every(alert => alert.includes('status page')));
    assert.deepEqual(form.errors, {});
    assert.equal(form.isRequestLoading, false);
    assert.equal(browser.location.reloaded, false);
});

test('submitForm only sends a valid form', async (t) => {
    stubBrowser(t);
    const requests = stubRequests(t, jsonResponse({id: 3}), jsonResponse({id: 4}));
    const monitorForm = upsertIcmpMonitorForm(null, {nameRequired: 'NAME', hostRequired: 'HOST'}, 'category-select', false, 0);
    const maintenanceWindowForm = upsertMaintenanceWindowForm(null, {nameRequired: 'NAME'}, 'select', []);

    monitorForm.resetState();
    monitorForm.submitForm();
    assert.equal(requests.length, 0);
    assert.equal(monitorForm.errors.name, 'NAME');
    assert.equal(monitorForm.errors.host, 'HOST');

    Object.assign(monitorForm, {name: 'ping', host: 'example.com'});
    monitorForm.submitForm();
    await new Promise(setImmediate);
    assert.equal(requests.length, 1);

    maintenanceWindowForm.resetState();
    await maintenanceWindowForm.submitForm();
    assert.equal(requests.length, 1);
    assert.equal(maintenanceWindowForm.errors.name, 'NAME');

    maintenanceWindowForm.name = 'Upgrade';
    await maintenanceWindowForm.submitForm();
    await new Promise(setImmediate);
    assert.equal(requests.length, 2);
    assert.equal(requests[1].url, '/api/v2/maintenance-windows');
});

test('cloneFrom copies the source monitor under the new name, with a fresh client secret for push monitors', async (t) => {
    const pushSource = {name: 'job', heartbeatInterval: 30, clientSecret: 'x'.repeat(36), category: 'Jobs'};
    const icmpSource = {name: 'ping', host: 'example.com', packetCount: 5};
    const requests = stubRequests(t, jsonResponse(pushSource), jsonResponse(icmpSource));
    const pushForm = upsertPushMonitorForm(null, {}, 'category-select', false, 0);
    const icmpForm = upsertIcmpMonitorForm(null, {}, 'category-select', false, 0);

    pushForm.cloneFrom(5, 'job (copy)');
    assert.equal(pushForm.isLoadingEntity, true);
    await new Promise(setImmediate);
    icmpForm.cloneFrom(6, 'ping (copy)');
    await new Promise(setImmediate);

    assert.deepEqual(requests.map(request => request.url), ['/api/v2/push-monitors/5', '/api/v2/icmp-monitors/6']);
    assert.equal(pushForm.isLoadingEntity, false);
    assert.equal(pushForm.name, 'job (copy)');
    assert.equal(pushForm.heartbeatInterval, 30);
    assert.equal(pushForm.category, 'Jobs');
    assert.notEqual(pushForm.clientSecret, pushSource.clientSecret);
    assert.equal(icmpForm.name, 'ping (copy)');
    assert.equal(icmpForm.host, 'example.com');
    assert.equal(icmpForm.packetCount, 5);
});

test('HTTP validators enforce the name, SSL expiry, interval, failure count and response time rules', () => {
    const msgs = {
        nameRequired: 'N', sslExpiryThresholdInvalid: 'SSL', uptimeCheckIntervalInvalid: 'UI',
        failureCountThresholdInvalid: 'FC', responseTimeThresholdInvalid: 'RT',
    };
    const buildForm = () => upsertHttpMonitorForm(null, msgs, 'category-select', false, 'select', [], 0);

    assertValidatorBoundaries(buildForm, 'name', 'validateName', 'N', [
        ['', true], ['   ', true], [null, true], ['site', false],
    ]);
    assertValidatorBoundaries(buildForm, 'sslExpiryThreshold', 'validateSslExpiryThreshold', 'SSL', [
        [0, true], [-1, true], ['', true], [1, false],
    ]);
    assertValidatorBoundaries(buildForm, 'uptimeCheckInterval', 'validateUptimeCheckInterval', 'UI', [
        [4, true], [5, false], ['abc', true],
    ]);
    assertValidatorBoundaries(buildForm, 'failureCountThreshold', 'validateFailureCountThreshold', 'FC', [
        [0, true], [1, false], ['abc', true],
    ]);
    // responseTimeThresholdMillis: optional (null), otherwise valid 1..30000
    assertValidatorBoundaries(buildForm, 'responseTimeThresholdMillis', 'validateResponseTimeThreshold', 'RT', [
        [null, false], [0, true], [1, false], [30000, false], [30001, true], ['', true],
    ]);
});

test('HTTP submitForm keeps an invalid request body on its field instead of sending it', async (t) => {
    stubBrowser(t);
    const requests = stubRequests(t, jsonResponse({id: 8}));
    const form = upsertHttpMonitorForm(null, {requestBodyInvalid: 'JSON'}, 'category-select', false, 'select', [], 0);
    form.resetState();
    Object.assign(form, {name: 'api', url: 'https://example.com', requestBody: '{"key": '});

    form.submitForm();
    assert.equal(requests.length, 0);
    assert.equal(form.errors.requestBody, 'JSON');

    form.requestBody = '{"key": "value"}';
    form.submitForm();
    await new Promise(setImmediate);
    assert.equal(requests.length, 1);
    assert.equal(requests[0].body.requestBody, '{"key": "value"}');
});

// --------- Editing a monitor through the shared upsert modal of a list ---------

const monitorFormFactories = {
    http: (monitor = null, isNameLocked = false) =>
        upsertHttpMonitorForm(monitor, {}, 'category-select', isNameLocked, 'select', [], 0),
    push: (monitor = null, isNameLocked = false) => upsertPushMonitorForm(monitor, {}, 'category-select', isNameLocked, 0),
    icmp: (monitor = null, isNameLocked = false) => upsertIcmpMonitorForm(monitor, {}, 'category-select', isNameLocked, 0),
    tcp: (monitor = null, isNameLocked = false) => upsertTcpMonitorForm(monitor, {}, 'category-select', isNameLocked, 0),
    dns: (monitor = null, isNameLocked = false) => upsertDnsMonitorForm(monitor, {}, 'category-select', isNameLocked, 0),
};

const sourceMonitor = {id: 7, name: 'Source', category: 'Payments', clientSecret: 'x'.repeat(36)};

const methodsAndUrlsOf = (requests) => requests.map(({method, url}) => [method, url]);

Object.entries(monitorFormFactories).forEach(([type, createForm]) => {
    test(`${type} editFrom loads the monitor into update mode and saves it with a PATCH, reloading the page`, async (t) => {
        const browser = stubBrowser(t);
        const requests = stubRequests(t, jsonResponse(sourceMonitor), jsonResponse({id: 7}));
        const form = createForm();
        form.init();

        await form.editFrom(7, 'Update Source', true);
        assert.equal(form.name, 'Source');
        assert.equal(form.category, 'Payments');
        assert.equal(form.isUpdate, true);
        assert.equal(form.entityId, 7);
        assert.equal(form.editTitle, 'Update Source');
        assert.equal(form.isNameLocked, true);
        assert.equal(form.isLoadingEntity, false);

        await form.upsert();

        assert.deepEqual(methodsAndUrlsOf(requests), [
            ['GET', `/api/v2/${type}-monitors/7`],
            ['PATCH', `/api/v2/${type}-monitors/7`],
        ]);
        // An update leaves the enabled state of the monitor alone, and stays on the page it was opened from
        assert.equal('enabled' in requests[1].body, false);
        assert.equal(requests[1].body.name, 'Source');
        assert.equal(browser.location.reloaded, true);
        assert.equal(browser.location.href, null);
    });

    test(`${type} resetState after editFrom turns the form back into a create form, which cloning keeps`, async (t) => {
        const browser = stubBrowser(t);
        const requests = stubRequests(
            t,
            jsonResponse(sourceMonitor),
            jsonResponse(sourceMonitor),
            jsonResponse({id: 42}),
        );
        const form = createForm();
        form.init();

        await form.editFrom(7, 'Update Source', true);
        form.resetState();
        assert.equal(form.name, '');
        assert.equal(form.category, null);
        assert.equal(form.isUpdate, false);
        assert.equal(form.entityId, null);
        assert.equal(form.editTitle, null);
        assert.equal(form.isNameLocked, false);

        await form.cloneFrom(7, 'Source (copy)');
        assert.equal(form.name, 'Source (copy)');
        assert.equal(form.isUpdate, false);
        assert.equal(form.entityId, null);

        await form.upsert();

        assert.deepEqual(methodsAndUrlsOf(requests), [
            ['GET', `/api/v2/${type}-monitors/7`],
            ['GET', `/api/v2/${type}-monitors/7`],
            ['POST', `/api/v2/${type}-monitors`],
        ]);
        assert.equal(requests[2].body.enabled, true);
        assert.equal(browser.location.reloaded, false);
        assert.equal(browser.location.href, `/${type}-monitors/42`);
    });

    test(`${type} a form rendered for a monitor gets that monitor and its name lock back after loading another one`, async (t) => {
        const browser = stubBrowser(t);
        const requests = stubRequests(t, jsonResponse(sourceMonitor), jsonResponse({id: 3}));
        const form = createForm({id: 3, name: 'Rendered'}, true);
        form.init();
        assert.equal(form.isUpdate, true);
        assert.equal(form.entityId, 3);
        assert.equal(form.isNameLocked, true);

        await form.editFrom(7, 'Update Source', false);
        assert.equal(form.entityId, 7);
        assert.equal(form.isNameLocked, false);

        form.resetState();
        assert.equal(form.name, 'Rendered');
        assert.equal(form.isUpdate, true);
        assert.equal(form.entityId, 3);
        assert.equal(form.editTitle, null);
        assert.equal(form.isNameLocked, true);

        await form.upsert();
        assert.deepEqual(methodsAndUrlsOf(requests)[1], ['PATCH', `/api/v2/${type}-monitors/3`]);
        assert.equal(browser.location.reloaded, true);
    });

    test(`${type} editFrom hides the overlay without switching to update mode when the monitor can't be loaded`, async (t) => {
        const browser = stubBrowser(t);
        stubRequests(t, errorResponse(500));
        const form = createForm();
        form.init();

        await form.editFrom(7, 'Update Source', true);

        assert.equal(form.isLoadingEntity, false);
        assert.equal(form.isUpdate, false);
        assert.equal(form.entityId, null);
        assert.equal(form.editTitle, null);
        assert.equal(form.isNameLocked, false);
        assert.equal(browser.alerts.length, 1);
    });

    test(`${type} a monitor that loads after the modal has been closed doesn't populate the reset form`, async (t) => {
        stubBrowser(t);
        const pending = stubPendingRequests(t);
        const form = createForm();
        form.init();

        const editing = form.editFrom(7, 'Update Source', true);
        const cloning = form.cloneFrom(7, 'Source (copy)');
        assert.equal(form.isLoadingEntity, true);
        form.resetState();
        assert.equal(form.isLoadingEntity, false);

        pending.forEach(request => request.resolve(jsonResponse(sourceMonitor)));
        await Promise.all([editing, cloning]);

        assert.equal(form.name, '');
        assert.equal(form.isUpdate, false);
        assert.equal(form.entityId, null);
        assert.equal(form.editTitle, null);
        assert.equal(form.isNameLocked, false);
        assert.equal(form.isLoadingEntity, false);
    });
});

test('Push editFrom keeps the client secret of the monitor, while cloneFrom generates a fresh one', async (t) => {
    stubRequests(t, jsonResponse(sourceMonitor), jsonResponse(sourceMonitor));
    const edited = monitorFormFactories.push();
    const cloned = monitorFormFactories.push();
    edited.init();
    cloned.init();

    await edited.editFrom(7, 'Update Source', false);
    await cloned.cloneFrom(7, 'Source (copy)');

    assert.equal(edited.clientSecret, sourceMonitor.clientSecret);
    assert.notEqual(cloned.clientSecret, sourceMonitor.clientSecret);
});

// --------- Editing a status page or a maintenance window through the upsert modal of its list ---------

const listEditedEntities = {
    'status-pages': {
        createForm: (entity = null) => upsertStatusPageForm(entity, {}, 'monitor-select', [], 'categories-select'),
        source: {
            id: 7,
            title: 'Source',
            slug: 'source',
            monitors: ['http:Site'],
            categories: ['Payments'],
            displayCategories: false,
            public: true,
        },
        rendered: {id: 3, title: 'Rendered', slug: 'rendered'},
        assertLoaded: (form) => {
            assert.equal(form.title, 'Source');
            assert.equal(form.slug, 'source');
            assert.deepEqual(form.selectedMonitors, ['http:Site']);
            assert.deepEqual(form.selectedCategories, ['Payments']);
            assert.equal(form.displayCategories, false);
            assert.equal(form.public, true);
        },
        assertBlank: (form) => {
            assert.equal(form.title, '');
            assert.deepEqual(form.selectedCategories, []);
        },
        assertRendered: (form) => assert.equal(form.title, 'Rendered'),
    },
    'maintenance-windows': {
        createForm: (entity = null) => upsertMaintenanceWindowForm(entity, {}, 'monitor-select', [], 'categories-select'),
        source: {
            id: 7,
            name: 'Source',
            cron: '0 2 * * *',
            duration: 'PT1H',
            enabled: false,
            monitors: ['http:Site'],
            categories: ['Payments'],
            integrations: ['email:ops'],
        },
        rendered: {id: 3, name: 'Rendered'},
        assertLoaded: (form) => {
            assert.equal(form.name, 'Source');
            assert.equal(form.type, MAINTENANCE_WINDOW_TYPES.CRON);
            assert.equal(form.cron, '0 2 * * *');
            assert.equal(form.duration, 'PT1H');
            assert.equal(form.enabled, false);
            assert.deepEqual(form.selectedMonitors, ['http:Site']);
            assert.deepEqual(form.selectedCategories, ['Payments']);
            assert.deepEqual(form.integrations, ['email:ops']);
        },
        assertBlank: (form) => {
            assert.equal(form.name, '');
            assert.equal(form.type, MAINTENANCE_WINDOW_TYPES.MANUAL);
            assert.deepEqual(form.selectedCategories, []);
        },
        assertRendered: (form) => assert.equal(form.name, 'Rendered'),
    },
};

Object.entries(listEditedEntities).forEach(([path, {createForm, source, rendered, assertLoaded, assertBlank, assertRendered}]) => {
    test(`${path} editFrom loads the entity into update mode and saves it with a PATCH, reloading the page`, async (t) => {
        const browser = stubBrowser(t);
        const requests = stubRequests(t, jsonResponse(source), jsonResponse({id: 7}));
        const form = createForm();
        form.init();

        const loading = form.editFrom(7, 'Update Source');
        assert.equal(form.isLoadingEntity, true);
        await loading;
        assertLoaded(form);
        assert.equal(form.isUpdate, true);
        assert.equal(form.entityId, 7);
        assert.equal(form.editTitle, 'Update Source');
        assert.equal(form.isLoadingEntity, false);

        await form.upsert();

        assert.deepEqual(methodsAndUrlsOf(requests), [
            ['GET', `/api/v2/${path}/7`],
            ['PATCH', `/api/v2/${path}/7`],
        ]);
        assert.deepEqual(requests[1].body.categories, ['Payments']);
        assert.equal(browser.location.reloaded, true);
        assert.equal(browser.location.href, null);
    });

    test(`${path} resetState after editFrom turns the form back into a create form`, async (t) => {
        const browser = stubBrowser(t);
        const requests = stubRequests(t, jsonResponse(source), jsonResponse({id: 42}));
        const form = createForm();
        form.init();

        await form.editFrom(7, 'Update Source');
        form.resetState();
        assertBlank(form);
        assert.equal(form.isUpdate, false);
        assert.equal(form.entityId, null);
        assert.equal(form.editTitle, null);

        await form.upsert();

        assert.deepEqual(methodsAndUrlsOf(requests)[1], ['POST', `/api/v2/${path}`]);
        assert.equal(browser.location.reloaded, false);
        assert.equal(browser.location.href, `/${path}/42`);
    });

    test(`${path} a form rendered for an entity gets that entity back after loading another one`, async (t) => {
        const browser = stubBrowser(t);
        const requests = stubRequests(t, jsonResponse(source), jsonResponse({id: 3}));
        const form = createForm(rendered);
        form.init();
        assert.equal(form.isUpdate, true);
        assert.equal(form.entityId, 3);

        await form.editFrom(7, 'Update Source');
        assert.equal(form.entityId, 7);

        form.resetState();
        assertRendered(form);
        assert.equal(form.isUpdate, true);
        assert.equal(form.entityId, 3);
        assert.equal(form.editTitle, null);

        await form.upsert();
        assert.deepEqual(methodsAndUrlsOf(requests)[1], ['PATCH', `/api/v2/${path}/3`]);
        assert.equal(browser.location.reloaded, true);
    });

    test(`${path} editFrom hides the overlay without switching to update mode when the entity can't be loaded`, async (t) => {
        const browser = stubBrowser(t);
        stubRequests(t, errorResponse(500));
        const form = createForm();
        form.init();

        await form.editFrom(7, 'Update Source');

        assert.equal(form.isLoadingEntity, false);
        assert.equal(form.isUpdate, false);
        assert.equal(form.entityId, null);
        assert.equal(form.editTitle, null);
        assertBlank(form);
        assert.equal(browser.alerts.length, 1);
    });

    test(`${path} an entity that loads after the modal has been closed doesn't turn the reset form into an update`, async (t) => {
        const browser = stubBrowser(t);
        const pending = stubPendingRequests(t);
        const form = createForm();
        form.init();

        const loading = form.editFrom(7, 'Update Source');
        form.resetState();
        assert.equal(form.isLoadingEntity, false);

        pending[0].resolve(jsonResponse(source));
        await loading;

        assertBlank(form);
        assert.equal(form.isUpdate, false);
        assert.equal(form.entityId, null);
        assert.equal(form.editTitle, null);
        assert.equal(form.isLoadingEntity, false);

        const saving = form.upsert();
        pending[1].resolve(jsonResponse({id: 42}));
        await saving;
        assert.equal(pending[1].method, 'POST');
        assert.equal(pending[1].url, `/api/v2/${path}`);
        assert.equal(browser.location.href, `/${path}/42`);
    });

    test(`${path} the response of an earlier load neither overrides nor ends the load of another entity`, async (t) => {
        const browser = stubBrowser(t);
        const pending = stubPendingRequests(t);
        const form = createForm();
        form.init();

        const loadingFailing = form.editFrom(5, 'Update Failing');
        const loadingStale = form.editFrom(6, 'Update Stale');
        const loadingCurrent = form.editFrom(7, 'Update Source');

        pending[0].resolve(errorResponse(500));
        pending[1].resolve(jsonResponse({...source, id: 6}));
        await Promise.all([loadingFailing, loadingStale]);
        // The overlay stays up for the entity that is still being loaded
        assert.equal(form.isLoadingEntity, true);
        assert.equal(form.isUpdate, false);
        assert.equal(form.entityId, null);
        assert.equal(browser.alerts.length, 1);

        pending[2].resolve(jsonResponse(source));
        await loadingCurrent;

        assertLoaded(form);
        assert.equal(form.isUpdate, true);
        assert.equal(form.entityId, 7);
        assert.equal(form.editTitle, 'Update Source');
        assert.equal(form.isLoadingEntity, false);
    });
});

test('the list items of status pages and maintenance windows dispatch the events of the upsert modal of their list', () => {
    const statusPage = statusPageListItem(7, true, 'Update Status');
    const maintenanceWindow = maintenanceWindowListItem(8, false, 'Update Window');
    const dispatched = [];
    [statusPage, maintenanceWindow].forEach(item => item.$dispatch = (name, detail) => dispatched.push([name, detail]));

    statusPage.editStatusPage();
    maintenanceWindow.editMaintenanceWindow();

    assert.deepEqual(dispatched, [
        ['edit-status-page', {id: 7, title: 'Update Status'}],
        ['edit-maintenance-window', {id: 8, title: 'Update Window'}],
    ]);
});

test('monitorListItem dispatches the events the shared upsert modal of the list listens to', () => {
    const item = monitorListItem({}, () => {})(7, true, false, 'Source (clone)', 'Update Source', true);
    const dispatched = [];
    item.$dispatch = (name, detail) => dispatched.push([name, detail]);

    item.editMonitor();
    item.cloneMonitor();

    assert.deepEqual(dispatched, [
        ['edit-monitor', {id: 7, title: 'Update Source', nameLocked: true}],
        ['clone-monitor', {id: 7, name: 'Source (clone)'}],
    ]);
});
