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
    loadCategoryOptions,
    resetCategorySelect,
    resetMultiSelect,
    fetchMonitors,
    loadMonitorOptions,
    addSearchHint,
    listQuery,
    LIST_QUERY_CHANGE_EVENT,
    monitorListItem,
    statusPageListItem,
    maintenanceWindowListItem,
    upsertHttpMonitorForm,
    upsertPushMonitorForm,
    upsertIcmpMonitorForm,
    upsertTcpMonitorForm,
    upsertDnsMonitorForm,
    upsertDockerMonitorForm,
    renderDockerContainerOption,
    upsertStatusPageForm,
    upsertMaintenanceWindowForm,
    incidentNoteForm,
    resolveStatusPageAccordionState,
    applyStatusPageAccordionsState,
    rememberStatusPageAccordionChoice,
    rememberShownStatusPageAccordions,
    showStatusPageIncidents,
    refreshStatusPageIncidentsModal,
    INVESTIGATION_STATUSES,
    httpMetricsBlock,
    icmpMetricsBlock,
    tcpMetricsBlock,
    dnsMetricsBlock,
    dockerMetricsBlock,
    buildIncidentAnnotations,
    CHART_INCIDENTS_PAGE_SIZE,
    buildRestartAnnotations,
    metricsChartTooltip,
    formatChartTimestamp,
    toDateTimeLocalValue,
    anchorPopupToModal,
    setTheme,
    setThemeOption,
    appearanceSettings,
    reInitTooltips,
    initSparklines,
    disposeComponents,
    TOOLTIP_DISPOSE_DELAY_MS,
} = require('../main/resources/js/kuvasz.js');

// --------- #1: isValidHttpHeaderName (regex) ---------

test('isValidHttpHeaderName', () => {
    const form = upsertHttpMonitorForm(null, {}, 'category-select', false, 'select', [], 'proxy-select', [], '(not configured)', 0);
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
    const form = upsertMaintenanceWindowForm(null, {}, 'select');
    Object.assign(form, {
        type: MAINTENANCE_WINDOW_TYPES.MANUAL,
        name: 'MW', description: 'desc', enabled: true, global: false, showOnStatusPages: true,
        cron: '0 0 * * *', pickedDate: '2024-01-01', pickedTime: '10:00', duration: 'PT1H',
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
    const form = upsertMaintenanceWindowForm(null, {}, 'select');
    Object.assign(form, {
        type: MAINTENANCE_WINDOW_TYPES.CRON,
        name: 'MW', cron: '0 0 * * *', pickedDate: '2024-01-01', pickedTime: '10:00', duration: 'PT2H',
        selectedMonitors: [], integrations: [],
    });
    const body = form.buildRequestBody();
    assert.equal(body.cron, '0 0 * * *');
    assert.equal(body.start, null);
    assert.equal(body.duration, 'PT2H');
});

test('buildRequestBody converts start to ISO and drops cron for a SINGLE window', () => {
    const form = upsertMaintenanceWindowForm(null, {}, 'select');
    Object.assign(form, {
        type: MAINTENANCE_WINDOW_TYPES.SINGLE,
        name: 'MW', cron: '0 0 * * *', pickedDate: '2024-01-01', pickedTime: '10:00', duration: 'PT30M',
        selectedMonitors: [], integrations: [],
    });
    const body = form.buildRequestBody();
    assert.equal(body.cron, null);
    assert.equal(body.duration, 'PT30M');
    // The local date and time are converted to an absolute ISO instant
    assert.equal(body.start, new Date('2024-01-01T10:00').toISOString());
});

test('buildRequestBody leaves start null for a SINGLE window without a date or a time', () => {
    const form = upsertMaintenanceWindowForm(null, {}, 'select');
    Object.assign(form, {
        type: MAINTENANCE_WINDOW_TYPES.SINGLE, name: 'MW', pickedDate: '', pickedTime: '10:00', duration: 'PT30M',
        selectedMonitors: [], integrations: [],
    });
    assert.equal(form.buildRequestBody().start, null);

    Object.assign(form, {pickedDate: '2024-01-01', pickedTime: ''});
    assert.equal(form.buildRequestBody().start, null);
});

test('validateDateTime requires a valid date and time, only for a SINGLE window', () => {
    const form = upsertMaintenanceWindowForm(null, {startRequired: 'REQUIRED', startInvalid: 'INVALID'}, 'select');
    Object.assign(form, {type: MAINTENANCE_WINDOW_TYPES.SINGLE, pickedDate: '', pickedTime: '', errors: {}});

    form.validateDateTime();
    assert.equal(form.errors.start, 'REQUIRED');

    form.pickedDate = '2030-01-01';
    form.validateDateTime();
    assert.equal(form.errors.start, 'REQUIRED');

    form.pickedTime = '10:00';
    form.validateDateTime();
    assert.equal(form.errors.start, null);

    form.pickedDate = '2030-02-30';
    form.validateDateTime();
    assert.equal(form.errors.start, 'INVALID');

    form.pickedDate = '2030-02-28';
    form.pickedTime = '24:00';
    form.validateDateTime();
    assert.equal(form.errors.start, 'INVALID');

    form.type = MAINTENANCE_WINDOW_TYPES.CRON;
    form.validateDateTime();
    assert.equal(form.errors.start, null);
});

test('onTypeChange clears the start date and time when leaving a SINGLE window', () => {
    const form = upsertMaintenanceWindowForm(null, {}, 'select');
    Object.assign(form, {
        type: MAINTENANCE_WINDOW_TYPES.CRON, name: 'MW', cron: '0 0 * * *', pickedDate: '2030-01-01',
        pickedTime: '10:00', duration: 'PT1H', errors: {},
    });
    form.onTypeChange();
    assert.equal(form.pickedDate, '');
    assert.equal(form.pickedTime, '');
});

test('populateFrom splits the start of a SINGLE window into a local date and time', () => {
    const form = upsertMaintenanceWindowForm(null, {}, 'monitor-select', 'categories-select');
    const start = '2030-01-01T10:30:00Z';
    form.populateFrom({name: 'MW', start, duration: 'PT1H'});

    const [expectedDate, expectedTime] = toDateTimeLocalValue(start).split('T');
    assert.equal(form.type, MAINTENANCE_WINDOW_TYPES.SINGLE);
    assert.equal(form.pickedDate, expectedDate);
    assert.equal(form.pickedTime, expectedTime);

    form.populateFrom(null);
    assert.equal(form.pickedDate, '');
    assert.equal(form.pickedTime, '');
});

// Stubs the root element (with the given theme attributes) and the local storage for the theme preference helpers
const stubThemeEnvironment = (t, attributes = {}) => {
    const originalDocumentElement = globalThis.document.documentElement;
    const originalLocalStorage = globalThis.localStorage;
    const storage = {};
    globalThis.document.documentElement = {
        getAttribute: (name) => attributes[name] ?? null,
        setAttribute: (name, value) => attributes[name] = value,
    };
    globalThis.localStorage = {setItem: (key, value) => storage[key] = value};
    t.after(() => {
        globalThis.document.documentElement = originalDocumentElement;
        globalThis.localStorage = originalLocalStorage;
    });
    return {attributes, storage};
};

test('setThemeOption applies the option on the root element and saves it for the next page loads', (t) => {
    const {attributes, storage} = stubThemeEnvironment(t);

    setThemeOption('base', 'slate');
    setThemeOption('primary', 'teal');

    assert.deepEqual(attributes, {'data-bs-theme-base': 'slate', 'data-bs-theme-primary': 'teal'});
    assert.deepEqual(storage, {'kuvasz-theme-base': 'slate', 'kuvasz-theme-primary': 'teal'});
});

test('appearanceSettings preselects the applied options, falling back to the default accent color', (t) => {
    stubThemeEnvironment(t, {'data-bs-theme-base': 'gray'});
    const settings = appearanceSettings('blue');
    assert.equal(settings.base, 'gray');
    assert.equal(settings.primary, 'blue');
});

test('appearanceSettings preselects a saved accent color', (t) => {
    stubThemeEnvironment(t, {'data-bs-theme-base': 'zinc', 'data-bs-theme-primary': 'purple'});
    const settings = appearanceSettings('blue');
    assert.equal(settings.base, 'zinc');
    assert.equal(settings.primary, 'purple');
});

test('anchorPopupToModal moves the datepicker popup into the modal, translating its page coordinates', (t) => {
    const originalWindow = globalThis.window;
    globalThis.window = {scrollX: 5, scrollY: 100};
    t.after(() => { globalThis.window = originalWindow; });

    const appended = [];
    const modal = {
        scrollTop: 250,
        scrollLeft: 0,
        getBoundingClientRect: () => ({top: 10, left: 20}),
        append: (element) => {
            appended.push(element);
            popup.parentElement = modal;
        },
    };
    const input = {closest: (selector) => selector === '.modal' ? modal : null};
    const popup = {parentElement: {}, style: {top: '400px', left: '305px'}, dataset: {}};

    anchorPopupToModal(input, popup);
    assert.deepEqual(appended, [popup]);
    // 400 - 100 (page scroll) - 10 (modal offset) + 250 (modal scroll), and 305 - 5 - 20 + 0
    assert.deepEqual(popup.style, {top: '540px', left: '280px'});

    // An already anchored position isn't translated again
    anchorPopupToModal(input, popup);
    assert.deepEqual(popup.style, {top: '540px', left: '280px'});

    // Once it's in the modal, it's only translated again when the calendar has repositioned it in page coordinates
    popup.style = {top: '400px', left: '305px'};
    anchorPopupToModal(input, popup);
    assert.equal(appended.length, 1);
    assert.deepEqual(popup.style, {top: '540px', left: '280px'});
});

test('onDateTimeTyped splits the typed start into a date and a time, and validates them', () => {
    const form = upsertMaintenanceWindowForm(null, {startRequired: 'REQUIRED', startInvalid: 'INVALID'}, 'select');
    Object.assign(form, {type: MAINTENANCE_WINDOW_TYPES.SINGLE, pickedDate: '', pickedTime: '', errors: {}});

    form.onDateTimeTyped(' 2030-01-05 10:30 ');
    assert.equal(form.pickedDate, '2030-01-05');
    assert.equal(form.pickedTime, '10:30');
    assert.equal(form.errors.start, null);

    form.onDateTimeTyped('2030-01-05');
    assert.equal(form.pickedTime, '');
    assert.equal(form.errors.start, 'REQUIRED');

    form.onDateTimeTyped('2030-01-05 25:00');
    assert.equal(form.errors.start, 'INVALID');
});

test('initDateTimePicker keeps the Tabler datepicker and the start in sync', async (t) => {
    const datepickers = [];
    const originalTabler = globalThis.tabler;
    globalThis.tabler = {
        Datepicker: class {
            constructor(element, config) {
                this.element = element;
                this.config = config;
                this.selectedDates = [];
                this.setCalls = [];
                this.calendarUpdates = [];
                this.calendar = {
                    context: {selectedTime: '08:15', mainElement: popup},
                    update: (resets) => this.calendarUpdates.push(resets),
                };
                datepickers.push(this);
            }
            getSelectedDates() { return [...this.selectedDates]; }
            setSelectedDates(dates) {
                this.selectedDates = dates;
                this.setCalls.push(dates);
                // Like Tabler, the input is written with the selection
                this.element.value = dates.length > 0 ? this.config.dateFormat(new Date(`${dates[0]}T00:00`)) : '';
            }
        },
    };
    const originalWindow = globalThis.window;
    const originalRequestAnimationFrame = globalThis.requestAnimationFrame;
    const windowListeners = {};
    globalThis.window = {scrollX: 0, scrollY: 0, addEventListener: (name, listener) => windowListeners[name] = listener};
    globalThis.requestAnimationFrame = (callback) => callback();
    t.after(() => {
        globalThis.tabler = originalTabler;
        globalThis.window = originalWindow;
        globalThis.requestAnimationFrame = originalRequestAnimationFrame;
    });

    const popup = {parentElement: null, style: {top: '500px', left: '40px'}, dataset: {}};
    let modal = null;
    const listeners = {};
    const element = {addEventListener: (name, listener) => listeners[name] = listener, closest: () => modal};
    const watchers = {};
    const form = upsertMaintenanceWindowForm(null, {startRequired: 'REQUIRED'}, 'select');
    Object.assign(form, {
        type: MAINTENANCE_WINDOW_TYPES.SINGLE, pickedDate: '', pickedTime: '', errors: {},
        $watch: (property, callback) => watchers[property] = callback,
    });

    form.initDateTimePicker(element);
    const [datepicker] = datepickers;
    assert.equal(datepicker.element, element);
    assert.equal(datepicker.config.vcpOptions.selectionTimeMode, 24);
    assert.equal(datepicker.config.placement, 'auto');
    // Without a valid time the calendar's own default is kept
    assert.equal('selectedTime' in datepicker.config.vcpOptions, false);

    // Outside of a modal the popup is left where Tabler put it
    listeners['shown.bs.datepicker']();
    assert.deepEqual(popup.style, {top: '500px', left: '40px'});
    listeners['hidden.bs.datepicker']();

    modal = {
        scrollTop: 100,
        scrollLeft: 0,
        getBoundingClientRect: () => ({top: 0, left: 0}),
        append: (child) => child.parentElement = modal,
    };
    // Before the popup is shown (when the calendar's main element is still the input), a resize leaves it in place
    windowListeners.resize();
    assert.equal(popup.parentElement, null);
    assert.deepEqual(popup.style, {top: '500px', left: '40px'});

    // In a modal, the popup is anchored to it whenever the calendar has put it in page coordinates
    listeners['shown.bs.datepicker']();
    assert.deepEqual(popup.style, {top: '600px', left: '40px'});

    // ...e.g. after the calendar repositions it on a resize
    popup.style = {top: '520px', left: '40px'};
    windowListeners.resize();
    assert.deepEqual(popup.style, {top: '620px', left: '40px'});

    // ...but not while it's hidden
    listeners['hidden.bs.datepicker']();
    popup.style = {top: '520px', left: '40px'};
    windowListeners.resize();
    assert.deepEqual(popup.style, {top: '520px', left: '40px'});
    listeners['shown.bs.datepicker']();

    // A picked day lands in the model, taking over the time shown by the time picker when there's none yet
    listeners['change.bs.datepicker']({dates: ['2030-01-05']});
    assert.equal(form.pickedDate, '2030-01-05');
    assert.equal(form.pickedTime, '08:15');
    assert.equal(form.errors.start, null);
    // Tabler writes the input with the time included
    assert.equal(datepicker.config.dateFormat(new Date(2030, 0, 5)), '2030-01-05 08:15');

    // ...and the popup stays open right after, so the time can be adjusted, but it can be closed after that
    const hideAfterPick = {defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }};
    listeners['hide.bs.datepicker'](hideAfterPick);
    assert.equal(hideAfterPick.defaultPrevented, true);
    // Tabler reopens the popup in page coordinates right after the prevented hide, which gets anchored again
    popup.style = {top: '510px', left: '40px'};
    await Promise.resolve();
    assert.deepEqual(popup.style, {top: '610px', left: '40px'});
    const laterHide = {defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }};
    listeners['hide.bs.datepicker'](laterHide);
    assert.equal(laterHide.defaultPrevented, false);

    // An already set time is kept when another day is picked
    form.pickedTime = '10:30';
    listeners['change.bs.datepicker']({dates: ['2030-01-06']});
    assert.equal(form.pickedTime, '10:30');

    // A picked time lands in the model
    datepicker.config.vcpOptions.onChangeTime({context: {selectedTime: '11:45'}});
    assert.equal(form.pickedTime, '11:45');
    // ...keeping the typed seconds, which the time picker can't select
    form.pickedTime = '11:45:30';
    datepicker.config.vcpOptions.onChangeTime({context: {selectedTime: '12:10'}});
    assert.equal(form.pickedTime, '12:10:30');
    // ...but not the seconds of an invalid time
    form.pickedTime = '11:4:30';
    datepicker.config.vcpOptions.onChangeTime({context: {selectedTime: '12:10'}});
    assert.equal(form.pickedTime, '12:10');
    form.pickedTime = '11:45';

    // A typed (or loaded) valid day is selected in the calendar, an invalid one clears it
    form.pickedDate = '2030-02-10';
    watchers.pickedDate(form.pickedDate);
    assert.equal(element.value, '2030-02-10 11:45');
    form.pickedDate = '2030-02-1';
    watchers.pickedDate(form.pickedDate);
    assert.deepEqual(datepicker.setCalls, [['2030-02-10'], []]);
    // ...but the typed value is kept in the input, instead of what Tabler writes there
    assert.equal(element.value, '2030-02-1 11:45');
    // The calendar isn't reset when it already shows the same day, e.g. right after it was picked there
    datepicker.selectedDates = ['2030-03-01'];
    watchers.pickedDate('2030-03-01');
    assert.equal(datepicker.setCalls.length, 2);

    // A typed (or loaded) valid time is set in the calendar, without touching its dates
    watchers.pickedTime('14:00');
    assert.equal(datepicker.calendar.selectedTime, '14:00');
    assert.deepEqual(datepicker.calendarUpdates, [{dates: false, month: false, year: false}]);
    // ...unless it shows that time already (e.g. it was just picked there), or the time is invalid
    watchers.pickedTime('08:15');
    watchers.pickedTime('8:1');
    assert.equal(datepicker.calendarUpdates.length, 1);
    assert.equal(datepicker.calendar.selectedTime, '08:15');
    // A time with seconds is set in the calendar without them, which it can't show
    watchers.pickedTime('08:15:30');
    assert.equal(datepicker.calendarUpdates.length, 1);
    assert.equal(datepicker.calendar.selectedTime, '08:15');
    watchers.pickedTime('16:20:30');
    assert.equal(datepicker.calendar.selectedTime, '16:20');
    assert.equal(datepicker.calendarUpdates.length, 2);

    // A cleared selection clears the date and lets the popup close
    listeners['change.bs.datepicker']({dates: []});
    assert.equal(form.pickedDate, '');
    const hideAfterClear = {defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }};
    listeners['hide.bs.datepicker'](hideAfterClear);
    assert.equal(hideAfterClear.defaultPrevented, false);
});

