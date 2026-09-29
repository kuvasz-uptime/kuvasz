const test = require('node:test');
const assert = require('node:assert/strict');

// kuvasz.js registers a single DOMContentLoaded listener at load time; stub the one DOM call it needs.
globalThis.document = {addEventListener() {}};

const {
    MAINTENANCE_WINDOW_TYPES,
    sanitizeTextInput,
    splitWithLimit,
    statusCodeToBadgeClass,
    hasNonNullValue,
    bytesToMib,
    isValidUrl,
    isValidSlug,
    isValidIsoDuration,
    isoDurationToMillis,
    toDateTimeLocalValue,
    isValidIsoDate,
    isValidTime,
    formatStartValue,
    parseStartValue,
    toRgbColor,
    hueOf,
    distinctSeriesColor,
    resolveMaintenanceWindowType,
    createRandomSecret,
} = require('../main/resources/js/kuvasz.js');

const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

test('sanitizeTextInput', () => {
    assert.equal(sanitizeTextInput(null), null);
    assert.equal(sanitizeTextInput(undefined), null);
    assert.equal(sanitizeTextInput(42), null);
    assert.equal(sanitizeTextInput(''), null);
    assert.equal(sanitizeTextInput('   '), null);
    // Non-empty input is returned verbatim (not trimmed)
    assert.equal(sanitizeTextInput('  hi  '), '  hi  ');
    assert.equal(sanitizeTextInput('value'), 'value');
});

test('splitWithLimit', () => {
    assert.deepEqual(splitWithLimit('a', ':', 2), ['a']);
    assert.deepEqual(splitWithLimit('a:b', ':', 2), ['a', 'b']);
    // Everything past the limit is kept, joined, as the last element
    assert.deepEqual(splitWithLimit('a:b:c', ':', 2), ['a', 'b:c']);
    assert.deepEqual(splitWithLimit('http:name:extra', ':', 2), ['http', 'name:extra']);
    assert.deepEqual(splitWithLimit('a:b:c:d', ':', 3), ['a', 'b', 'c:d']);
});

test('statusCodeToBadgeClass', () => {
    assert.equal(statusCodeToBadgeClass('100'), 'status-azure');
    assert.equal(statusCodeToBadgeClass('204'), 'status-green');
    assert.equal(statusCodeToBadgeClass('301'), 'status-yellow');
    assert.equal(statusCodeToBadgeClass('404'), 'status-red');
    assert.equal(statusCodeToBadgeClass('500'), '');
});

test('hasNonNullValue', () => {
    assert.equal(hasNonNullValue({}), false);
    assert.equal(hasNonNullValue({a: null, b: null}), false);
    assert.equal(hasNonNullValue({a: null, b: 'err'}), true);
    // Falsy-but-not-null values still count as present
    assert.equal(hasNonNullValue({a: ''}), true);
    assert.equal(hasNonNullValue({a: 0}), true);
});

test('bytesToMib', () => {
    assert.equal(bytesToMib(null), null);
    assert.equal(bytesToMib(undefined), null);
    assert.equal(bytesToMib(0), 0);
    assert.equal(bytesToMib(1048576), 1);
    assert.equal(bytesToMib(1572864), 1.5);
    assert.equal(bytesToMib(1100000), 1.0);
    assert.equal(bytesToMib(123456789), 117.7);
});

test('isValidUrl', () => {
    assert.equal(isValidUrl('https://example.com'), true);
    assert.equal(isValidUrl('http://a.b/c?d=e&f=g'), true);
    assert.equal(isValidUrl('ftp://example.com'), false);
    assert.equal(isValidUrl('example.com'), false);
    assert.equal(isValidUrl(''), false);
});

test('isValidSlug', () => {
    assert.equal(isValidSlug('my-slug'), true);
    assert.equal(isValidSlug('valid_slug-1'), true);
    assert.equal(isValidSlug('UPPER'), false);
    assert.equal(isValidSlug(''), false);
});

test('isValidIsoDuration', () => {
    assert.equal(isValidIsoDuration('PT1H30M'), true);
    assert.equal(isValidIsoDuration('P1DT2H'), true);
    assert.equal(isValidIsoDuration('PT45S'), true);
    // Structurally valid but all-zero durations are rejected
    assert.equal(isValidIsoDuration('PT0S'), false);
    assert.equal(isValidIsoDuration('P0D'), false);
    assert.equal(isValidIsoDuration(''), false);
    assert.equal(isValidIsoDuration('garbage'), false);
    assert.equal(isValidIsoDuration('1H'), false);
});

