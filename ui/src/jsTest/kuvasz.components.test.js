const test = require('node:test');
const assert = require('node:assert/strict');

// kuvasz.js registers a single DOMContentLoaded listener at load time; stub the DOM calls it needs.
// getElementById returns null so resetTomSelectState (called from the HTTP form's populateFrom) no-ops.
globalThis.document = {addEventListener() {}, getElementById() { return null; }};
// resetTomSelectState references TomSelect in an instanceof check; a stub keeps that expression from throwing.
globalThis.TomSelect = class TomSelect {};

const {
    MAINTENANCE_WINDOW_TYPES,
    upsertHttpMonitorForm,
    upsertPushMonitorForm,
    upsertIcmpMonitorForm,
    upsertTcpMonitorForm,
    upsertMaintenanceWindowForm,
    httpMetricsBlock,
    icmpMetricsBlock,
    tcpMetricsBlock,
} = require('../main/resources/js/kuvasz.js');

// --------- #1: isValidHttpHeaderName (regex) ---------

test('isValidHttpHeaderName', () => {
    const form = upsertHttpMonitorForm(null, {}, 'select', [], 0);
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

// --------- #3: chart data transforms ---------

test('httpMetricsBlock.transformData maps latency logs to a single series', () => {
    const block = httpMetricsBlock(1, true, 60, 'no data', 24);
    const result = block.transformData({
        latencyLogs: [
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: '123'},
            {createdAt: '2024-01-01T00:01:00Z', latencyInMs: 200},
        ],
    });
    assert.equal(result.labels.length, 2);
    assert.equal(result.series.length, 1);
    assert.equal(result.series[0].name, 'Latency');
    assert.deepEqual(result.series[0].data, [123, 200]);
});

test('icmpMetricsBlock.transformData preserves null latency but parses packet loss', () => {
    const block = icmpMetricsBlock(1, true, 60, 'no data', 24);
    const result = block.transformData({
        metricsLogs: [
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: null, packetLossPercentage: '50'},
            {createdAt: '2024-01-01T00:01:00Z', latencyInMs: '80', packetLossPercentage: 0},
        ],
    });
    // Null latency must stay null (a gap in the chart), not become NaN
    assert.deepEqual(result.latency.series[0].data, [null, 80]);
    assert.deepEqual(result.packetLoss.series[0].data, [50, 0]);
    assert.equal(result.latency.labels.length, 2);
    assert.equal(result.packetLoss.labels.length, 2);
});

test('tcpMetricsBlock.transformData preserves null latency and has no packet-loss series', () => {
    const block = tcpMetricsBlock(1, true, 60, 'no data', 24);
    const result = block.transformData({
        metricsLogs: [
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: null},
            {createdAt: '2024-01-01T00:01:00Z', latencyInMs: '80'},
        ],
    });
    // Null latency must stay null (a gap in the chart), not become NaN
    assert.deepEqual(result.latency.series[0].data, [null, 80]);
    assert.equal(result.latency.labels.length, 2);
    // TCP monitors track latency only - there is no packet-loss series
    assert.equal(result.packetLoss, undefined);
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
    const buildForm = () => upsertIcmpMonitorForm(null, msgs, 0);

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
    const buildForm = () => upsertTcpMonitorForm(null, msgs, 0);

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
    const buildForm = () => upsertPushMonitorForm(null, msgs, 0);

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
    const form = upsertHttpMonitorForm(null, {}, 'select', [], 0);

    form.populateFrom({
        name: 'Src', url: 'https://example.com', sensitiveUrl: true, sslExpiryThreshold: 14,
        failureCountThreshold: 4, uptimeCheckInterval: 120, sslCheckEnabled: true,
        latencyHistoryEnabled: false, forceNoCache: false, followRedirects: false,
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
    const form = upsertPushMonitorForm(null, {}, 0);

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
    const form = upsertIcmpMonitorForm(null, {}, 0);

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
    const form = upsertTcpMonitorForm(null, {}, 0);

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