// A Tabler datepicker that parsed the given dates from the input (or nothing, if it couldn't)
const stubDatepicker = (t, parsedDates) => {
    const datepickers = [];
    const originalTabler = globalThis.tabler;
    const originalWindow = globalThis.window;
    globalThis.tabler = {
        Datepicker: class {
            constructor(element, config) {
                this.element = element;
                this.config = config;
                this.selectedDates = parsedDates;
                this.setCalls = [];
                this.listeners = {};
                element.addEventListener = (name, listener) => this.listeners[name] = listener;
                this.calendar = {
                    selectedTime: config.vcpOptions.selectedTime,
                    context: {selectedTime: config.vcpOptions.selectedTime ?? '00:00'},
                    // Like the calendar, the time is reset from the options, falling back to its default
                    update() { this.context.selectedTime = this.selectedTime ?? '00:00'; },
                };
                datepickers.push(this);
            }
            getSelectedDates() { return [...this.selectedDates]; }
            setSelectedDates(dates) {
                this.selectedDates = dates;
                this.setCalls.push(dates);
                this.element.value = '';
            }
        },
    };
    globalThis.window = {addEventListener() {}};
    t.after(() => {
        globalThis.tabler = originalTabler;
        globalThis.window = originalWindow;
    });
    return datepickers;
};

// A maintenance window form with a loaded start, and the watchers it registered
const startFormOf = (pickedDate, pickedTime) => {
    const watchers = {};
    const form = upsertMaintenanceWindowForm(null, {}, 'select');
    Object.assign(form, {
        type: MAINTENANCE_WINDOW_TYPES.SINGLE, pickedDate, pickedTime, errors: {},
        $watch: (property, callback) => watchers[property] = callback,
    });
    return {form, watchers};
};

test('initDateTimePicker preselects a loaded start in the calendar, even if Tabler could not parse it', (t) => {
    const datepickers = stubDatepicker(t, []);
    const {form} = startFormOf('2030-01-05', '10:30');
    const element = {value: '2030-01-05 10:30'};

    form.initDateTimePicker(element);
    const [datepicker] = datepickers;
    assert.equal(datepicker.config.vcpOptions.selectedTime, '10:30');
    assert.deepEqual(datepicker.setCalls, [['2030-01-05']]);
    // ...keeping the time in the input, which Tabler would write without it
    assert.equal(element.value, '2030-01-05 10:30');
});

test('initDateTimePicker preselects the time of a loaded start with seconds without them', (t) => {
    const datepickers = stubDatepicker(t, []);
    const {form} = startFormOf('2030-01-05', '10:30:45');
    const element = {value: '2030-01-05 10:30:45'};

    form.initDateTimePicker(element);
    assert.equal(datepickers[0].config.vcpOptions.selectedTime, '10:30');
    assert.equal(element.value, '2030-01-05 10:30:45');
    // A day picked in the calendar keeps the time, seconds included
    datepickers[0].listeners['change.bs.datepicker']({dates: ['2030-01-06']});
    assert.equal(form.pickedTime, '10:30:45');
    assert.equal(form.pickedDateTimeAsIso(), new Date('2030-01-06T10:30:45').toISOString());
});

test('initDateTimePicker leaves the calendar alone when Tabler parsed the loaded start already', (t) => {
    const datepickers = stubDatepicker(t, ['2030-01-05']);
    const {form} = startFormOf('2030-01-05', '10:30');

    form.initDateTimePicker({value: '2030-01-05 10:30'});
    assert.deepEqual(datepickers[0].setCalls, []);
});

test('initDateTimePicker does not take over the time of a previous session of a reset form', (t) => {
    const datepickers = stubDatepicker(t, []);
    const {form, watchers} = startFormOf('', '');
    form.initDateTimePicker({});
    const [datepicker] = datepickers;

    // A time is picked, then the form is cancelled and opened again
    datepicker.config.vcpOptions.onChangeTime({context: {selectedTime: '23:30'}});
    watchers.pickedTime(form.pickedTime);
    datepicker.calendar.context.selectedTime = '23:30';
    form.resetState();
    assert.equal(form.pickedTime, '');
    watchers.pickedTime(form.pickedTime);
    assert.equal(datepicker.calendar.selectedTime, undefined);

    // A picked day takes over the calendar's default time instead
    datepicker.listeners['change.bs.datepicker']({dates: ['2030-01-05']});
    assert.equal(form.pickedTime, '00:00');
});

// --------- #3: metrics blocks ---------

const CHART_LABELS = {
    noData: 'no data',
    incidentStarted: 'Incident started',
    incidentResolved: 'Incident resolved',
    latency: 'Latency',
    packetLoss: 'Packet loss',
    cpuUsage: 'CPU',
    memoryUsage: 'Memory',
    memoryLimit: 'Memory limit',
    containerRestarted: 'The container has been restarted',
    containerRestartedDetails: 'Restarts since the last check: {delta} (total: {total})',
    latencyHighest: 'Latency (highest)',
    packetLossHighest: 'Packet loss (highest)',
    cpuUsageHighest: 'CPU (highest)',
    memoryUsageHighest: 'Memory (highest)',
};
const MARKER_COLORS = {started: 'red', resolved: 'green', restarted: 'yellow'};
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

// The incidents of a metrics chart, as the v3 API returns them
const incidentPage = (incidents) =>
    ({result: incidents, page: 1, size: CHART_INCIDENTS_PAGE_SIZE, totalCount: incidents.length, totalPages: 1});

// Replaces the global fetch for the duration of a single test
const stubFetch = (t, implementation) => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = implementation;
    t.after(() => {
        globalThis.fetch = originalFetch;
    });
};

// The values of a chart series, without the times of the points
const valuesOf = (series) => series.data.map(point => point.y);

test('httpMetricsBlock.transformData maps the checks to a single series, without a band', () => {
    const block = httpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    const result = block.transformData({
        points: [
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: 123, maxLatencyInMs: null},
            {createdAt: '2024-01-01T00:01:00Z', latencyInMs: 200, maxLatencyInMs: null},
        ],
    }, []);
    assert.equal(result.series.length, 1);
    assert.equal(result.series[0].name, 'Latency');
    assert.equal(result.series[0].type, 'area');
    assert.deepEqual(result.series[0].data, [
        {x: Date.parse('2024-01-01T00:00:00Z'), y: 123},
        {x: Date.parse('2024-01-01T00:01:00Z'), y: 200},
    ]);
    assert.deepEqual(result.annotations, {xaxis: [], points: []});
});

test('httpMetricsBlock.transformData adds the band of the highest latencies of the buckets of a long period', () => {
    const block = httpMetricsBlock(1, true, 60, CHART_LABELS, 'PT720H');
    const result = block.transformData({
        points: [
            {createdAt: '2024-01-01T01:00:00Z', latencyInMs: 40, maxLatencyInMs: 900},
            // A bucket of only failed checks is a gap in both
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: null, maxLatencyInMs: null},
        ],
    }, []);
    assert.deepEqual(result.series.map(series => [series.name, series.type]), [
        ['Latency', 'area'],
        ['Latency (highest)', 'rangeArea'],
    ]);
    assert.deepEqual(valuesOf(result.series[0]), [40, null]);
    assert.deepEqual(result.series[1].data, [
        {x: Date.parse('2024-01-01T01:00:00Z'), y: [40, 900]},
        {x: Date.parse('2024-01-01T00:00:00Z'), y: null},
    ]);
});

test('icmpMetricsBlock.transformData merges latency and packet loss into a single chart', () => {
    const block = icmpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    const result = block.transformData({
        points: [
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: null, packetLossPercentage: 50},
            {createdAt: '2024-01-01T00:01:00Z', latencyInMs: 80, packetLossPercentage: 0},
        ],
    }, []);
    // Both series are rendered differently, on an axis of their own
    assert.deepEqual(result.series.map(series => series.name), ['Latency', 'Packet loss']);
    assert.deepEqual(result.series.map(series => series.type), ['area', 'line']);
    // Null latency must stay null (a gap in the chart), not become NaN
    assert.deepEqual(valuesOf(result.series[0]), [null, 80]);
    assert.deepEqual(valuesOf(result.series[1]), [50, 0]);
});

test('icmpMetricsBlock.transformData adds a band to both the latency and the packet loss of a long period', () => {
    const block = icmpMetricsBlock(1, true, 60, CHART_LABELS, 'PT168H');
    const result = block.transformData({
        points: [{
            createdAt: '2024-01-01T00:00:00Z',
            latencyInMs: 80,
            maxLatencyInMs: 300,
            packetLossPercentage: 2,
            maxPacketLossPercentage: 100,
        }],
    }, []);
    // The bands come last, so the colors and the axes of the other series keep their places
    assert.deepEqual(result.series.map(series => series.name),
        ['Latency', 'Packet loss', 'Latency (highest)', 'Packet loss (highest)']);
    assert.deepEqual(result.series[2].data[0].y, [80, 300]);
    assert.deepEqual(result.series[3].data[0].y, [2, 100]);
});