test('isoDurationToMillis', () => {
    const hour = 60 * 60 * 1000;
    // The periods of the metrics charts, as serialized by java.time.Duration
    assert.equal(isoDurationToMillis('PT1H'), hour);
    assert.equal(isoDurationToMillis('PT720H'), 720 * hour);
    // Every component, including a fractional second
    assert.equal(isoDurationToMillis('P1W2DT3H4M5.5S'), (7 * 24 + 2 * 24 + 3) * hour + 4 * 60 * 1000 + 5500);
});

test('toDateTimeLocalValue', () => {
    assert.equal(toDateTimeLocalValue(''), '');
    assert.equal(toDateTimeLocalValue(null), '');
    assert.equal(toDateTimeLocalValue('not-a-date'), '');

    // Compare against the same Date rendered locally, so the assertion is timezone-independent
    const iso = '2024-03-15T10:30:00Z';
    const d = new Date(iso);
    const pad = (n) => String(n).padStart(2, '0');
    const expected = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
        `T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    assert.equal(toDateTimeLocalValue(iso), expected);
});

test('isValidIsoDate', () => {
    assert.equal(isValidIsoDate('2030-01-05'), true);
    assert.equal(isValidIsoDate('2028-02-29'), true);
    assert.equal(isValidIsoDate(''), false);
    assert.equal(isValidIsoDate(null), false);
    assert.equal(isValidIsoDate('2030-1-5'), false);
    assert.equal(isValidIsoDate('05/01/2030'), false);
    assert.equal(isValidIsoDate('2030-01-05T10:00'), false);
    // Well-formed, but not an existing calendar day
    assert.equal(isValidIsoDate('2030-02-29'), false);
    assert.equal(isValidIsoDate('2030-13-01'), false);
});

test('isValidTime', () => {
    assert.equal(isValidTime('00:00'), true);
    assert.equal(isValidTime('23:59'), true);
    assert.equal(isValidTime(''), false);
    assert.equal(isValidTime(null), false);
    assert.equal(isValidTime('24:00'), false);
    assert.equal(isValidTime('10:60'), false);
    assert.equal(isValidTime('9:30'), false);
    assert.equal(isValidTime('10:30:00'), false);
});

test('formatStartValue', () => {
    assert.equal(formatStartValue('2030-01-05', '10:30'), '2030-01-05 10:30');
    assert.equal(formatStartValue('2030-01-05', ''), '2030-01-05');
    assert.equal(formatStartValue('', '10:30'), '10:30');
    assert.equal(formatStartValue('', ''), '');
});

test('parseStartValue', () => {
    assert.deepEqual(parseStartValue('2030-01-05 10:30'), {date: '2030-01-05', time: '10:30'});
    // Extra whitespace and the ISO separator are tolerated
    assert.deepEqual(parseStartValue('  2030-01-05   10:30 '), {date: '2030-01-05', time: '10:30'});
    assert.deepEqual(parseStartValue('2030-01-05T10:30'), {date: '2030-01-05', time: '10:30'});
    assert.deepEqual(parseStartValue('2030-01-05'), {date: '2030-01-05', time: ''});
    assert.deepEqual(parseStartValue(''), {date: '', time: ''});
    assert.deepEqual(parseStartValue(null), {date: '', time: ''});
});

test('toRgbColor', () => {
    assert.equal(toRgbColor([6, 111, 209]), '#066fd1');
    assert.equal(toRgbColor([0, 0, 0, 255]), '#000000');
    assert.equal(toRgbColor([6, 111, 209], 0.16), 'rgba(6, 111, 209, 0.16)');
});

test('hueOf', () => {
    assert.equal(hueOf('#ff0000'), 0);
    assert.equal(hueOf('#00ff00'), 120);
    assert.equal(hueOf('#0000ff'), 240);
    assert.equal(hueOf('#ff00ff'), 300);
    assert.equal(Math.round(hueOf('#066fd1')), 209);
    // Grays, like the inverted accent, have no hue
    assert.equal(hueOf('#ffffff'), null);
    assert.equal(hueOf('#1f2937'), null);
});

// The sRGB values of Tabler's palette
const TABLER = {
    blue: '#066fd1',
    azure: '#4299e1',
    green: '#2fb344',
    lime: '#74b816',
    teal: '#0ca678',
    red: '#d63939',
    pink: '#d6336c',
    orange: '#f76707',
    yellow: '#f59f00',
    purple: '#ae3ec9',
    gray500: '#737373',
};

test('distinctSeriesColor keeps the preferred color when it is distinct from the taken ones', () => {
    assert.equal(distinctSeriesColor([TABLER.blue], [TABLER.orange, TABLER.purple]), TABLER.orange);
    assert.equal(distinctSeriesColor([TABLER.purple], [TABLER.orange, TABLER.purple]), TABLER.orange);
    // Purple and pink are close in name only
    assert.equal(distinctSeriesColor([TABLER.pink], [TABLER.purple, TABLER.orange]), TABLER.purple);
});

test('distinctSeriesColor skips the candidates close to the accent', () => {
    [TABLER.green, TABLER.lime, TABLER.teal].forEach((accent) =>
        assert.equal(distinctSeriesColor([accent], [TABLER.green, TABLER.purple, TABLER.orange]), TABLER.purple));
    [TABLER.orange, TABLER.red, TABLER.yellow, TABLER.pink].forEach((accent) =>
        assert.equal(distinctSeriesColor([accent], [TABLER.orange, TABLER.purple]), TABLER.purple));
    assert.equal(distinctSeriesColor([TABLER.azure], [TABLER.blue, TABLER.green]), TABLER.green);
});

test('distinctSeriesColor keeps the series apart from each other too', () => {
    assert.equal(distinctSeriesColor([TABLER.blue, TABLER.green], [TABLER.red, TABLER.gray500]), TABLER.red);
    assert.equal(distinctSeriesColor([TABLER.green, TABLER.purple], [TABLER.red, TABLER.gray500]), TABLER.red);
    assert.equal(distinctSeriesColor([TABLER.orange, TABLER.green], [TABLER.red, TABLER.gray500]), TABLER.gray500);
    assert.equal(distinctSeriesColor([TABLER.blue, TABLER.pink], [TABLER.red, TABLER.gray500]), TABLER.gray500);
});

test('distinctSeriesColor ignores the hue of a gray accent', () => {
    assert.equal(distinctSeriesColor(['#fafafa'], [TABLER.orange, TABLER.purple]), TABLER.orange);
    assert.equal(distinctSeriesColor(['#1f2937'], [TABLER.green, TABLER.purple]), TABLER.green);
});

test('distinctSeriesColor falls back to the last candidate when none of them are distinct', () => {
    assert.equal(distinctSeriesColor([TABLER.red], [TABLER.orange, TABLER.pink]), TABLER.pink);
});

test('resolveMaintenanceWindowType', () => {
    assert.equal(resolveMaintenanceWindowType(null), MAINTENANCE_WINDOW_TYPES.MANUAL);
    assert.equal(resolveMaintenanceWindowType(undefined), MAINTENANCE_WINDOW_TYPES.MANUAL);
    assert.equal(resolveMaintenanceWindowType({name: 'x'}), MAINTENANCE_WINDOW_TYPES.MANUAL);
    assert.equal(resolveMaintenanceWindowType({cron: '0 0 * * *'}), MAINTENANCE_WINDOW_TYPES.CRON);
    assert.equal(resolveMaintenanceWindowType({start: '2024-01-01T00:00:00Z'}), MAINTENANCE_WINDOW_TYPES.SINGLE);
    // cron takes precedence over start
    assert.equal(resolveMaintenanceWindowType({cron: '0 0 * * *', start: '2024-01-01T00:00:00Z'}),
        MAINTENANCE_WINDOW_TYPES.CRON);
});

test('createRandomSecret uses crypto.randomUUID when available', () => {
    assert.match(createRandomSecret(), UUID_V4_PATTERN);
});

test('createRandomSecret falls back to getRandomValues in non-secure contexts', () => {
    // crypto.randomUUID is undefined outside secure contexts (plain-HTTP LAN deployments)
    const original = crypto.randomUUID;
    try {
        crypto.randomUUID = undefined;
        const secret = createRandomSecret();
        assert.match(secret, UUID_V4_PATTERN);
        // Two consecutive calls must not collide
        assert.notEqual(secret, createRandomSecret());
    } finally {
        crypto.randomUUID = original;
    }
});
