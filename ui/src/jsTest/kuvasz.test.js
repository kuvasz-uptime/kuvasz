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
    isValidUrl,
    isValidSlug,
    isValidIsoDuration,
    toDateTimeLocalValue,
    resolveMaintenanceWindowType,
} = require('../main/resources/js/kuvasz.js');

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