test('tcpMetricsBlock.transformData preserves null latency and has no packet-loss series', () => {
    const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    const result = block.transformData({
        points: [
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: null},
            {createdAt: '2024-01-01T00:01:00Z', latencyInMs: 80},
        ],
    }, []);
    // Null latency must stay null (a gap in the chart), not become NaN
    assert.deepEqual(valuesOf(result.series[0]), [null, 80]);
    // TCP monitors track latency only - there is no packet-loss series
    assert.equal(result.series.length, 1);
});

test('transformData marks the incidents of the whole selected period, not only of the range of the logs', () => {
    const block = metricsBlockAt(tcpMetricsBlock, NOW);
    const result = block.transformData({points: METRICS_LOGS_OF_AN_HOUR}, [
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
    const result = block.transformData({points: []}, [
        {incidentType: 'HTTP', startedAt: '2023-12-31T12:00:00Z', endedAt: null, details: 'Connection refused'},
    ]);
    assert.deepEqual(result.series[0].data, []);
    assert.deepEqual(result.range, {start: NOW - ONE_DAY_IN_MILLIS, end: NOW});
    assert.deepEqual(result.annotations.points.map(point => point.x), [Date.parse('2023-12-31T12:00:00Z')]);
});

test('transformData extends the range to the data newer than the local clock', () => {
    const block = metricsBlockAt(dnsMetricsBlock, NOW);
    const result = block.transformData({points: [{createdAt: '2024-01-01T02:00:01Z', latencyInMs: 5}]}, [
        {incidentType: 'DNS', startedAt: '2024-01-01T02:00:01.004Z', endedAt: '2024-01-01T02:00:02Z', details: null},
    ]);
    assert.deepEqual(result.range, {start: NOW - ONE_DAY_IN_MILLIS, end: Date.parse('2024-01-01T02:00:02Z')});
    assert.equal(result.annotations.points.length, 2);
});

test('transformData displays the selected period up to the current time', () => {
    const block = metricsBlockAt(icmpMetricsBlock, NOW);
    block.period = 'PT720H';
    const result = block.transformData({points: []}, []);
    assert.deepEqual(result.range, {start: NOW - 30 * ONE_DAY_IN_MILLIS, end: NOW});
});

// Stubs the theme colors (hex values, by their CSS custom property), and the canvas they're resolved with
const stubThemeColors = (t, colors) => {
    const originalGetComputedStyle = globalThis.getComputedStyle;
    const originalCreateElement = globalThis.document.createElement;
    globalThis.getComputedStyle = () => ({getPropertyValue: (name) => colors[name] ?? ''});
    globalThis.document.body = {};
    globalThis.document.createElement = () => ({
        getContext: () => ({
            fillStyle: '',
            clearRect() {},
            fillRect() {},
            getImageData() {
                return {data: [1, 3, 5].map((start) => parseInt(this.fillStyle.slice(start, start + 2), 16))};
            },
        }),
    });
    t.after(() => {
        globalThis.getComputedStyle = originalGetComputedStyle;
        globalThis.document.createElement = originalCreateElement;
        delete globalThis.document.body;
    });
};

test('a metrics chart takes the colors of the theme again, when it is switched between dark and light', (t) => {
    // The inverted accent, in the light mode
    const colors = {'--tblr-primary': '#1f2937', '--tblr-orange': '#f76707', '--tblr-purple': '#ae3ec9'};
    stubThemeColors(t, colors);
    stubThemeEnvironment(t);
    const originalWindow = globalThis.window;
    const originalApexCharts = globalThis.ApexCharts;
    const windowListeners = {};
    globalThis.window = {
        addEventListener: (name, listener) => windowListeners[name] = listener,
        dispatchEvent: (event) => windowListeners[event.type]?.(event),
    };
    const charts = [];
    globalThis.ApexCharts = class {
        constructor(element, options) {
            this.options = options;
            this.updates = [];
            charts.push(this);
        }
        render() {}
        updateOptions(options) { this.updates.push(options); }
    };
    t.after(() => {
        globalThis.window = originalWindow;
        globalThis.ApexCharts = originalApexCharts;
    });
    stubFetch(t, () => new Promise(() => {}));

    const block = icmpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    block.$watch = () => {};
    block.init();
    const [chart] = charts;
    // The bands of the highest readings take the colors of their series
    assert.deepEqual(chart.options.colors, ['#1f2937', '#f76707', '#1f2937', '#f76707']);

    colors['--tblr-primary'] = '#fafafa';
    setTheme('dark');
    assert.deepEqual(chart.updates, [{
        colors: ['#fafafa', '#f76707', '#fafafa', '#f76707'],
        fill: {type: 'solid', opacity: [0.16, 1, 0.3, 0.3]},
    }]);
});

test('updateChart spans the time axis over the displayed range', () => {
    const block = metricsBlockAt(tcpMetricsBlock, NOW);
    const updates = [];
    block.chart = {updateOptions: (options) => updates.push(options)};
    const data = block.transformData({points: METRICS_LOGS_OF_AN_HOUR}, []);

    block.updateChart(data);

    assert.deepEqual(updates, [{
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

const CONTAINER_CREATED_AT = '2023-12-01T00:00:00.914442Z';
const aDockerLog = (createdAt, restartCount, containerCreatedAt = CONTAINER_CREATED_AT) => ({
    createdAt, restartCount, containerCreatedAt, cpuUsagePercent: 1, memoryUsageBytes: 1024, memoryLimitBytes: 2048,
});
const buildRestarts = (logs) => buildRestartAnnotations(logs, RANGE_START, RANGE_END, CHART_LABELS, MARKER_COLORS);

test('buildRestartAnnotations marks the checks that found the restart count increased', () => {
    // Newest first, the way the API returns them
    const result = buildRestarts([
        aDockerLog('2024-01-01T04:00:00Z', 5),
        aDockerLog('2024-01-01T03:00:00Z', 2),
        aDockerLog('2024-01-01T02:00:00Z', 2),
        aDockerLog('2024-01-01T01:00:00Z', 1),
    ]);

    assert.deepEqual(result.xaxis.map(line => line.x), [
        Date.parse('2024-01-01T02:00:00Z'),
        Date.parse('2024-01-01T04:00:00Z'),
    ]);
    const [first, second] = result.points;
    assert.equal(first.marker.fillColor, 'yellow');
    assert.match(first.tooltip.text, /The container has been restarted/);
    assert.match(first.tooltip.text, /Restarts since the last check: 1 \(total: 2\)/);
    assert.match(second.tooltip.text, /Restarts since the last check: 3 \(total: 5\)/);
});

test('buildRestartAnnotations does not mark a count reset to zero by a manual restart', () => {
    const result = buildRestarts([
        aDockerLog('2024-01-01T01:00:00Z', 4),
        aDockerLog('2024-01-01T02:00:00Z', 0),
    ]);

    assert.deepEqual(result, {xaxis: [], points: []});
});

test('buildRestartAnnotations counts the restarts after a manual restart from zero', () => {
    const result = buildRestarts([
        aDockerLog('2024-01-01T01:00:00Z', 4),
        aDockerLog('2024-01-01T02:00:00Z', 1),
    ]);

    assert.deepEqual(result.xaxis.map(line => line.x), [Date.parse('2024-01-01T02:00:00Z')]);
    assert.match(result.points[0].tooltip.text, /Restarts since the last check: 1 \(total: 1\)/);
});

test('buildRestartAnnotations does not compare the counts of a recreated container with the old one', () => {
    const result = buildRestarts([
        aDockerLog('2024-01-01T01:00:00Z', 1),
        aDockerLog('2024-01-01T02:00:00Z', 3, '2024-01-01T01:30:00.123456Z'),
        aDockerLog('2024-01-01T03:00:00Z', 4, '2024-01-01T01:30:00.123456Z'),
    ]);

    assert.deepEqual(result.xaxis.map(line => line.x), [Date.parse('2024-01-01T03:00:00Z')]);
    assert.match(result.points[0].tooltip.text, /Restarts since the last check: 1 \(total: 4\)/);
});

test('buildRestartAnnotations does not mark a count without the creation time of its container', () => {
    const result = buildRestarts([
        aDockerLog('2024-01-01T01:00:00Z', 1, null),
        aDockerLog('2024-01-01T02:00:00Z', 2, null),
    ]);

    assert.deepEqual(result, {xaxis: [], points: []});
});

test('buildRestartAnnotations compares across the logs without a restart count', () => {
    const result = buildRestarts([
        aDockerLog('2024-01-01T01:00:00Z', 1),
        aDockerLog('2024-01-01T02:00:00Z', null),
        aDockerLog('2024-01-01T03:00:00Z', 2),
    ]);

    assert.deepEqual(result.xaxis.map(line => line.x), [Date.parse('2024-01-01T03:00:00Z')]);
});

test('buildRestartAnnotations only marks the restarts within the range, but compares against an earlier log', () => {
    const result = buildRestarts([
        aDockerLog('2023-12-31T23:00:00Z', 1),
        aDockerLog('2024-01-01T01:00:00Z', 2),
        aDockerLog('2024-01-01T13:00:00Z', 3),
    ]);

    assert.deepEqual(result.xaxis.map(line => line.x), [Date.parse('2024-01-01T01:00:00Z')]);
});

test('dockerMetricsBlock.transformData shows the restart markers alongside the incident markers', () => {
    const block = metricsBlockAt(dockerMetricsBlock, NOW);
    const result = block.transformData({
        points: [],
        restarts: [aDockerLog('2024-01-01T01:30:00Z', 2), aDockerLog('2024-01-01T01:00:00Z', 1)],
    }, [anIncident({startedAt: '2024-01-01T01:10:00Z', endedAt: null})]);

    assert.deepEqual(result.annotations.points.map(point => point.marker.fillColor), ['red', 'yellow']);
});

test('dockerMetricsBlock.transformData adds a band to the CPU and the memory usage of a long period, not to the limit', () => {
    const block = metricsBlockAt(dockerMetricsBlock, NOW);
    block.period = 'PT720H';
    const result = block.transformData({
        points: [{
            createdAt: '2024-01-01T00:00:00Z',
            cpuUsagePercent: 1.5,
            maxCpuUsagePercent: 42.25,
            memoryUsageBytes: 1048576,
            maxMemoryUsageBytes: 3145728,
            memoryLimitBytes: 8388608,
        }],
        restarts: [],
    }, []);
    assert.deepEqual(result.series.map(series => series.name),
        ['CPU', 'Memory', 'Memory limit', 'CPU (highest)', 'Memory (highest)']);
    assert.deepEqual(result.series[3].data[0].y, [1.5, 42.25]);
    // In MiB, like the memory usage itself
    assert.deepEqual(result.series[4].data[0].y, [1, 3]);
    assert.deepEqual(valuesOf(result.series[2]), [8]);
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

test('buildIncidentAnnotations escapes the details of an incident in the tooltip', () => {
    const [start] = buildAnnotations([anIncident({details: '<img src=x onerror=alert(1)>'})]).points;
    assert.ok(!start.tooltip.text.includes('<img'));
    assert.ok(start.tooltip.text.includes('&lt;img src=x onerror=alert(1)&gt;'));
});

test('buildIncidentAnnotations leaves the details out of the tooltip of an incident without any', () => {
    const [start] = buildAnnotations([anIncident({details: null})]).points;
    assert.equal(start.tooltip.text.split('<div>').length - 1, 1);
});

// The parts of a tooltip of a metrics chart that are read: the title and the label and the value of every row
const withUnit = (unit) => (value) => value === null ? '-' : `${value}${unit}`;
const tooltipTextOf = (html) => [...html.matchAll(/apexcharts-tooltip-(?:title|text-y-label|text-y-value)">([^<]*)</g)]
    .map(match => match[1]);

test('metricsChartTooltip shows the highest reading of a band, and the value of every other visible series', () => {
    const x = new Date(2024, 0, 2, 3, 4, 5).getTime();
    const tooltip = metricsChartTooltip([withUnit(' ms'), withUnit('%'), withUnit(' ms')]);
    const html = tooltip({
        dataPointIndex: 1,
        w: {
            config: {
                series: [
                    {name: 'Latency', data: [{x: 0, y: 1}, {x, y: 40}]},
                    {name: 'Packet loss', data: [{x: 0, y: 0}, {x, y: 5}]},
                    {name: 'Latency <highest>', data: [{x: 0, y: [1, 2]}, {x, y: [40, 900]}]},
                ],
            },
            globals: {collapsedSeriesIndices: [1], colors: ['blue', 'orange', 'blue']},
        },
    });

    // The collapsed packet loss is left out, and the names are escaped
    assert.deepEqual(tooltipTextOf(html),
        ['2024/01/02 03:04:05', 'Latency: ', '40 ms', 'Latency &lt;highest&gt;: ', '900 ms']);
    assert.match(html, /color: blue/);
});

test('metricsChartTooltip formats a missing reading too, and leaves out the series without a point there', () => {
    const tooltip = metricsChartTooltip([withUnit(' ms'), withUnit(' ms')]);
    const html = tooltip({
        dataPointIndex: 0,
        w: {
            config: {
                series: [
                    {name: 'Latency', data: [{x: 1000, y: null}]},
                    {name: 'Latency (highest)', data: []},
                ],
            },
            globals: {collapsedSeriesIndices: [], colors: ['blue', 'blue']},
        },
    });

    assert.deepEqual(tooltipTextOf(html).slice(1), ['Latency: ', '-']);
});

test('formatChartTimestamp pads every part of the date to two digits', () => {
    assert.equal(formatChartTimestamp(new Date(2024, 0, 2, 3, 4, 5)), '2024/01/02 03:04:05');
});

test('the metrics blocks fetch their chart and every uptime incident of their type for the selected period', () => {
    const cases = [
        [httpMetricsBlock, 'http', 'HTTP'],
        [icmpMetricsBlock, 'icmp', 'ICMP'],
        [tcpMetricsBlock, 'tcp', 'TCP'],
        [dnsMetricsBlock, 'dns', 'DNS'],
        [dockerMetricsBlock, 'docker', 'DOCKER'],
    ];
    const incidentsUrl = (type, period) => `/api/v3/incidents/${type}?monitorId=42&period=${period}` +
        `&includeResolved=true&page=1&size=${CHART_INCIDENTS_PAGE_SIZE}`;
    for (const [factory, chartPath, incidentType] of cases) {
        const block = factory(42, true, 60, CHART_LABELS, 'PT24H');
        assert.equal(block.chartUrl(), `/api/internal/monitor-charts/${chartPath}/42?period=PT24H`);
        assert.equal(block.incidentsUrl(), incidentsUrl(incidentType, 'PT24H'));

        block.period = 'PT168H';
        assert.equal(block.chartUrl(), `/api/internal/monitor-charts/${chartPath}/42?period=PT168H`);
        assert.equal(block.incidentsUrl(), incidentsUrl(incidentType, 'PT168H'));
    }
});

test('the incidents of the chart are read from the single page they are fetched in', async (t) => {
    const warn = t.mock.method(console, 'warn', () => {});
    const incident = {incidentType: 'TCP', startedAt: '2024-01-01T00:30:00Z', endedAt: null, details: null};
    const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    stubFetch(t, async () => jsonResponse({result: [incident], page: 1, size: 1, totalCount: 1, totalPages: 1}));

    assert.deepEqual(await block.fetchIncidents(), [incident]);
    assert.equal(warn.mock.callCount(), 0);
});

test('a warning is logged when the incidents of the chart do not fit into a single page', async (t) => {
    const warn = t.mock.method(console, 'warn', () => {});
    const incident = {incidentType: 'TCP', startedAt: '2024-01-01T00:30:00Z', endedAt: null, details: null};
    const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    stubFetch(t, async () => jsonResponse({result: [incident], page: 1, size: 1, totalCount: 3, totalPages: 3}));

    assert.deepEqual(await block.fetchIncidents(), [incident]);
    assert.equal(warn.mock.callCount(), 1);
    assert.match(warn.mock.calls[0].arguments[0], /first 1 of the 3 incidents/);
});

test('changing the period drops the previous data and polls the metrics right away', (t) => {
    const originalWindow = globalThis.window;
    globalThis.window = {addEventListener() {}};
    t.after(() => { globalThis.window = originalWindow; });
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
    const stats = {points: METRICS_LOGS_OF_AN_HOUR};
    stubFetch(t, async (url) => url.startsWith('/api/v3/incidents')
        ? jsonResponse(incidentPage([{incidentType: 'TCP', startedAt: '2024-01-01T00:30:00Z', endedAt: null, details: null}]))
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
    const stats = {points: METRICS_LOGS_OF_AN_HOUR};
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
        stubFetch(t, async (url) => url.startsWith('/api/v3/incidents') ? failure() : jsonResponse(stats));

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
    stubFetch(t, async (url) => url.startsWith('/api/v3/incidents') ? jsonResponse(incidentPage([])) : {ok: false, status: 500});

    await block.pollEndpoint();

    assert.equal(block.lastResponse, null);
    assert.equal(rendered.length, 0);
});

test('pollEndpoint discards the response of a period that has been changed in the meantime', async (t) => {
    const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
    const rendered = [];
    block.updateChart = (data) => rendered.push(data);
    let resolveStats;
    stubFetch(t, (url) => url.startsWith('/api/v3/incidents')
        ? Promise.resolve(jsonResponse(incidentPage([])))
        : new Promise(resolve => {
            resolveStats = resolve;
        }));

    const polling = block.pollEndpoint();
    // What the watcher of the period does
    block.period = 'PT168H';
    block.isPeriodLoading = true;
    resolveStats(jsonResponse({points: METRICS_LOGS_OF_AN_HOUR}));
    await polling;

    // The loading of the newly selected period is still in progress
    assert.equal(block.isPeriodLoading, true);

    assert.equal(block.lastResponse, null);
    assert.equal(rendered.length, 0);
});

test('changing the period shows the loading state until the response of the new period is handled', async (t) => {
    t.mock.method(console, 'error', () => {});
    const cases = [
        [jsonResponse({points: METRICS_LOGS_OF_AN_HOUR}), 1],
        // A failed request must not leave the block loading forever
        [{ok: false, status: 500}, 0],
    ];
    for (const [statsResponse, expectedRenderCount] of cases) {
        const block = tcpMetricsBlock(1, true, 60, CHART_LABELS, 'PT24H');
        const rendered = [];
        block.updateChart = (data) => rendered.push(data);
        let resolveStats;
        stubFetch(t, (url) => url.startsWith('/api/v3/incidents')
            ? Promise.resolve(jsonResponse(incidentPage([])))
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
        return url.startsWith('/api/v3/incidents')
            ? jsonResponse(incidentPage([]))
            : jsonResponse({points: METRICS_LOGS_OF_AN_HOUR});
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
    const buildForm = () => upsertTcpMonitorForm(null, msgs, 'category-select', false, 'proxy-select', [], '(not configured)', 0);

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

test('Docker validators reject a host that is not configured, but only on creation', () => {
    const msgs = {
        dockerHostRequired: 'REQ', dockerHostNotConfigured: 'NC', containerRequired: 'CR', timeoutMsInvalid: 'TS',
    };
    const buildForm = () => upsertDockerMonitorForm(
        null, msgs, 'category-select', false, 'host-select', 'container-select', ['local'], '(not configured)', 0,
    );

    // A host that is not configured is what a clone of a monitor with a since-removed host would carry
    assertValidatorBoundaries(buildForm, 'dockerHost', 'validateDockerHost', 'REQ', [['', true], ['local', false]]);
    assertValidatorBoundaries(buildForm, 'dockerHost', 'validateDockerHost', 'NC', [['removed', true]]);
    assertValidatorBoundaries(buildForm, 'container', 'validateContainer', 'CR', [[' ', true], ['my-app', false]]);
    assertValidatorBoundaries(buildForm, 'timeoutMs', 'validateTimeoutMs', 'TS', [
        [0, true], [1, false], [30000, false], [30001, true],
    ]);

    // The monitor that already has such a host may keep it
    const update = buildForm();
    Object.assign(update, {errors: {}, isUpdate: true, dockerHost: 'removed'});
    update.validateDockerHost();
    assert.equal(update.errors.dockerHost, null);
});

test('Docker host select re-offers a host that is no longer configured, marked as such', () => {
    const tomSelect = fakeTomSelect();
    const form = upsertDockerMonitorForm(
        null, {}, 'category-select', false, 'host-select', 'container-select', ['local'], '(not configured)', 0,
    );

    withCategorySelect(tomSelect, () => form.populateTypeFields({dockerHost: 'removed', container: 'my-app'}));

    assert.deepEqual(tomSelect.options.slice(0, 2), [
        {value: 'local', text: 'local'},
        {value: 'removed', text: 'removed (not configured)'},
    ]);
    assert.equal(form.dockerHost, 'removed');
});

test('Docker populateTypeFields copies the toggles and falls back to them being off', () => {
    const form = upsertDockerMonitorForm(
        null, {}, 'category-select', false, 'host-select', 'container-select', ['local'], '(not configured)', 0,
    );

    withCategorySelect(fakeTomSelect(), () => form.populateTypeFields({
        dockerHost: 'local', container: 'my-app', uptimeCheckInterval: 120, timeoutMs: 10000,
        metricsHistoryEnabled: true, restartAlertEnabled: true,
    }));
    assert.deepEqual(form.typeRequestBody(), {
        dockerHost: 'local', container: 'my-app', uptimeCheckInterval: 120, timeoutMs: 10000,
        metricsHistoryEnabled: true, restartAlertEnabled: true,
    });

    withCategorySelect(fakeTomSelect(), () => form.populateTypeFields(null));
    assert.equal(form.metricsHistoryEnabled, false);
    assert.equal(form.restartAlertEnabled, false);
});

const proxiedFormFactories = {
    HTTP: (errorMessages = {}) => upsertHttpMonitorForm(
        null, errorMessages, 'category-select', false, 'select', [], 'proxy-select', ['egress'], '(not configured)', 0,
    ),
    TCP: (errorMessages = {}) => upsertTcpMonitorForm(
        null, errorMessages, 'category-select', false, 'proxy-select', ['egress'], '(not configured)', 0,
    ),
};

Object.entries(proxiedFormFactories).forEach(([type, buildProxiedForm]) => {
    test(`${type} proxy validator rejects a proxy that is not configured, unless the monitor already had it`, () => {
        const buildForm = () => buildProxiedForm({proxyNotConfigured: 'NC'});

        // No proxy means a direct connection, which is always fine
        assertValidatorBoundaries(buildForm, 'proxy', 'validateProxy', 'NC', [['', false], [null, false], ['egress', false]]);
        // A proxy that is not configured is what a clone of a monitor with a since-removed proxy would carry
        assertValidatorBoundaries(buildForm, 'proxy', 'validateProxy', 'NC', [['removed', true]]);

        // The monitor that already has such a proxy may keep it...
        const update = buildForm();
        update.populateTypeFields({proxy: 'removed'});
        Object.assign(update, {errors: {}, isUpdate: true});
        update.validateProxy();
        assert.equal(update.errors.proxy, null);

        // ...but it can't be pointed to another proxy that is not configured either
        update.proxy = 'other-removed';
        update.validateProxy();
        assert.equal(update.errors.proxy, 'NC');
    });

    test(`${type} proxy is validated with the rest of the form`, () => {
        const form = buildProxiedForm({proxyNotConfigured: 'NC'});
        form.populateFrom(null);
        form.proxy = 'removed';

        form.validate();

        assert.equal(form.errors.proxy, 'NC');
    });

    test(`${type} proxy select re-offers a proxy that is no longer configured, marked as such`, () => {
        const tomSelect = fakeTomSelect();
        const form = buildProxiedForm();

        withCategorySelect(tomSelect, () => form.populateTypeFields({proxy: 'removed'}));

        assert.deepEqual(tomSelect.options, [
            {value: 'egress', text: 'egress'},
            {value: 'removed', text: 'removed (not configured)'},
        ]);
        assert.deepEqual(tomSelect.items, ['removed']);
        assert.equal(form.proxy, 'removed');
        assert.equal(form.storedProxy, 'removed');
    });

    test(`${type} proxy select only offers the configured proxies without a stored one`, () => {
        const tomSelect = fakeTomSelect();
        const form = buildProxiedForm();

        withCategorySelect(tomSelect, () => form.populateTypeFields({proxy: 'egress'}));
        assert.deepEqual(tomSelect.options, [{value: 'egress', text: 'egress'}]);
        assert.deepEqual(tomSelect.items, ['egress']);
        assert.equal(form.proxy, 'egress');

        const directTomSelect = fakeTomSelect();
        withCategorySelect(directTomSelect, () => form.populateTypeFields(null));
        assert.deepEqual(directTomSelect.options, [{value: 'egress', text: 'egress'}]);
        assert.deepEqual(directTomSelect.items, []);
        assert.equal(form.proxy, '');
    });

    test(`${type} proxy select drops the dangling proxy of the previous monitor in the shared modal`, () => {
        const tomSelect = fakeTomSelect();
        const form = buildProxiedForm();

        withCategorySelect(tomSelect, () => {
            form.populateTypeFields({proxy: 'removed'});
            form.populateTypeFields({proxy: 'egress'});
        });

        assert.deepEqual(tomSelect.options, [{value: 'egress', text: 'egress'}]);
        assert.equal(form.proxy, 'egress');
    });

    test(`${type} request body sends the proxy, or null for a direct connection`, () => {
        const form = buildProxiedForm();

        form.populateFrom({name: 'proxied', proxy: 'egress'});
        assert.equal(form.buildRequestBody().proxy, 'egress');

        form.proxy = '';
        assert.equal(form.buildRequestBody().proxy, null);
    });
});

test('Docker containers are listed only while the modal is open', () => {
    const listeners = {};
    const modal = {addEventListener: (event, listener) => listeners[event] = listener};
    const originalGetElementById = document.getElementById;
    document.getElementById = () => ({closest: () => modal});
    try {
        const form = upsertDockerMonitorForm(
            null, {}, 'category-select', false, 'host-select', 'container-select', ['local'], '(not configured)', 0,
        );
        let loads = 0;
        form.loadContainers = () => loads++;
        form.watchModal();

        // The initial populate of a rendered page, and the reset after a close, must not ask the daemon
        form.populateFrom({dockerHost: 'local', container: 'my-app'});
        assert.equal(loads, 0);

        listeners['show.bs.modal']();
        assert.equal(loads, 1);
        // A list row loaded into the open modal lists the containers of its own host
        form.populateFrom({dockerHost: 'local', container: 'other-app'});
        assert.equal(loads, 2);

        listeners['hide.bs.modal']();
        form.populateFrom(null);
        assert.equal(loads, 2);
    } finally {
        document.getElementById = originalGetElementById;
    }
});

test('a listed Docker container is offered with its image and a badge of its raw state', () => {
    const html = renderDockerContainerOption(
        {value: 'web', text: 'web', image: 'nginx:alpine', state: 'running'}, escapeHtml,
    );

    assert.match(html, /web<span class="badge ms-2 bg-green-lt text-green-lt-fg">running<\/span>/);
    assert.match(html, /<div class="text-secondary small">nginx:alpine<\/div>/);
    assert.match(renderDockerContainerOption({text: 'w', state: 'exited'}, escapeHtml), /bg-red-lt/);
    assert.match(renderDockerContainerOption({text: 'w', state: 'paused'}, escapeHtml), /bg-orange-lt/);
    assert.match(renderDockerContainerOption({text: 'w', state: 'created'}, escapeHtml), /bg-secondary-lt/);
});

test('a Docker container that was not listed, e.g. a typed in one, is offered by its name only', () => {
    assert.equal(renderDockerContainerOption({value: '<x>', text: '<x>'}, escapeHtml), '<div><div>&lt;x&gt;</div></div>');
});

// Like TomSelect's, clearOptions() keeps the selected option, and addOption() replaces an existing one
const stubContainerTomSelect = (t, options = {}) => {
    const wrapperClasses = new Set();
    const tomSelect = {
        options,
        loading: 0,
        settings: {loadingClass: 'loading'},
        wrapper: {classList: {add: (c) => wrapperClasses.add(c), remove: (c) => wrapperClasses.delete(c)}},
        wrapperClasses,
        clearOptions() {},
        addOption(option) {
            this.options[option.value] = option;
        },
        addItem() {},
        refreshOptions() {},
    };
    const originalGetElementById = document.getElementById;
    document.getElementById = () => ({tomselect: tomSelect});
    t.after(() => document.getElementById = originalGetElementById);
    return tomSelect;
};

test('the listing gives the details to the container that is already selected, too', async (t) => {
    const tomSelect = stubContainerTomSelect(t, {web: {value: 'web', text: 'web'}});
    stubFetch(t, async () => jsonResponse({
        available: true,
        containers: [
            {name: 'web', image: 'nginx:alpine', state: 'running'},
            {name: 'worker', image: 'busybox', state: 'exited'},
        ],
    }));
    const form = upsertDockerMonitorForm(
        null, {}, 'category-select', false, 'host-select', 'container-select', ['local'], '(not configured)', 0,
    );
    Object.assign(form, {dockerHost: 'local', container: 'web'});

    await form.loadContainers();

    assert.deepEqual(tomSelect.options.web, {value: 'web', text: 'web', image: 'nginx:alpine', state: 'running'});
    assert.deepEqual(tomSelect.options.worker, {value: 'worker', text: 'worker', image: 'busybox', state: 'exited'});
});

test('the container select is in its loading state only while the listing is in flight', async (t) => {
    const tomSelect = stubContainerTomSelect(t);
    let respond;
    stubFetch(t, () => new Promise(resolve => respond = resolve));
    const form = upsertDockerMonitorForm(
        null, {}, 'category-select', false, 'host-select', 'container-select', ['local'], '(not configured)', 0,
    );
    Object.assign(form, {dockerHost: 'local', container: ''});

    const loading = form.loadContainers();
    assert.equal(tomSelect.loading, 1);
    assert.ok(tomSelect.wrapperClasses.has('loading'));

    respond(jsonResponse({available: true, containers: []}));
    await loading;
    assert.equal(tomSelect.loading, 0);
    assert.ok(!tomSelect.wrapperClasses.has('loading'));
});

test('a late listing of a host that is not selected anymore is dropped', async (t) => {
    const tomSelect = stubContainerTomSelect(t);
    const pending = {};
    stubFetch(t, (url) => new Promise(resolve => pending[url.includes('/slow/') ? 'slow' : 'fast'] = resolve));
    const form = upsertDockerMonitorForm(
        null, {}, 'category-select', false, 'host-select', 'container-select', ['slow', 'fast'], '(not configured)', 0,
    );
    Object.assign(form, {dockerHost: 'slow', container: ''});
    const slowLoad = form.loadContainers();
    form.dockerHost = 'fast';
    const fastLoad = form.loadContainers();
    assert.equal(tomSelect.loading, 2);

    pending.fast(jsonResponse({available: true, containers: [{name: 'web', image: 'nginx', state: 'running'}]}));
    await fastLoad;
    // The other listing is still in flight, so the widget keeps loading
    assert.ok(tomSelect.wrapperClasses.has('loading'));
    pending.slow(jsonResponse({available: false, containers: []}));
    await slowLoad;

    assert.deepEqual(Object.keys(tomSelect.options), ['web']);
    assert.equal(form.containerLoadFailed, false);
    assert.equal(tomSelect.loading, 0);
    assert.ok(!tomSelect.wrapperClasses.has('loading'));
});

test('switching the Docker host drops the container of the previous one, and switching back restores it', (t) => {
    const tomSelect = stubContainerTomSelect(t);
    let cleared = 0;
    tomSelect.clear = () => cleared++;
    const form = upsertDockerMonitorForm(
        null, {}, 'category-select', false, 'host-select', 'container-select', ['homelab', 'local'], '(not configured)', 0,
    );
    form.errors = {};
    let loads = 0;
    form.loadContainers = () => loads++;
    Object.assign(form, {dockerHost: 'homelab', container: 'bentopdf', storedDockerHost: 'homelab', storedContainer: 'bentopdf'});

    form.dockerHost = 'local';
    form.onDockerHostChanged();
    assert.equal(form.container, '');
    assert.equal(cleared, 1);

    form.dockerHost = 'homelab';
    form.onDockerHostChanged();
    assert.equal(form.container, 'bentopdf');
    assert.equal(loads, 2);
});

test('a Docker container picked on a new monitor is dropped when its host is switched', (t) => {
    const tomSelect = stubContainerTomSelect(t);
    let cleared = 0;
    tomSelect.clear = () => cleared++;
    const form = upsertDockerMonitorForm(
        null, {}, 'category-select', false, 'host-select', 'container-select', ['homelab', 'local'], '(not configured)', 0,
    );
    form.errors = {};
    form.loadContainers = () => {};
    Object.assign(form, {dockerHost: 'local', container: 'web'});

    form.dockerHost = 'homelab';
    form.onDockerHostChanged();
    assert.equal(form.container, '');
    assert.equal(cleared, 1);

    // Nothing to drop the second time
    form.dockerHost = 'local';
    form.onDockerHostChanged();
    assert.equal(cleared, 1);
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
    const form = upsertHttpMonitorForm(null, {}, 'category-select', false, 'select', [], 'proxy-select', [], '(not configured)', 0);

    form.populateFrom({
        name: 'Src', url: 'https://example.com', sensitiveUrl: true, sslExpiryThreshold: 14,
        failureCountThreshold: 4, uptimeCheckInterval: 120, sslCheckEnabled: true,
        latencyHistoryEnabled: false, forceNoCache: false, followRedirects: false,
        crossOriginHeaderPropagation: true, proxy: 'egress',
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
    assert.equal(form.proxy, 'egress');
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
    assert.equal(form.proxy, '');
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
    const form = upsertTcpMonitorForm(null, {}, 'category-select', false, 'proxy-select', [], '(not configured)', 0);

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
        points: [
            {createdAt: '2024-01-01T00:00:00Z', latencyInMs: null},
            {createdAt: '2024-01-01T00:01:00Z', latencyInMs: 42},
        ],
    }, []);
    // Null latency must stay null (a gap in the chart), not become NaN
    assert.deepEqual(valuesOf(result.series[0]), [null, 42]);
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
        upsertHttpMonitorForm(null, {}, 'category-select', false, 'select', [], 'proxy-select', [], '(not configured)', 0),
        upsertPushMonitorForm(null, {}, 'category-select', false, 0),
        upsertIcmpMonitorForm(null, {}, 'category-select', false, 0),
        upsertTcpMonitorForm(null, {}, 'category-select', false, 'proxy-select', [], '(not configured)', 0),
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
    const form = upsertHttpMonitorForm(null, {categoryTooLong: 'too long'}, 'category-select', false, 'select', [], 'proxy-select', [], '(not configured)', 0);
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
    // The real one keeps the selected options, but the helpers clear the selection before calling it
    instance.clearOptions = () => {
        instance.options = [];
    };
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

test('resetMultiSelect re-adds and selects every value of the form', () => {
    const tomSelect = fakeTomSelect();
    withCategorySelect(tomSelect, () => resetMultiSelect('categories-select', ['Payments', 'Search']));
    assert.equal(tomSelect.cleared, 1);
    // Same reason as above: an option the endpoint does not offer (yet) is dropped when the selection is cleared
    assert.deepEqual(tomSelect.options, [
        {value: 'Payments', text: 'Payments'},
        {value: 'Search', text: 'Search'},
    ]);
    assert.deepEqual(tomSelect.items, ['Payments', 'Search']);
});

test('resetMultiSelect only clears when the form has no values', () => {
    const tomSelect = fakeTomSelect();
    withCategorySelect(tomSelect, () => resetMultiSelect('categories-select', []));
    assert.equal(tomSelect.cleared, 1);
    assert.deepEqual(tomSelect.options, []);
    assert.deepEqual(tomSelect.items, []);
});

test('resetMultiSelect tolerates a missing list of values', () => {
    const tomSelect = fakeTomSelect();
    withCategorySelect(tomSelect, () => resetMultiSelect('categories-select', undefined));
    assert.equal(tomSelect.cleared, 1);
    assert.deepEqual(tomSelect.items, []);
});

const statusPageWithMonitors = {id: 2, title: 'Status', slug: 'status', monitors: ['http:Site', 'tcp:DB']};
const maintenanceWindowWithMonitors = {id: 2, name: 'Window', monitors: ['http:Site', 'tcp:DB']};

[
    ['status page', () => upsertStatusPageForm(statusPageWithMonitors, {}, 'monitor-select', 'categories-select')],
    ['maintenance window', () => upsertMaintenanceWindowForm(maintenanceWindowWithMonitors, {}, 'monitor-select', 'categories-select')],
].forEach(([entity, createForm]) => {
    test(`the ${entity} form renders only its own monitors as options, and offers them even before the rest arrive`, () => {
        const form = createForm();
        assert.deepEqual(form.initialMonitors, ['http:Site', 'tcp:DB']);

        const tomSelect = fakeTomSelect();
        withCategorySelect(tomSelect, () => form.resetState());
        assert.deepEqual(tomSelect.options.map(option => option.value), ['http:Site', 'tcp:DB']);
        assert.deepEqual(tomSelect.items, ['http:Site', 'tcp:DB']);
    });
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

// A category select of a modal, returning the listener that loads its options whenever the modal is shown
const categorySelectOfModal = (tomSelect) => {
    const listeners = {};
    const modal = {addEventListener: (event, listener) => listeners[event] = listener};
    Object.assign(tomSelect, {
        input: {closest: () => modal},
        refreshed: 0,
        addOptions: (options) => options.forEach(tomSelect.addOption),
        refreshOptions: () => tomSelect.refreshed += 1,
    });
    loadCategoryOptions(tomSelect);
    return listeners['show.bs.modal'];
};

test('loadCategoryOptions offers the categories once the modal is shown', async () => {
    const tomSelect = fakeTomSelect();
    const loadOptions = categorySelectOfModal(tomSelect);
    assert.deepEqual(tomSelect.options, []);

    await withFetch(async () => ({ok: true, json: async () => ['alerting', 'Payments']}), loadOptions);

    assert.deepEqual(tomSelect.options, [
        {value: 'alerting', text: 'alerting'},
        {value: 'Payments', text: 'Payments'},
    ]);
    // Nobody is using the field, so its dropdown is left alone
    assert.equal(tomSelect.refreshed, 0);
});

test('loadCategoryOptions refreshes a field that was opened before the categories arrived', async () => {
    const tomSelect = fakeTomSelect();
    const loadOptions = categorySelectOfModal(tomSelect);
    tomSelect.isFocused = true;

    await withFetch(async () => ({ok: true, json: async () => ['alerting']}), loadOptions);

    assert.deepEqual(tomSelect.options, [{value: 'alerting', text: 'alerting'}]);
    assert.equal(tomSelect.refreshed, 1);
});

test('loadMonitorOptions offers the monitors once the modal is shown', async () => {
    const tomSelect = fakeTomSelect();
    const listeners = {};
    Object.assign(tomSelect, {
        input: {closest: () => ({addEventListener: (event, listener) => listeners[event] = listener})},
        addOptions: (options) => options.forEach(tomSelect.addOption),
    });
    loadMonitorOptions(tomSelect);
    assert.deepEqual(tomSelect.options, []);

    const requestedUrls = [];
    await withFetch(async (url) => {
        requestedUrls.push(url);
        return {ok: true, json: async () => ['tcp:Alpha', 'http:Bravo']};
    }, listeners['show.bs.modal']);

    assert.deepEqual(requestedUrls, ['/api/internal/monitors']);
    assert.deepEqual(tomSelect.options, [
        {value: 'tcp:Alpha', text: 'tcp:Alpha'},
        {value: 'http:Bravo', text: 'http:Bravo'},
    ]);
});

test('fetchMonitors fails open', async (t) => {
    t.mock.method(console, 'error', () => {});
    const failures = [
        async () => ({ok: false, json: async () => ['http:Site']}),
        async () => {
            throw new Error('network down');
        },
    ];
    for (const failure of failures) {
        assert.deepEqual(await withFetch(failure, () => fetchMonitors()), []);
    }
});

// A TomSelect stub with a dropdown and the hooks of its methods, and the hint added to it
const tomSelectWithSearchHint = (t, maxOptions) => {
    const originalCreateElement = globalThis.document.createElement;
    globalThis.document.createElement = () => ({dataset: {}});
    t.after(() => {
        globalThis.document.createElement = originalCreateElement;
    });
    const appended = [];
    const hooks = [];
    const tomSelect = {
        settings: {maxOptions},
        currentResults: null,
        dropdown: {append: (element) => appended.push(element)},
        hook: (when, method, callback) => hooks.push({when, method, callback}),
        refreshOptions() {
            hooks.filter(hook => hook.method === 'refreshOptions').forEach(hook => hook.callback());
        },
    };
    addSearchHint(tomSelect, 'Type to search');
    return {tomSelect, hint: appended[0], hooks};
};

test('the search hint is shown only while more options match than the dropdown shows', (t) => {
    const {tomSelect, hint, hooks} = tomSelectWithSearchHint(t, 2);
    assert.equal(hint.textContent, 'Type to search');
    assert.equal(hint.dataset.testid, 'select-search-hint');
    assert.equal(hint.hidden, true);
    assert.deepEqual(hooks.map(({when, method}) => [when, method]), [['after', 'refreshOptions']]);

    const cases = [
        [[1, 2, 3], false],
        [[1, 2], true],
        [[], true],
    ];
    for (const [matches, isHidden] of cases) {
        tomSelect.currentResults = {items: matches};
        tomSelect.refreshOptions();
        assert.equal(hint.hidden, isHidden, `${matches.length} matches`);
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
        name: 'ping', failureCountThreshold: 1, integrations: [], category: null,
        ignoreConnectivityCheck: false, host: 'example.com',
        uptimeCheckInterval: 60, packetCount: 3, timeoutSeconds: 5, packetLossThreshold: 100,
        metricsHistoryEnabled: true, enabled: true,
    });
    // The connectivity opt-out is a shared field of every monitor type, handled by the common form base
    form.ignoreConnectivityCheck = true;
    assert.equal(form.buildRequestBody().ignoreConnectivityCheck, true);
    form.populateFrom({name: 'ping', host: 'example.com', ignoreConnectivityCheck: true});
    assert.equal(form.ignoreConnectivityCheck, true);
    assert.equal(browser.location.href, '/icmp-monitors/42');
    assert.equal(form.isRequestLoading, false);
});

test('upsert updates an existing monitor without touching its enabled state and reloads the page', async (t) => {
    const browser = stubBrowser(t);
    let isBodyRead = false;
    const requests = stubRequests(t, {ok: true, json: async () => { isBodyRead = true; return {id: 7}; }});
    const form = upsertTcpMonitorForm({id: 7, name: 'db', host: 'db.local', port: '5432'}, {}, 'category-select', false, 'proxy-select', [], '(not configured)', 0);
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
    const statusPageForm = upsertStatusPageForm(null, messages, 'monitor-select', 'categories-select');

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
    const monitorForm = upsertHttpMonitorForm({id: 1, name: 'site'}, messages, 'category-select', false, 'select', [], 'proxy-select', [], '(not configured)', 0);
    const maintenanceWindowForm = upsertMaintenanceWindowForm(null, messages, 'select');

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
    const form = upsertStatusPageForm({id: 2, title: 'Status', slug: 'status'}, {}, 'monitor-select', 'categories-select');
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
    const maintenanceWindowForm = upsertMaintenanceWindowForm(null, {nameRequired: 'NAME'}, 'select');

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
    const buildForm = () => upsertHttpMonitorForm(null, msgs, 'category-select', false, 'select', [], 'proxy-select', [], '(not configured)', 0);

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
    const form = upsertHttpMonitorForm(null, {requestBodyInvalid: 'JSON'}, 'category-select', false, 'select', [], 'proxy-select', [], '(not configured)', 0);
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
        upsertHttpMonitorForm(monitor, {}, 'category-select', isNameLocked, 'select', [], 'proxy-select', [], '(not configured)', 0),
    push: (monitor = null, isNameLocked = false) => upsertPushMonitorForm(monitor, {}, 'category-select', isNameLocked, 0),
    icmp: (monitor = null, isNameLocked = false) => upsertIcmpMonitorForm(monitor, {}, 'category-select', isNameLocked, 0),
    tcp: (monitor = null, isNameLocked = false) => upsertTcpMonitorForm(monitor, {}, 'category-select', isNameLocked, 'proxy-select', [], '(not configured)', 0),
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

test('the status page form sends the picked gray palette', (t) => {
    stubBrowser(t);
    const form = upsertStatusPageForm(
        {id: 2, title: 'Status', slug: 'status', themeBase: 'SLATE'},
        {}, 'monitor-select', 'categories-select', 'GRAY',
    );
    form.resetState();
    assert.equal(form.buildRequestBody().themeBase, 'SLATE');

    form.themeBase = 'ZINC';

    assert.equal(form.buildRequestBody().themeBase, 'ZINC');
});

// --------- Editing a status page or a maintenance window through the upsert modal of its list ---------

const listEditedEntities = {
    'status-pages': {
        createForm: (entity = null) => upsertStatusPageForm(
            entity, {}, 'monitor-select', 'categories-select', 'GRAY',
        ),
        clonedFields: {title: 'Copy of Source', slug: 'source-copy', public: false},
        source: {
            id: 7,
            title: 'Source',
            slug: 'source',
            monitors: ['http:Site'],
            categories: ['Payments'],
            displayCategories: false,
            themeBase: 'SLATE',
            public: true,
        },
        rendered: {id: 3, title: 'Rendered', slug: 'rendered'},
        assertLoaded: (form) => {
            assert.equal(form.title, 'Source');
            assert.equal(form.slug, 'source');
            assert.deepEqual(form.selectedMonitors, ['http:Site']);
            assert.deepEqual(form.selectedCategories, ['Payments']);
            assert.equal(form.displayCategories, false);
            assert.equal(form.themeBase, 'SLATE');
            assert.equal(form.public, true);
        },
        assertBlank: (form) => {
            assert.equal(form.title, '');
            assert.deepEqual(form.selectedCategories, []);
            // An unset palette is preselected with the default it falls back to
            assert.equal(form.themeBase, 'GRAY');
        },
        assertRendered: (form) => assert.equal(form.title, 'Rendered'),
    },
    'maintenance-windows': {
        createForm: (entity = null) => upsertMaintenanceWindowForm(entity, {}, 'monitor-select', 'categories-select'),
        clonedFields: {name: 'Copy of Source', enabled: false},
        source: {
            id: 7,
            name: 'Source',
            cron: '0 2 * * *',
            duration: 'PT1H',
            enabled: true,
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
            assert.equal(form.enabled, true);
            assert.deepEqual(form.selectedMonitors, ['http:Site']);
            assert.deepEqual(form.selectedCategories, ['Payments']);
            assert.deepEqual(form.integrations, ['email:ops']);
        },
        assertBlank: (form) => {
            assert.equal(form.name, '');
            assert.equal(form.type, MAINTENANCE_WINDOW_TYPES.MANUAL);
            assert.equal(form.pickedDate, '');
            assert.equal(form.pickedTime, '');
            assert.deepEqual(form.selectedCategories, []);
        },
        assertRendered: (form) => assert.equal(form.name, 'Rendered'),
    },
};

Object.entries(listEditedEntities).forEach(([path, {createForm, clonedFields, source, rendered, assertLoaded, assertBlank, assertRendered}]) => {
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

    test(`${path} cloneFrom after an edit loads the entity into a create form, overriding the values its copy differs in`, async (t) => {
        const browser = stubBrowser(t);
        const requests = stubRequests(t, jsonResponse(source), jsonResponse(source), jsonResponse({id: 42}));
        const form = createForm();
        form.init();

        await form.editFrom(7, 'Update Source');
        form.resetState();
        const cloning = form.cloneFrom(7, clonedFields);
        assert.equal(form.isLoadingEntity, true);
        await cloning;

        Object.entries(clonedFields).forEach(([field, value]) => assert.equal(form[field], value));
        // Everything else is copied from the source
        assert.deepEqual(form.selectedMonitors, ['http:Site']);
        assert.deepEqual(form.selectedCategories, ['Payments']);
        assert.equal(form.isUpdate, false);
        assert.equal(form.entityId, null);
        assert.equal(form.editTitle, null);
        assert.equal(form.isLoadingEntity, false);

        await form.upsert();

        assert.deepEqual(methodsAndUrlsOf(requests), [
            ['GET', `/api/v2/${path}/7`],
            ['GET', `/api/v2/${path}/7`],
            ['POST', `/api/v2/${path}`],
        ]);
        Object.entries(clonedFields).forEach(([field, value]) => assert.equal(requests[2].body[field], value));
        assert.deepEqual(requests[2].body.categories, ['Payments']);
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
    const statusPage = statusPageListItem(7, true, {title: 'Copy of Status', slug: 'status-copy'}, 'Update Status');
    const maintenanceWindow = maintenanceWindowListItem(8, false, {name: 'Copy of Window'}, 'Update Window');
    const dispatched = [];
    [statusPage, maintenanceWindow].forEach(item => item.$dispatch = (name, detail) => dispatched.push([name, detail]));

    statusPage.editStatusPage();
    maintenanceWindow.editMaintenanceWindow();
    statusPage.cloneStatusPage();
    maintenanceWindow.cloneMaintenanceWindow();

    assert.deepEqual(dispatched, [
        ['edit-status-page', {id: 7, title: 'Update Status'}],
        ['edit-maintenance-window', {id: 8, title: 'Update Window'}],
        ['clone-status-page', {id: 7, fields: {title: 'Copy of Status', slug: 'status-copy'}}],
        ['clone-maintenance-window', {id: 8, fields: {name: 'Copy of Window'}}],
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

// --------- DOM helpers run after the HTMX swaps ---------

// Stubs the few DOM and Tabler calls the helpers make: `elements` maps the selectors to what they match, and
// `existingTooltips` holds the trigger elements that already have a tooltip instance.
const stubDom = (t, {elements, existingTooltips = []}) => {
    const originalQuerySelectorAll = globalThis.document.querySelectorAll;
    const originalQuerySelector = globalThis.document.querySelector;
    const originalTabler = globalThis.tabler;
    const created = [];
    globalThis.document.querySelectorAll = (selector) => elements[selector] ?? [];
    globalThis.document.querySelector = (selector) => (elements[selector] ?? [])[0] ?? null;
    globalThis.tabler = {
        Tooltip: class {
            static getInstance(element) { return existingTooltips.includes(element) ? {} : null; }
            constructor(element, options) { created.push({element, options}); }
        },
        Sparkline: {getOrCreateInstance: (element) => created.push({element})},
    };
    t.after(() => {
        globalThis.document.querySelectorAll = originalQuerySelectorAll;
        globalThis.document.querySelector = originalQuerySelector;
        globalThis.tabler = originalTabler;
    });
    return created;
};

const fakeElement = (attributes = {}, id = null) => {
    const element = {
        id,
        removed: false,
        getAttribute: (name) => attributes[name] ?? null,
        remove() { element.removed = true; },
    };
    return element;
};

test('reInitTooltips only creates the tooltips of the elements that do not have one yet', (t) => {
    const existing = fakeElement();
    const swappedIn = fakeElement({'data-bs-html': 'true', 'data-bs-placement': 'top'});
    const created = stubDom(t, {
        elements: {'[data-bs-toggle="tooltip"]': [existing, swappedIn]},
        existingTooltips: [existing],
    });

    reInitTooltips();

    // Disposing the existing one could break its pending fade-out transition, so it's left alone
    assert.equal(created.length, 1);
    assert.equal(created[0].element, swappedIn);
    assert.deepEqual(created[0].options, {delay: {show: 50, hide: 50}, html: true, placement: 'top'});
});

test('reInitTooltips falls back to the default options of the tooltips', (t) => {
    const created = stubDom(t, {elements: {'[data-bs-toggle="tooltip"]': [fakeElement()]}});

    reInitTooltips();

    assert.deepEqual(created[0].options, {delay: {show: 50, hide: 50}, html: false, placement: 'auto'});
});

test('reInitTooltips removes only the tooltips whose element was swapped out', (t) => {
    const orphaned = fakeElement({}, 'tooltip1');
    const stillDescribing = fakeElement({}, 'tooltip2');
    stubDom(t, {
        elements: {
            'div.tooltip': [orphaned, stillDescribing],
            '[aria-describedby="tooltip2"]': [fakeElement()],
        },
    });

    reInitTooltips();

    assert.equal(orphaned.removed, true);
    assert.equal(stillDescribing.removed, false);
});

test('initSparklines renders every sparkline of the page', (t) => {
    const sparklines = [fakeElement(), fakeElement()];
    const created = stubDom(t, {elements: {'[data-bs-toggle="sparkline"]': sparklines}});

    initSparklines();

    assert.deepEqual(created.map(it => it.element), sparklines);
});

// Stubs the Tabler instances of the given elements, recording which of them got disposed
const stubInstances = (t, {tooltips = [], sparklines = []}) => {
    const originalTabler = globalThis.tabler;
    const disposed = [];
    const instanceOf = (elements) => (element) =>
        elements.includes(element) ? {dispose: () => disposed.push(element)} : null;
    globalThis.tabler = {
        Tooltip: {getInstance: instanceOf(tooltips)},
        Sparkline: {getInstance: instanceOf(sparklines)},
    };
    t.after(() => { globalThis.tabler = originalTabler; });
    return disposed;
};

const fakeContainer = (elements) => ({querySelectorAll: (selector) => elements[selector] ?? []});

test('disposeComponents disposes the sparklines and, after their transitions, the tooltips of the containers', (t) => {
    t.mock.timers.enable({apis: ['setTimeout']});
    const [tooltip, tooltipWithoutInstance, otherTooltip] = [fakeElement(), fakeElement(), fakeElement()];
    const sparkline = fakeElement();
    const disposed = stubInstances(t, {tooltips: [tooltip, otherTooltip], sparklines: [sparkline]});

    disposeComponents(
        fakeContainer({
            '[data-bs-toggle="tooltip"]': [tooltip, tooltipWithoutInstance],
            '[data-bs-toggle="sparkline"]': [sparkline],
        }),
        // A container that isn't on the page is skipped
        null,
        fakeContainer({'[data-bs-toggle="tooltip"]': [otherTooltip]}),
    );

    assert.deepEqual(disposed, [sparkline]);
    t.mock.timers.tick(TOOLTIP_DISPOSE_DELAY_MS - 1);
    assert.deepEqual(disposed, [sparkline]);
    t.mock.timers.tick(1);
    assert.deepEqual(disposed, [sparkline, tooltip, otherTooltip]);
});

// --------- Incident notes ---------

const NOTE_ERRORS = {contentRequired: 'CONTENT_REQUIRED', timestampInvalid: 'TIMESTAMP_INVALID'};

test('incidentNoteForm starts as the creation of a new, private note under investigation', () => {
    const form = incidentNoteForm('HTTP', 42, NOTE_ERRORS);
    form.init();

    assert.equal(form.isUpdate, false);
    assert.equal(form.noteId, null);
    assert.equal(form.status, INVESTIGATION_STATUSES.INVESTIGATING);
    assert.equal(form.content, '');
    assert.equal(form.public, false);
    assert.equal(form.pickedDate, '');
    assert.equal(form.pickedTime, '');
});

test('incidentNoteForm.editFrom loads the note, and resetState goes back to a new note', () => {
    const form = incidentNoteForm('HTTP', 42, NOTE_ERRORS);
    const timestamp = '2030-01-05T09:30:00Z';

    form.editFrom({id: 7, status: 'IDENTIFIED', content: 'Found it', timestamp, public: true});
    assert.equal(form.isUpdate, true);
    assert.equal(form.noteId, 7);
    assert.equal(form.status, 'IDENTIFIED');
    assert.equal(form.content, 'Found it');
    assert.equal(form.public, true);
    // The timestamp is shown in the local time zone, and sent back as it was while it isn't changed
    assert.equal(form.pickedDateTimeAsIso(), timestamp);

    form.isDeleteConfirmationShown = true;
    form.resetState();
    assert.equal(form.isUpdate, false);
    assert.equal(form.noteId, null);
    assert.equal(form.content, '');
    assert.equal(form.pickedDate, '');
    assert.equal(form.isDeleteConfirmationShown, false);
});

test('incidentNoteForm.validate requires a non-blank content', () => {
    const form = incidentNoteForm('HTTP', 42, NOTE_ERRORS);
    form.init();

    form.content = '  \n ';
    form.validate();
    assert.equal(form.errors.content, 'CONTENT_REQUIRED');

    form.content = 'Looking into it';
    form.validate();
    assert.equal(form.errors.content, null);
});

test('incidentNoteForm.validate accepts an empty or a complete timestamp, but not a partial or invalid one', () => {
    const form = incidentNoteForm('HTTP', 42, NOTE_ERRORS);
    form.init();
    form.content = 'Looking into it';

    const errorOf = (date, time) => {
        Object.assign(form, {pickedDate: date, pickedTime: time});
        form.validate();
        return form.errors.timestamp;
    };
    assert.equal(errorOf('', ''), null);
    assert.equal(errorOf('2030-01-05', '10:30'), null);
    assert.equal(errorOf('2030-01-05', '10:30:45'), null);
    assert.equal(errorOf('2030-01-05', '10:30:'), 'TIMESTAMP_INVALID');
    assert.equal(errorOf('2030-01-05', ''), 'TIMESTAMP_INVALID');
    assert.equal(errorOf('', '10:30'), 'TIMESTAMP_INVALID');
    assert.equal(errorOf('2030-02-30', '10:30'), 'TIMESTAMP_INVALID');
    assert.equal(errorOf('2030-01-05', '24:00'), 'TIMESTAMP_INVALID');
});

test('incidentNoteForm.buildRequestBody converts the timestamp to ISO, or sends null without one', () => {
    const form = incidentNoteForm('HTTP', 42, NOTE_ERRORS);
    form.init();
    Object.assign(form, {status: 'RESOLVED', content: 'Fixed\nfor good', public: true});

    assert.deepEqual(form.buildRequestBody(), {status: 'RESOLVED', content: 'Fixed\nfor good', timestamp: null, public: true});

    Object.assign(form, {pickedDate: '2030-01-05', pickedTime: '10:30'});
    assert.equal(form.buildRequestBody().timestamp, new Date('2030-01-05T10:30').toISOString());
});

test('incidentNoteForm.buildRequestBody keeps the seconds of an edited note\'s timestamp until it is changed', () => {
    const form = incidentNoteForm('HTTP', 42, NOTE_ERRORS);
    const timestamp = '2030-01-05T09:30:45.123456Z';
    form.editFrom({id: 7, status: 'IDENTIFIED', content: 'Found it', timestamp, public: true});

    form.content = 'Found it, fixed a typo';
    assert.equal(form.buildRequestBody().timestamp, timestamp);

    form.pickedTime = '11:15';
    assert.equal(form.buildRequestBody().timestamp, new Date(`${form.pickedDate}T11:15`).toISOString());
    // The seconds are shown, so they can be changed too
    form.setPickedDateTime(timestamp);
    assert.equal(form.pickedTime.slice(-3), ':45');
    form.pickedTime = `${form.pickedTime.slice(0, -3)}:50`;
    assert.equal(form.buildRequestBody().timestamp, new Date(`${form.pickedDate}T${form.pickedTime}`).toISOString());

    form.setPickedDateTime(timestamp);
    Object.assign(form, {pickedDate: '', pickedTime: ''});
    assert.equal(form.buildRequestBody().timestamp, null);

    // A new note doesn't inherit the timestamp of the previously edited one
    form.resetState();
    Object.assign(form, {pickedDate: '2030-01-05', pickedTime: '10:30'});
    assert.equal(form.buildRequestBody().timestamp, new Date('2030-01-05T10:30').toISOString());
});

const withReloadingWindow = (t) => {
    const originalWindow = globalThis.window;
    const reloads = [];
    globalThis.window = {location: {reload: () => reloads.push(true)}};
    t.after(() => { globalThis.window = originalWindow; });
    return reloads;
};

test('incidentNoteForm.submitForm creates a note of the incident, then reloads the page', async (t) => {
    const reloads = withReloadingWindow(t);
    const requests = [];
    stubFetch(t, async (url, init) => {
        requests.push({url, init});
        return {ok: true, status: 201, json: async () => ({})};
    });
    const form = incidentNoteForm('DOCKER', 42, NOTE_ERRORS);
    form.init();
    form.content = 'Looking into it';

    await form.submitForm();

    assert.equal(requests.length, 1);
    assert.equal(requests[0].url, '/api/v2/incidents/DOCKER/42/notes');
    assert.equal(requests[0].init.method, 'POST');
    assert.deepEqual(JSON.parse(requests[0].init.body), {
        status: 'INVESTIGATING', content: 'Looking into it', timestamp: null, public: false,
    });
    assert.equal(reloads.length, 1);
    assert.equal(form.isRequestLoading, false);
});

test('incidentNoteForm.submitForm updates an edited note', async (t) => {
    withReloadingWindow(t);
    const requests = [];
    stubFetch(t, async (url, init) => {
        requests.push({url, init});
        return {ok: true, status: 200, json: async () => ({})};
    });
    const form = incidentNoteForm('HTTP', 42, NOTE_ERRORS);
    form.editFrom({id: 7, status: 'IDENTIFIED', content: 'Found it', timestamp: null, public: false});

    await form.submitForm();

    assert.equal(requests[0].url, '/api/v2/incidents/notes/7');
    assert.equal(requests[0].init.method, 'PATCH');
});

test('incidentNoteForm.submitForm sends nothing while the form is invalid', async (t) => {
    const requests = [];
    stubFetch(t, async (url) => { requests.push(url); });
    const form = incidentNoteForm('HTTP', 42, NOTE_ERRORS);
    form.init();

    await form.submitForm();

    assert.equal(requests.length, 0);
    assert.equal(form.errors.content, 'CONTENT_REQUIRED');
});

test('incidentNoteForm.submitForm shows the error reported by the server', async (t) => {
    const reloads = withReloadingWindow(t);
    stubFetch(t, async () => ({ok: false, status: 405, json: async () => ({message: 'Incident notes are disabled'})}));
    const form = incidentNoteForm('HTTP', 42, NOTE_ERRORS);
    form.init();
    form.content = 'Looking into it';

    await form.submitForm();

    assert.equal(form.formError, 'Incident notes are disabled');
    assert.equal(reloads.length, 0);
    assert.equal(form.isRequestLoading, false);
});

test('incidentNoteForm.deleteNote deletes the edited note, then reloads the page', async (t) => {
    const reloads = withReloadingWindow(t);
    const requests = [];
    stubFetch(t, async (url, init) => {
        requests.push({url, method: init.method});
        return {ok: true, status: 204};
    });
    const form = incidentNoteForm('HTTP', 42, NOTE_ERRORS);
    form.editFrom({id: 7, status: 'IDENTIFIED', content: 'Found it', timestamp: null, public: false});

    await form.deleteNote();

    assert.deepEqual(requests, [{url: '/api/v2/incidents/notes/7', method: 'DELETE'}]);
    assert.equal(reloads.length, 1);
});

// --------- Incidents on the status pages ---------

test('resolveStatusPageAccordionState keeps the default until the visitor chooses', () => {
    assert.deepEqual(
        resolveStatusPageAccordionState({isOpenByDefault: true, wasShown: true, isOpenByUser: null}),
        {isOpen: true, isOpenByUser: null},
    );
    assert.deepEqual(
        resolveStatusPageAccordionState({isOpenByDefault: false, wasShown: true, isOpenByUser: null}),
        {isOpen: false, isOpenByUser: null},
    );
});

test('resolveStatusPageAccordionState keeps the choice of the visitor across the refreshes', () => {
    // The active incidents, closed by the visitor
    assert.deepEqual(
        resolveStatusPageAccordionState({isOpenByDefault: true, wasShown: true, isOpenByUser: false}),
        {isOpen: false, isOpenByUser: false},
    );
    // The resolved incidents, opened by the visitor
    assert.deepEqual(
        resolveStatusPageAccordionState({isOpenByDefault: false, wasShown: true, isOpenByUser: true}),
        {isOpen: true, isOpenByUser: true},
    );
});

test('resolveStatusPageAccordionState starts over with an accordion that appears again, e.g. on a new outage', () => {
    assert.deepEqual(
        resolveStatusPageAccordionState({isOpenByDefault: true, wasShown: false, isOpenByUser: false}),
        {isOpen: true, isOpenByUser: null},
    );
});

// A minimal stand-in of the classList of a DOM element
const fakeClassList = (...initial) => {
    const classes = new Set(initial);
    return {
        contains: (name) => classes.has(name),
        toggle: (name, force) => (force ? classes.add(name) : classes.delete(name)),
    };
};

// The refreshed element of the page with the given accordions, each rendered open or closed by default, with the same
// ids and toggle targets as the ones rendered by the server
const fakeAccordionsContent = ({accordions, dataset}) => {
    const rendered = Object.entries(accordions).map(([key, isOpenByDefault]) => {
        const attributes = {};
        return {
            key,
            attributes,
            collapse: {
                id: `status-page-${key}-collapse`,
                dataset: {statusPageAccordion: key},
                classList: isOpenByDefault ? fakeClassList('collapse', 'show') : fakeClassList('collapse'),
            },
            toggle: {
                classList: isOpenByDefault ? fakeClassList() : fakeClassList('collapsed'),
                setAttribute: (name, value) => attributes[name] = value,
            },
        };
    });
    const content = {
        dataset,
        querySelectorAll: () => rendered.map(({collapse}) => collapse),
        querySelector: (selector) =>
            rendered.find(({collapse}) => selector === `[data-bs-target="#${collapse.id}"]`)?.toggle ?? null,
    };
    const byKey = Object.fromEntries(rendered.map((accordion) => [accordion.key, accordion]));
    return {content, byKey};
};

const ACTIVE_INCIDENTS = 'active-incidents';
const ACTIVE_MAINTENANCE = 'active-maintenance';
const RESOLVED_INCIDENTS = 'resolved-incidents';

test('applyStatusPageAccordionsState applies the choices of the visitor to the refreshed accordions', () => {
    const {content, byKey} = fakeAccordionsContent({
        accordions: {[ACTIVE_INCIDENTS]: true, [ACTIVE_MAINTENANCE]: true, [RESOLVED_INCIDENTS]: false},
        dataset: {
            statusPageAccordionsShown: JSON.stringify([ACTIVE_INCIDENTS, ACTIVE_MAINTENANCE, RESOLVED_INCIDENTS]),
            statusPageAccordionChoices: JSON.stringify({[ACTIVE_INCIDENTS]: false, [RESOLVED_INCIDENTS]: true}),
        },
    });

    applyStatusPageAccordionsState(content);

    assert.equal(byKey[ACTIVE_INCIDENTS].collapse.classList.contains('show'), false);
    assert.equal(byKey[ACTIVE_INCIDENTS].toggle.classList.contains('collapsed'), true);
    assert.equal(byKey[ACTIVE_INCIDENTS].attributes['aria-expanded'], 'false');
    // Without a choice of the visitor, an accordion keeps its default
    assert.equal(byKey[ACTIVE_MAINTENANCE].collapse.classList.contains('show'), true);
    assert.equal(byKey[ACTIVE_MAINTENANCE].toggle.classList.contains('collapsed'), false);
    assert.equal(byKey[ACTIVE_MAINTENANCE].attributes['aria-expanded'], 'true');
    assert.equal(byKey[RESOLVED_INCIDENTS].collapse.classList.contains('show'), true);
    assert.equal(byKey[RESOLVED_INCIDENTS].toggle.classList.contains('collapsed'), false);
    assert.equal(byKey[RESOLVED_INCIDENTS].attributes['aria-expanded'], 'true');
    assert.deepEqual(
        JSON.parse(content.dataset.statusPageAccordionChoices),
        {[ACTIVE_INCIDENTS]: false, [RESOLVED_INCIDENTS]: true},
    );
});

test('applyStatusPageAccordionsState opens a new outage or maintenance, but keeps the other choices', () => {
    const {content, byKey} = fakeAccordionsContent({
        accordions: {[ACTIVE_INCIDENTS]: true, [ACTIVE_MAINTENANCE]: true, [RESOLVED_INCIDENTS]: false},
        // The active incidents and the maintenance were not there before the refresh, but the visitor had closed them
        dataset: {
            statusPageAccordionsShown: JSON.stringify([RESOLVED_INCIDENTS]),
            statusPageAccordionChoices: JSON.stringify({
                [ACTIVE_INCIDENTS]: false,
                [ACTIVE_MAINTENANCE]: false,
                [RESOLVED_INCIDENTS]: true,
            }),
        },
    });

    applyStatusPageAccordionsState(content);

    assert.equal(byKey[ACTIVE_INCIDENTS].collapse.classList.contains('show'), true);
    assert.equal(byKey[ACTIVE_MAINTENANCE].collapse.classList.contains('show'), true);
    assert.equal(byKey[RESOLVED_INCIDENTS].collapse.classList.contains('show'), true);
    assert.deepEqual(JSON.parse(content.dataset.statusPageAccordionChoices), {[RESOLVED_INCIDENTS]: true});
});

test('applyStatusPageAccordionsState forgets the choices about the accordions that are gone', () => {
    const {content} = fakeAccordionsContent({
        accordions: {},
        dataset: {
            statusPageAccordionsShown: JSON.stringify([ACTIVE_INCIDENTS]),
            statusPageAccordionChoices: JSON.stringify({[ACTIVE_INCIDENTS]: false}),
        },
    });

    applyStatusPageAccordionsState(content);

    assert.deepEqual(JSON.parse(content.dataset.statusPageAccordionChoices), {});
});

test('applyStatusPageAccordionsState keeps the defaults without any remembered state, even a broken one', () => {
    const {content, byKey} = fakeAccordionsContent({
        accordions: {[ACTIVE_INCIDENTS]: true, [ACTIVE_MAINTENANCE]: true, [RESOLVED_INCIDENTS]: false},
        dataset: {statusPageAccordionChoices: '{broken'},
    });

    applyStatusPageAccordionsState(content);

    assert.equal(byKey[ACTIVE_INCIDENTS].collapse.classList.contains('show'), true);
    assert.equal(byKey[ACTIVE_MAINTENANCE].collapse.classList.contains('show'), true);
    assert.equal(byKey[RESOLVED_INCIDENTS].collapse.classList.contains('show'), false);
});

test('rememberShownStatusPageAccordions records the accordions that are on the page before the refresh', () => {
    const {content} = fakeAccordionsContent({
        accordions: {[ACTIVE_INCIDENTS]: true, [ACTIVE_MAINTENANCE]: true, [RESOLVED_INCIDENTS]: false},
        dataset: {},
    });

    rememberShownStatusPageAccordions(content);

    assert.deepEqual(
        JSON.parse(content.dataset.statusPageAccordionsShown),
        [ACTIVE_INCIDENTS, ACTIVE_MAINTENANCE, RESOLVED_INCIDENTS],
    );
});

test('rememberStatusPageAccordionChoice records when the visitor opens or closes an accordion', () => {
    const content = {dataset: {}};
    const collapse = (key) => ({dataset: {statusPageAccordion: key}, closest: () => content});

    rememberStatusPageAccordionChoice({type: 'hidden.bs.collapse', target: collapse(ACTIVE_INCIDENTS)});
    rememberStatusPageAccordionChoice({type: 'hidden.bs.collapse', target: collapse(ACTIVE_MAINTENANCE)});
    rememberStatusPageAccordionChoice({type: 'shown.bs.collapse', target: collapse(RESOLVED_INCIDENTS)});
    const expectedChoices = {[ACTIVE_INCIDENTS]: false, [ACTIVE_MAINTENANCE]: false, [RESOLVED_INCIDENTS]: true};
    assert.deepEqual(JSON.parse(content.dataset.statusPageAccordionChoices), expectedChoices);

    // Any other collapsible element is ignored, just like an accordion outside the refreshed part of the page
    rememberStatusPageAccordionChoice({type: 'shown.bs.collapse', target: {dataset: {}, closest: () => content}});
    rememberStatusPageAccordionChoice({
        type: 'shown.bs.collapse',
        target: {dataset: {statusPageAccordion: ACTIVE_INCIDENTS}, closest: () => null},
    });
    assert.deepEqual(JSON.parse(content.dataset.statusPageAccordionChoices), expectedChoices);
});

const withIncidentsModal = (t, {templates, isShown = false}) => {
    const filled = [];
    const modal = {
        dataset: {},
        classList: isShown ? fakeClassList('modal', 'show') : fakeClassList('modal'),
        querySelector: () => ({replaceChildren: (child) => filled.push(child)}),
    };
    const originalDocument = globalThis.document;
    globalThis.document = {
        addEventListener() {},
        getElementById: (id) => id === 'status-page-incidents-modal' ? modal : null,
        querySelectorAll: () => templates.map(({key, content}) => ({
            dataset: {incidentsOf: key},
            content: {cloneNode: () => content},
        })),
    };
    t.after(() => { globalThis.document = originalDocument; });
    return {modal, filled};
};

test('showStatusPageIncidents fills the modal from the template of the monitor', (t) => {
    const {modal, filled} = withIncidentsModal(t, {
        templates: [{key: 'http:API "v2"', content: 'API'}, {key: 'push:Worker', content: 'Worker'}],
    });

    showStatusPageIncidents('push:Worker');

    assert.equal(modal.dataset.incidentsOf, 'push:Worker');
    assert.deepEqual(filled, ['Worker']);
});

test('refreshStatusPageIncidentsModal refills only an open modal, and keeps it when the template is gone', (t) => {
    const {modal, filled} = withIncidentsModal(t, {templates: [{key: 'http:API', content: 'Refreshed'}], isShown: true});
    modal.dataset.incidentsOf = 'http:API';

    refreshStatusPageIncidentsModal();
    assert.deepEqual(filled, ['Refreshed']);

    modal.dataset.incidentsOf = 'http:Gone';
    refreshStatusPageIncidentsModal();
    assert.deepEqual(filled, ['Refreshed']);
});

test('refreshStatusPageIncidentsModal leaves a closed modal alone', (t) => {
    const {modal, filled} = withIncidentsModal(t, {templates: [{key: 'http:API', content: 'Refreshed'}]});
    modal.dataset.incidentsOf = 'http:API';

    refreshStatusPageIncidentsModal();

    assert.deepEqual(filled, []);
});

// --------- Paged lists ---------

// A listQuery the way Alpine.js sets it up. The watcher of its filters is called explicitly, as there is no Alpine.js
// to call it on a change, and the requests of its list are recorded
const createListQuery = (t, {filters = {}, emptyFilters = {}, navigation = {}} = {}) => {
    const component = listQuery({
        filters,
        emptyFilters,
        navigation: {page: null, size: null, sortedBy: null, sortOrder: null, ...navigation},
    });
    const watchers = {};
    component.$watch = (property, callback) => watchers[property] = callback;
    component.$refs = {list: {id: 'the-list'}};
    const reloads = [];
    const originalHtmx = globalThis.htmx;
    globalThis.htmx = {trigger: (element, eventName) => reloads.push({element: element.id, eventName})};
    t.after(() => { globalThis.htmx = originalHtmx; });
    component.init();
    return {component, reloads, filtersChanged: () => watchers.filters()};
};

const requestPathOf = (component, path) => {
    const event = {detail: {path}};
    component.configureRequest(event);
    return event.detail.path;
};

const clickOn = (href, modifiers = {}) => {
    let isDefaultPrevented = false;
    const event = {
        button: 0,
        ctrlKey: false,
        metaKey: false,
        shiftKey: false,
        altKey: false,
        ...modifiers,
        currentTarget: {href},
        preventDefault: () => isDefaultPrevented = true,
    };
    return {event, isDefaultPrevented: () => isDefaultPrevented};
};

test('listQuery requests its list with the current filters and navigation, replacing the query of the request', (t) => {
    const {component} = createListQuery(t, {
        filters: {name: 'a & b', category: '', enabled: null, uptimeStatus: ['UP', 'DOWN']},
        navigation: {page: '2', sortedBy: 'NAME', sortOrder: 'DESC'},
    });

    // The empty category stands for the monitors without a category, so unlike the null ones, it's kept
    assert.equal(
        requestPathOf(component, '/http-monitors/fragments/list?name=old&page=9'),
        '/http-monitors/fragments/list?name=a+%26+b&category=&uptimeStatus=UP&uptimeStatus=DOWN' +
        '&page=2&sortedBy=NAME&sortOrder=DESC',
    );
});

test('listQuery requests its list on its plain path, when nothing is set', (t) => {
    const {component} = createListQuery(t, {filters: {enabled: null, uptimeStatus: []}});

    assert.equal(requestPathOf(component, '/http-monitors/fragments/list?page=3'), '/http-monitors/fragments/list');
});

test('listQuery starts over from the first page when its filters change', (t) => {
    const {component, reloads, filtersChanged} = createListQuery(t, {
        filters: {name: ''},
        navigation: {page: '3', size: '50', sortedBy: 'NAME'},
    });

    component.filters.name = 'api';
    filtersChanged();

    assert.deepEqual(component.navigation, {page: null, size: '50', sortedBy: 'NAME', sortOrder: null});
    assert.deepEqual(reloads, [{element: 'the-list', eventName: LIST_QUERY_CHANGE_EVENT}]);
});

test('listQuery takes the navigation of a clicked link, keeping its filters', (t) => {
    const {component, reloads} = createListQuery(t, {
        filters: {name: 'typed since the link was rendered'},
        navigation: {page: '3', size: '50'},
    });
    const click = clickOn('http://localhost/http-monitors?name=old&sortedBy=UPTIME_STATUS&sortOrder=DESC&size=10');

    component.followLink(click.event);

    assert.equal(click.isDefaultPrevented(), true);
    // What the link leaves out is the default, so the page is the first one again
    assert.deepEqual(component.navigation, {page: null, size: '10', sortedBy: 'UPTIME_STATUS', sortOrder: 'DESC'});
    assert.deepEqual(component.filters, {name: 'typed since the link was rendered'});
    assert.deepEqual(reloads, [{element: 'the-list', eventName: LIST_QUERY_CHANGE_EVENT}]);
});

test('listQuery leaves a click with a modifier key or another button to the browser', (t) => {
    const {component, reloads} = createListQuery(t, {navigation: {page: '3'}});
    const clicks = [
        {ctrlKey: true},
        {metaKey: true},
        {shiftKey: true},
        {altKey: true},
        {button: 1},
    ].map(modifiers => clickOn('http://localhost/http-monitors?page=5', modifiers));

    clicks.forEach(click => component.followLink(click.event));

    assert.equal(clicks.some(click => click.isDefaultPrevented()), false);
    assert.equal(component.navigation.page, '3');
    assert.deepEqual(reloads, []);
});

test('listQuery starts over from the first page on a new page size', (t) => {
    const {component, reloads} = createListQuery(t, {navigation: {page: '4', sortedBy: 'NAME'}});

    component.setSize('100');

    assert.deepEqual(component.navigation, {page: null, size: '100', sortedBy: 'NAME', sortOrder: null});
    assert.equal(reloads.length, 1);
});

test('listQuery clears its filters to a copy of the empty ones', (t) => {
    const emptyFilters = {name: '', category: null, uptimeStatus: []};
    const {component} = createListQuery(t, {
        filters: {name: 'api', category: '', uptimeStatus: ['DOWN']},
        emptyFilters,
    });

    component.clearFilters();
    component.filters.uptimeStatus.push('UP');
    component.clearFilters();

    assert.deepEqual(component.filters, {name: '', category: null, uptimeStatus: []});
    assert.deepEqual(emptyFilters, {name: '', category: null, uptimeStatus: []});
});

test('listQuery takes the page shown by the list, which is the last one when the requested page is past the end', (t) => {
    const {component, reloads} = createListQuery(t, {navigation: {page: '7'}});
    const listShowing = (page) => ({
        querySelector: (selector) => selector === '[data-list-page]' ? {dataset: {listPage: page}} : null,
    });

    component.syncFromList(listShowing('5'));
    assert.equal(component.navigation.page, '5');

    // A list without a page, e.g. an error message, changes nothing
    component.syncFromList({querySelector: () => null});
    assert.equal(component.navigation.page, '5');
    assert.deepEqual(reloads, []);
});
