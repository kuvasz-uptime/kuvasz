// Dark/light mode toggle
const setTheme = (theme) => {
    document.documentElement.setAttribute('data-bs-theme', theme);
    localStorage.setItem('kuvasz-theme', theme);
};

// Auto-select the active route in the navigation
document.addEventListener('DOMContentLoaded', function () {
    const navLinks = document.querySelectorAll('.nav-link');
    const currentPath = window.location.pathname;

    navLinks.forEach(link => {
        let linkPath = link.getAttribute('href');
        if (linkPath === "") {
            linkPath = '/';
        }
        if (currentPath === linkPath
            || (currentPath.startsWith(linkPath) && linkPath !== "/")
            || (
                (currentPath.startsWith('/http-monitors')
                    || currentPath.startsWith('/push-monitors')
                    || currentPath.startsWith('/icmp-monitors')
                    || currentPath.startsWith('/tcp-monitors')
                    || currentPath.startsWith('/dns-monitors')
                ) && linkPath === '#navbar-monitors')
        ) {
            link.parentNode.classList.add('active');
        }
    });
});

// Sends an HTMX event to the target element
const sendHtmxEvent = (target, eventName) => {
    htmx.trigger(target, eventName);
};

// Sends a custom window event
const sendWindowEvent = (eventName) => {
    const event = new CustomEvent(eventName);
    window.dispatchEvent(event);
};

// Reinitialize Bootstrap tooltips (useful after HTMX content swap)
const reInitTooltips = () => {
    // First remove all tooltips to prevent burn-ins upon HTMX swaps
    document.querySelectorAll('div.tooltip.show').forEach(tooltip => tooltip.remove());

    let tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        // If the tooltip is already initialized, dispose it
        const tooltipInstance = tabler.Tooltip.getInstance(tooltipTriggerEl);
        if (tooltipInstance) {
            tooltipInstance.dispose();
        }
        let options = {
            delay: {show: 50, hide: 50},
            html: tooltipTriggerEl.getAttribute("data-bs-html") === "true",
            placement: tooltipTriggerEl.getAttribute('data-bs-placement') ?? 'auto'
        };
        return new tabler.Tooltip(tooltipTriggerEl, options);
    });
};

// Sanitizes text input by trimming whitespace and converting empty strings to null
const sanitizeTextInput = (inputValue) => {
    if (typeof inputValue !== 'string') {
        return null;
    }
    if (inputValue.trim() === '') {
        return null;
    }
    return inputValue;
};

// Splits a string by a delimiter and return the parts over the limit as the last element, as they were in the
// original string
const splitWithLimit = (str, delimiter, limit) => {
    const parts = str.split(delimiter);
    if (parts.length <= limit) {
        return parts;
    }
    const limitedParts = parts.slice(0, limit - 1);
    limitedParts.push(parts.slice(limit - 1).join(delimiter));
    return limitedParts;
};

// Maps HTTP status codes to badge CSS classes
const statusCodeToBadgeClass = (statusCode) => {
    return statusCode.substring(0, 1) === '1' ? 'status-azure' :
        statusCode.substring(0, 1) === '2' ? 'status-green' :
            statusCode.substring(0, 1) === '3' ? 'status-yellow' :
                statusCode.substring(0, 1) === '4' ? 'status-red' : '';
};

const HTML_ESCAPES = {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'};

// Escapes the HTML special characters of a text that is interpolated into a markup string
const escapeHtml = (text) => String(text ?? '').replace(/[&<>"']/g, (char) => HTML_ESCAPES[char]);

// Builds the markup of a toast. Its header and body can carry remote content (e.g. the error of an integration
// test request), so both of them have to be escaped
const buildToastMarkup = (header, content, statusClass, autoHide) =>
    `<div class="toast fade" role="alert" aria-live="assertive" aria-atomic="true" data-bs-autohide="${autoHide}" ${autoHide ? 'data-bs-delay="3000"' : ''}>
            <div class="toast-header">
                <span class="status-dot ${statusClass} me-2"></span>
                <strong class="me-auto">${escapeHtml(header)}</strong>
                <button type="button" class="ms-2 btn-close" data-bs-dismiss="toast" aria-label="Close"></button>
            </div>
            <div class="toast-body">${escapeHtml(content)}</div>
        </div>`.trim();

// Shows a toast notification using Tabler's toast component
const showToast = (header, content, statusClass, autoHide) => {
    const html = buildToastMarkup(header, content, statusClass, autoHide);
    const toastElement = document.createElement('template');
    toastElement.innerHTML = html;
    const toastContainer = document.querySelector("#toast-container");
    toastContainer.appendChild(toastElement.content.firstChild);
    new tabler.Toast(toastContainer.lastElementChild).show();
};

// Generates a random UUIDv4. Prefers crypto.randomUUID, but that is only exposed in secure contexts (HTTPS or
// localhost), so we fall back to crypto.getRandomValues - which is always available - for plain-HTTP deployments.
const createRandomSecret = () => {
    if (typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID();
    }
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = [...bytes].map(b => b.toString(16).padStart(2, '0'));
    return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-` +
        `${hex.slice(8, 10).join('')}-${hex.slice(10, 16).join('')}`;
};

// --------- API layer ---------
const jsonContentHeaders = {'Content-Type': 'application/json'};

// Generic JSON request wrapper with shared success/error handling used by every entity's CRUD calls
const apiRequest = (
    method,
    url,
    {body = null, beforeRequest = () => {}, onSuccess = () => {}, onError = () => {}, errorMessage} = {}
) => {
    beforeRequest();
    const init = {method, headers: jsonContentHeaders};
    if (body != null) {
        init.body = JSON.stringify(body);
    }
    return fetch(url, init).then(response => {
        if (response.ok) {
            return onSuccess(response);
        } else {
            onError();
            console.error(errorMessage, response.statusText);
            alert(errorMessage);
        }
    }).catch(error => {
        onError();
        console.error(errorMessage, error);
        alert(errorMessage);
    });
};

// Builds the GET/PATCH/DELETE helpers for a given entity, keeping the URL and user-facing labels in one place
const crudRequests = (basePath, entityLabel) => ({
    get: (id, beforeRequest, onSuccess, onError) => apiRequest('GET', `${basePath}/${id}`, {
        beforeRequest, onSuccess, onError,
        errorMessage: `An error occurred while loading the ${entityLabel}.`,
    }),
    patch: (id, body, beforeRequest, onSuccess, onError) => apiRequest('PATCH', `${basePath}/${id}`, {
        body, beforeRequest, onSuccess, onError,
        errorMessage: `An error occurred while toggling the ${entityLabel}.`,
    }),
    remove: (id, beforeRequest, onSuccess, onError) => apiRequest('DELETE', `${basePath}/${id}`, {
        beforeRequest, onSuccess, onError,
        errorMessage: `An error occurred while deleting the ${entityLabel}.`,
    }),
});

const httpMonitorApi = crudRequests('/api/v2/http-monitors', 'monitor');
const pushMonitorApi = crudRequests('/api/v2/push-monitors', 'monitor');
const icmpMonitorApi = crudRequests('/api/v2/icmp-monitors', 'monitor');
const tcpMonitorApi = crudRequests('/api/v2/tcp-monitors', 'monitor');
const dnsMonitorApi = crudRequests('/api/v2/dns-monitors', 'monitor');
const statusPageApi = crudRequests('/api/v2/status-pages', 'status page');
const maintenanceWindowApi = crudRequests('/api/v2/maintenance-windows', 'maintenance window');

// --------- HTMX refresh triggers ---------
// Refreshes a monitor detail page's dynamic status blocks by triggering an HTMX event (OOB swap)
const refreshHttpMonitorDetailStatus = () => sendHtmxEvent('#http-monitor-detail-heading', 'refresh-monitor-detail-status');
const refreshPushMonitorDetailStatus = () => sendHtmxEvent('#push-monitor-detail-heading', 'refresh-monitor-detail-status');
const refreshIcmpMonitorDetailStatus = () => sendHtmxEvent('#icmp-monitor-detail-heading', 'refresh-monitor-detail-status');
const refreshTcpMonitorDetailStatus = () => sendHtmxEvent('#tcp-monitor-detail-heading', 'refresh-monitor-detail-status');
const refreshDnsMonitorDetailStatus = () => sendHtmxEvent('#dns-monitor-detail-heading', 'refresh-monitor-detail-status');

// Refreshes a monitor list by triggering an HTMX event
const refreshHttpMonitorList = () => sendHtmxEvent('#http-monitors-list', 'refresh-monitor-list');
const refreshPushMonitorList = () => sendHtmxEvent('#push-monitors-list', 'refresh-monitor-list');
const refreshIcmpMonitorList = () => sendHtmxEvent('#icmp-monitors-list', 'refresh-monitor-list');
const refreshTcpMonitorList = () => sendHtmxEvent('#tcp-monitors-list', 'refresh-monitor-list');
const refreshDnsMonitorList = () => sendHtmxEvent('#dns-monitors-list', 'refresh-monitor-list');

// Refreshes the status page list by triggering an HTMX event
const refreshStatusPageList = () => sendHtmxEvent('#status-page-list', 'refresh-status-page-list');

// Refreshes the dashboard by triggering an HTMX event
const refreshDashboard = () => {
    sendHtmxEvent('#dashboard-empty-state', 'refresh-dashboard');
    sendHtmxEvent('#http-monitoring-dashboard', 'refresh-dashboard');
    sendHtmxEvent('#push-monitoring-dashboard', 'refresh-dashboard');
    sendHtmxEvent('#icmp-monitoring-dashboard', 'refresh-dashboard');
    sendHtmxEvent('#tcp-monitoring-dashboard', 'refresh-dashboard');
    sendHtmxEvent('#dns-monitoring-dashboard', 'refresh-dashboard');
};

// --------- Alpine.js x-data ---------

// Shared list-row component for every monitor type; only the API binding and list refresh differ
const monitorListItem = (api, refreshList) => (
    monitorId,
    isMonitorEnabled,
    assignedToStatusPage,
    clonedName,
    editTitle,
    isNameLocked
) => ({
    monitorId,
    isMonitorEnabled,
    assignedToStatusPage,
    clonedName,
    editTitle,
    isNameLocked,
    isRequestLoading: false,
    cloneMonitor() {
        this.$dispatch('clone-monitor', {id: this.monitorId, name: this.clonedName});
    },
    editMonitor() {
        this.$dispatch('edit-monitor', {id: this.monitorId, title: this.editTitle, nameLocked: this.isNameLocked});
    },
    toggleMonitor() {
        api.patch(
            this.monitorId,
            {enabled: !this.isMonitorEnabled},
            () => this.isRequestLoading = true,
            () => refreshList(),
            () => this.isRequestLoading = false
        );
    },
    deleteMonitor() {
        api.remove(
            this.monitorId,
            () => this.isRequestLoading = true,
            () => refreshList()
        );
    }
});

const httpMonitorListItem = monitorListItem(httpMonitorApi, refreshHttpMonitorList);
const icmpMonitorListItem = monitorListItem(icmpMonitorApi, refreshIcmpMonitorList);
const tcpMonitorListItem = monitorListItem(tcpMonitorApi, refreshTcpMonitorList);
const dnsMonitorListItem = monitorListItem(dnsMonitorApi, refreshDnsMonitorList);
const pushMonitorListItem = monitorListItem(pushMonitorApi, refreshPushMonitorList);

const statusPageListItem = (statusPageId, isStatusPagePublic, clonedFields, editTitle) => ({
    statusPageId,
    isStatusPagePublic,
    clonedFields,
    editTitle,
    isRequestLoading: false,
    cloneStatusPage() {
        this.$dispatch('clone-status-page', {id: this.statusPageId, fields: this.clonedFields});
    },
    editStatusPage() {
        this.$dispatch('edit-status-page', {id: this.statusPageId, title: this.editTitle});
    },
    toggleStatusPageVisibility() {
        statusPageApi.patch(
            this.statusPageId,
            {public: !this.isStatusPagePublic},
            () => this.isRequestLoading = true,
            () => refreshStatusPageList(),
            () => this.isRequestLoading = false
        );
    },
    deleteStatusPage() {
        statusPageApi.remove(
            this.statusPageId,
            () => this.isRequestLoading = true,
            () => refreshStatusPageList()
        );
    }
});

// Shared detail-page component for every monitor type; the API binding, status refresh and list path differ
const monitorDetails = (api, refreshDetailStatus, listPath) => (monitorId, isMonitorEnabled) => ({
    monitorId,
    isMonitorEnabled,
    isRequestLoading: false,

    toggleMonitor() {
        api.patch(
            this.monitorId,
            {enabled: !this.isMonitorEnabled},
            () => this.isRequestLoading = true,
            () => {
                this.isRequestLoading = false;
                this.isMonitorEnabled = !this.isMonitorEnabled;
                this.$dispatch(this.isMonitorEnabled ? 'monitor-enabled' : 'monitor-disabled');
                refreshDetailStatus();
            },
            () => this.isRequestLoading = false
        );
    },

    deleteMonitor() {
        api.remove(
            this.monitorId,
            () => this.isRequestLoading = true,
            () => window.location.href = listPath,
            () => this.isRequestLoading = false
        );
    }
});

const httpMonitorDetails = monitorDetails(httpMonitorApi, refreshHttpMonitorDetailStatus, '/http-monitors');
const pushMonitorDetails = monitorDetails(pushMonitorApi, refreshPushMonitorDetailStatus, '/push-monitors');
const icmpMonitorDetails = monitorDetails(icmpMonitorApi, refreshIcmpMonitorDetailStatus, '/icmp-monitors');
const tcpMonitorDetails = monitorDetails(tcpMonitorApi, refreshTcpMonitorDetailStatus, '/tcp-monitors');
const dnsMonitorDetails = monitorDetails(dnsMonitorApi, refreshDnsMonitorDetailStatus, '/dns-monitors');

const statusPageDetails = (statusPageId, isStatusPagePublic) => ({
    statusPageId,
    isStatusPagePublic,
    isRequestLoading: false,

    toggleStatusPageVisibility() {
        statusPageApi.patch(
            this.statusPageId,
            {public: !this.isStatusPagePublic},
            () => this.isRequestLoading = true,
            () => window.location.reload(),
            () => this.isRequestLoading = false
        );
    },

    deleteStatusPage() {
        statusPageApi.remove(
            this.statusPageId,
            () => this.isRequestLoading = true,
            () => window.location.href = '/status-pages',
            () => this.isRequestLoading = false
        );
    }
});

// Shared ApexCharts config for the metrics charts on the monitor detail pages
const baseAreaChartOptions = (noDataLabel, tooltipFormatter) => ({
    chart: {
        type: "area",
        fontFamily: "inherit",
        height: 240,
        parentHeightOffset: 0,
        toolbar: {
            show: false,
        },
        animations: {
            enabled: false,
        },
    },
    dataLabels: {
        enabled: false,
    },
    fill: {
        colors: [tabler.tabler.getColor("primary", 0.16), tabler.tabler.getColor("primary", 0.16)],
        type: "solid",
    },
    stroke: {
        width: 2,
        lineCap: "round",
        curve: "smooth",
    },
    noData: {
        text: noDataLabel,
        align: "center",
        verticalAlign: "middle",
    },
    series: [],
    tooltip: {
        enabled: true,
        x: {
            format: "yyyy/MM/dd HH:mm:ss",
        },
        y: {
            formatter: tooltipFormatter,
        },
        theme: "dark",
    },
    grid: {
        padding: {
            top: -20,
            right: 0,
            // ApexCharts 7 prefixes the day to the hour labels when the range crosses midnight, so the leftmost
            // label needs room not to be clipped
            left: 8,
            bottom: -4,
        },
        strokeDashArray: 4,
    },
    xaxis: {
        labels: {
            padding: 0,
            datetimeUTC: false
        },
        tooltip: {
            enabled: false,
        },
        axisBorder: {
            show: false,
        },
        type: "datetime",
    },
    yaxis: {
        // The incident markers sit on the zero line, so it always has to be the bottom of the chart
        min: 0,
        labels: {
            padding: 4,
        },
    },
    labels: [],
    annotations: {
        xaxis: [],
        points: [],
    },
    // ApexCharts can't resolve CSS color functions, so Tabler's own helper resolves the theme color for it
    colors: [tabler.tabler.getColor("primary")],
    legend: {
        show: false,
    },
});

// Gaps (null values) are rendered as a dash instead of "null ms" in the tooltips
const formatWithUnit = (unit) => (val) => val === null || val === undefined ? "-" : val + unit;

const padToTwoDigits = (number) => String(number).padStart(2, '0');

// Formats a date in the same way as the tooltips of the charts do (yyyy/MM/dd HH:mm:ss, in the local time zone)
const formatChartTimestamp = (date) =>
    `${date.getFullYear()}/${padToTwoDigits(date.getMonth() + 1)}/${padToTwoDigits(date.getDate())} ` +
    `${padToTwoDigits(date.getHours())}:${padToTwoDigits(date.getMinutes())}:${padToTwoDigits(date.getSeconds())}`;

// SSL incidents are returned for the HTTP monitors too, but they have nothing to do with the metrics of a monitor
const NON_METRICS_INCIDENT_TYPES = ['SSL'];

// Builds the chart annotations marking the start and the end of the incidents that fall into the displayed range.
// Every marker is a vertical line and a point on the zero line, the latter carrying the details of the incident in a
// tooltip. The details can be anything (e.g. the error message of a check), so they have to be escaped.
const buildIncidentAnnotations = (incidents, rangeStart, rangeEnd, labels, colors) => {
    const annotations = {xaxis: [], points: []};
    const isInRange = (timestamp) => timestamp >= rangeStart && timestamp <= rangeEnd;
    const addMarker = (timestamp, color, title, details) => {
        annotations.xaxis.push({
            x: timestamp,
            borderColor: color,
            strokeDashArray: 4,
        });
        annotations.points.push({
            x: timestamp,
            y: 0,
            yAxisIndex: 0,
            marker: {
                size: 5,
                fillColor: color,
                strokeColor: '#fff',
                strokeWidth: 2,
            },
            tooltip: {
                enabled: true,
                theme: 'dark',
                text: `<strong>${escapeHtml(title)}</strong>` +
                    `<div>${formatChartTimestamp(new Date(timestamp))}</div>` +
                    (details ? `<div>${escapeHtml(details)}</div>` : ''),
            },
        });
    };

    incidents
        .filter(incident => !NON_METRICS_INCIDENT_TYPES.includes(incident.incidentType))
        .forEach(incident => {
            const startedAt = new Date(incident.startedAt).getTime();
            if (isInRange(startedAt)) {
                addMarker(startedAt, colors.started, labels.incidentStarted, incident.details);
            }
            if (incident.endedAt) {
                const endedAt = new Date(incident.endedAt).getTime();
                if (isInRange(endedAt)) {
                    addMarker(endedAt, colors.resolved, labels.incidentResolved, incident.details);
                }
            }
        });
    return annotations;
};

// The time range displayed by a metrics chart: the selected period up to now. Not the range of the metrics logs, as an
// incident is always a bit newer than the log of the check causing it, and HTTP monitors don't log failed checks at all.
// Newer data extends the range, in case the local clock lags behind the server's. A loop instead of spreading the
// timestamps into Math.max, because the logs of a long period can exceed the maximum number of arguments.
const metricsChartRange = (period, now, logs, incidents) => {
    let end = now;
    const extendTo = (dateTime) => {
        if (dateTime) {
            end = Math.max(end, new Date(dateTime).getTime());
        }
    };
    logs.forEach(item => extendTo(item.createdAt));
    incidents.forEach(incident => {
        extendTo(incident.startedAt);
        extendTo(incident.endedAt);
    });
    return {start: now - isoDurationToMillis(period), end};
};

const nullableLatencyOf = (item) => item.latencyInMs !== null ? parseInt(item.latencyInMs) : null;

const toLatencyChartData = (logs, labels, latencyOf) => ({
    labels: logs.map(item => new Date(item.createdAt).toString()),
    series: [{name: labels.latency, data: logs.map(latencyOf)}],
});

const latencyChartOptions = (chartLabels) => baseAreaChartOptions(chartLabels.noData, formatWithUnit(" ms"));

// The shared lifecycle of the metrics blocks on the monitor detail pages: polls the stats and the incidents of the
// monitor in the selected period, and renders them with the type specific chart options and data transformation
const metricsBlock = ({
    monitorId,
    isMonitorEnabled,
    uptimeCheckInterval,
    chartLabels,
    period,
    statsPath,
    chartElementId,
    buildChartOptions,
    logsOf,
    toChartData,
}) => {
    return {
        isMonitorEnabled,
        chart: null,
        previousData: null,
        pollInterval: uptimeCheckInterval * 1000,
        isAutoRefreshEnabled: false,
        intervalId: null,
        lastResponse: null,
        chartLabels,
        markerColors: {},
        // Only an explicit change of the period shows the loader, the auto-refresh keeps showing the stale data instead
        isPeriodLoading: false,
        // An ISO-8601 duration, bound to the period selector of the block
        period,

        now() {
            return Date.now();
        },

        init() {
            this.initializeChart();
            this.startPolling();
            if (!this.isAutoRefreshEnabled) {
                this.stopPolling();
            }
            this.$watch('isAutoRefreshEnabled', (value) => {
                if (value) {
                    this.startPolling();
                } else {
                    this.stopPolling();
                }
            });
            this.$watch('period', () => this.refreshPeriod());
        },

        refreshPeriod() {
            this.previousData = null;
            this.isPeriodLoading = true;
            return this.pollEndpoint();
        },

        statsUrl() {
            return `/api/v2/${statsPath}/${monitorId}/stats?period=${this.period}`;
        },

        incidentsUrl() {
            return `/api/v2/incidents?monitorId=${monitorId}&period=${this.period}&includeResolved=true`;
        },

        startPolling() {
            this.pollEndpoint();
            this.intervalId = setInterval(() => this.pollEndpoint(), this.pollInterval);
        },

        stopPolling() {
            if (this.intervalId) {
                clearInterval(this.intervalId);
                this.intervalId = null;
            }
        },

        initializeChart() {
            this.markerColors = {
                started: tabler.tabler.getColor("red"),
                resolved: tabler.tabler.getColor("green"),
            };
            this.chart = new ApexCharts(document.getElementById(chartElementId), buildChartOptions(this.chartLabels));
            this.chart.render();
        },

        // The incidents are only decoration on the chart, so failing to fetch them must not block the metrics
        async fetchIncidents() {
            try {
                const response = await fetch(this.incidentsUrl());
                if (!response.ok) {
                    console.error('Error fetching incidents:', response.status);
                    return [];
                }
                return await response.json();
            } catch (error) {
                console.error('Error fetching incidents:', error);
                return [];
            }
        },

        async pollEndpoint() {
            const requestedPeriod = this.period;
            try {
                const [response, incidents] = await Promise.all([fetch(this.statsUrl()), this.fetchIncidents()]);
                if (!response.ok) {
                    console.error('Error fetching data:', response.status);
                    return;
                }
                const rawData = await response.json();
                // The period has been changed in the meantime, so this response is outdated
                if (requestedPeriod !== this.period) {
                    return;
                }
                this.lastResponse = rawData;
                const transformedData = this.transformData(rawData, incidents);
                // The range follows the clock, so comparing it too would re-render the same data on every poll
                const {range, ...displayedData} = transformedData;

                if (!this.previousData || JSON.stringify(displayedData) !== JSON.stringify(this.previousData)) {
                    this.updateChart(transformedData);
                    this.previousData = displayedData;
                }
            } catch (error) {
                console.error('Error during polling:', error);
            } finally {
                // Only the response of the currently selected period can end the loading of a period change
                if (requestedPeriod === this.period) {
                    this.isPeriodLoading = false;
                }
            }
        },

        transformData(rawData, incidents) {
            const logs = logsOf(rawData);
            const range = metricsChartRange(this.period, this.now(), logs, incidents);
            return {
                ...toChartData(logs, this.chartLabels),
                annotations: buildIncidentAnnotations(
                    incidents, range.start, range.end, this.chartLabels, this.markerColors,
                ),
                range,
            };
        },

        updateChart(newData) {
            // ApexCharts replaces (instead of merging) the arrays of the options, so the outdated markers are dropped
            this.chart.updateOptions({
                labels: newData.labels,
                series: newData.series,
                annotations: newData.annotations,
                // The axis spans the whole range, otherwise it would only fit the logs and cut off the newest markers
                xaxis: {min: newData.range.start, max: newData.range.end},
            });
        },
    };
};

const httpMetricsBlock = (monitorId, isMonitorEnabled, uptimeCheckInterval, chartLabels, period) => metricsBlock({
    monitorId,
    isMonitorEnabled,
    uptimeCheckInterval,
    chartLabels,
    period,
    statsPath: 'http-monitors',
    chartElementId: 'monitor-details-latency-chart',
    buildChartOptions: latencyChartOptions,
    logsOf: (rawData) => rawData.latencyLogs,
    toChartData: (logs, labels) => toLatencyChartData(logs, labels, (item) => parseInt(item.latencyInMs)),
});

// Latency and packet loss share a single chart, each of them with an axis of its own
const icmpChartOptions = (chartLabels) => {
    const options = baseAreaChartOptions(chartLabels.noData, null);
    return {
        ...options,
        chart: {...options.chart, type: "line"},
        colors: [tabler.tabler.getColor("primary"), tabler.tabler.getColor("orange")],
        fill: {
            type: "solid",
            opacity: [0.16, 1],
        },
        tooltip: {
            ...options.tooltip,
            shared: true,
            y: [{formatter: formatWithUnit(" ms")}, {formatter: formatWithUnit("%")}],
        },
        yaxis: [
            {
                seriesName: chartLabels.latency,
                min: 0,
                labels: {padding: 4, formatter: (val) => Math.round(val) + " ms"},
            },
            {
                seriesName: chartLabels.packetLoss,
                opposite: true,
                min: 0,
                max: 100,
                labels: {padding: 4, formatter: (val) => Math.round(val) + "%"},
            },
        ],
        legend: {
            show: true,
        },
    };
};

const icmpMetricsBlock = (monitorId, isMonitorEnabled, uptimeCheckInterval, chartLabels, period) => metricsBlock({
    monitorId,
    isMonitorEnabled,
    uptimeCheckInterval,
    chartLabels,
    period,
    statsPath: 'icmp-monitors',
    chartElementId: 'icmp-monitor-details-metrics-chart',
    buildChartOptions: icmpChartOptions,
    logsOf: (rawData) => rawData.metricsLogs,
    toChartData: (logs, labels) => ({
        labels: logs.map(item => new Date(item.createdAt).toString()),
        series: [
            {name: labels.latency, type: 'area', data: logs.map(nullableLatencyOf)},
            {
                name: labels.packetLoss,
                type: 'line',
                data: logs.map(item => parseInt(item.packetLossPercentage)),
            },
        ],
    }),
});

const tcpMetricsBlock = (monitorId, isMonitorEnabled, uptimeCheckInterval, chartLabels, period) => metricsBlock({
    monitorId,
    isMonitorEnabled,
    uptimeCheckInterval,
    chartLabels,
    period,
    statsPath: 'tcp-monitors',
    chartElementId: 'tcp-monitor-details-latency-chart',
    buildChartOptions: latencyChartOptions,
    logsOf: (rawData) => rawData.metricsLogs,
    toChartData: (logs, labels) => toLatencyChartData(logs, labels, nullableLatencyOf),
});

const dnsMetricsBlock = (monitorId, isMonitorEnabled, uptimeCheckInterval, chartLabels, period) => metricsBlock({
    monitorId,
    isMonitorEnabled,
    uptimeCheckInterval,
    chartLabels,
    period,
    statsPath: 'dns-monitors',
    chartElementId: 'dns-monitor-details-latency-chart',
    buildChartOptions: latencyChartOptions,
    logsOf: (rawData) => rawData.metricsLogs,
    toChartData: (logs, labels) => toLatencyChartData(logs, labels, nullableLatencyOf),
});

const hasNonNullValue = (obj) => Object.values(obj).some(value => value !== null);

const isValidUrl = (url) => {
    const urlPattern = /^(https?):\/\/[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]$/;
    return urlPattern.test(url);
};

const isValidSlug = (slug) => {
    const slugPattern = /^[a-z0-9_-]{1,50}/;
    return slugPattern.test(slug);
}

const blankError = (value, message) => !value || value.trim() === '' ? message : null;

const rangeError = (value, min, max, message) =>
    !value || isNaN(value) || value < min || value > max ? message : null;

const isBlankNumber = (value) => value === '' || value == null;

// Shared by the create/update forms of every entity, the forms provide resetState, populateFrom, validate and
// buildRequestBody
const upsertForm = ({api, entity, errorMessages, pagePath, entityLabel}) => ({
    errorMessages: errorMessages || {},
    isRequestLoading: false,
    formError: null,
    isUpdate: !!entity,
    entityId: entity?.id ?? null,
    isLoadingEntity: false,
    // Identifies the latest entity load, the responses of the earlier ones are discarded
    entityLoadId: 0,
    editTitle: null,

    init() {
        this.resetState();
    },

    // Points the form to the entity it saves: the one it was rendered for (none for a create form), or the one opened
    // from a list row by editFrom
    setEditTarget(target, editTitle) {
        Object.assign(this, {isUpdate: !!target, entityId: target?.id ?? null, editTitle});
    },

    loadEntity(entityId, onLoaded) {
        const loadId = ++this.entityLoadId;
        const isCurrentLoad = () => loadId === this.entityLoadId;
        return api.get(
            entityId,
            () => this.isLoadingEntity = true,
            async (response) => {
                const source = await response.json();
                if (!isCurrentLoad()) return;
                this.populateFrom(source);
                onLoaded(source);
                this.isLoadingEntity = false;
            },
            () => {
                if (isCurrentLoad()) this.isLoadingEntity = false;
            }
        );
    },

    // Keeps a load that is still in progress from populating the form after it has been reset, e.g. by closing the modal
    discardEntityLoad() {
        this.entityLoadId++;
        this.isLoadingEntity = false;
    },

    // Loads the entity of a list row, so the modal of the list updates it instead of creating a new one
    editFrom(entityId, title) {
        return this.loadEntity(entityId, (source) => this.setEditTarget(source, title));
    },

    // Loads the entity of a list row into the create form of the list, with the values its copy differs in
    cloneFrom(entityId, overrides) {
        return this.loadEntity(entityId, () => Object.assign(this, overrides));
    },

    submitForm() {
        this.validate();
        if (!hasNonNullValue(this.errors)) {
            this.upsert();
        }
    },

    handleConflict() {
        this.errors.name = this.errorMessages.nameAlreadyExists;
    },

    handleBadRequest(errorData) {
        this.formError = errorData.message;
    },

    async upsert() {
        const apiPath = '/api/v2' + pagePath;
        try {
            this.isRequestLoading = true;
            const response = await fetch(this.isUpdate ? `${apiPath}/${this.entityId}` : apiPath, {
                method: this.isUpdate ? 'PATCH' : 'POST',
                headers: jsonContentHeaders,
                body: JSON.stringify(this.buildRequestBody()),
            });
            if (response.ok) {
                const responseData = await response.json();
                if (this.isUpdate) {
                    window.location.reload();
                } else {
                    window.location.href = `${pagePath}/${responseData.id}`;
                }
            } else if (response.status === 409) {
                this.handleConflict();
            } else if (response.status === 400) {
                this.handleBadRequest(await response.json());
            } else {
                console.error(`Error creating/updating the ${entityLabel}:`, response.statusText);
                alert(`An error occurred while creating/updating the ${entityLabel}, refer to the console for more details`);
            }
        } catch (error) {
            console.error(`Error creating/updating the ${entityLabel}:`, error);
            alert(`An error occurred while creating/updating the ${entityLabel}. Please try again.`);
        } finally {
            this.isRequestLoading = false;
        }
    },
});

// Shared by every monitor type, the forms provide populateTypeFields, validateTypeFields and typeRequestBody
const monitorForm = ({api, pagePath, monitor, errorMessages, categorySelectId, isNameLocked, globalIntegrationCount}) => ({
    ...upsertForm({api, entity: monitor, errorMessages, pagePath, entityLabel: 'monitor'}),
    globalIntegrationCount: globalIntegrationCount || 0,
    // The name of a monitor can't be changed while it's on a status page that is read-only
    isNameLocked: !!isNameLocked,

    resetState() {
        this.discardEntityLoad();
        this.setEditTarget(monitor || null, null, !!isNameLocked);
        this.populateFrom(monitor || null);
    },

    setEditTarget(target, editTitle, isNameLocked) {
        Object.assign(this, {isUpdate: !!target, entityId: target?.id ?? null, editTitle, isNameLocked});
    },

    populateFrom(source) {
        this.name = source?.name || '';
        this.failureCountThreshold = source?.failureCountThreshold || 1;
        this.integrations = source?.integrations || [];
        this.category = source?.category || null;
        resetCategorySelect(categorySelectId, this.category);
        this.populateTypeFields(source);
        this.errors = {};
        this.formError = null;
    },

    cloneFrom(monitorId, clonedName) {
        return this.loadEntity(monitorId, () => {
            this.name = clonedName;
            this.regenerateUniqueFields();
        });
    },

    // Loads the monitor of a list row, so the modal of the list updates it instead of creating a new one
    editFrom(monitorId, title, isNameLocked) {
        return this.loadEntity(monitorId, (source) => this.setEditTarget(source, title, isNameLocked));
    },

    regenerateUniqueFields() {
    },

    validate() {
        this.errors = {};
        this.formError = null;
        this.validateName();
        this.validateCategory();
        this.validateFailureCountThreshold();
        this.validateTypeFields();
    },

    validateName() {
        this.errors.name = blankError(this.name, this.errorMessages.nameRequired);
    },

    validateCategory() {
        this.errors.category = this.category?.length > 100 ? this.errorMessages.categoryTooLong : null;
    },

    validateFailureCountThreshold() {
        this.errors.failureCountThreshold =
            rangeError(this.failureCountThreshold, 1, Infinity, this.errorMessages.failureCountThresholdInvalid);
    },

    handleBadRequest(errorData) {
        if (errorData.errorCode === 'MONITOR_NAME_CANNOT_BE_CHANGED') {
            this.errors.name = this.errorMessages.nameCannotBeChanged;
        } else {
            this.formError = errorData.message;
        }
    },

    buildRequestBody() {
        return {
            name: this.name,
            failureCountThreshold: this.failureCountThreshold,
            integrations: this.integrations,
            category: sanitizeTextInput(this.category),
            ...this.typeRequestBody(),
            ...(this.isUpdate ? {} : {enabled: true}),
        };
    },
});

const upsertHttpMonitorForm = (
    monitor,
    errorMessages,
    categorySelectId,
    isNameLocked,
    acceptedStatusCodeSelectId,
    supportedHttpStatusCodes,
    globalIntegrationCount
) => ({
    ...monitorForm({
        api: httpMonitorApi,
        pagePath: '/http-monitors',
        monitor,
        errorMessages,
        categorySelectId,
        isNameLocked,
        globalIntegrationCount,
    }),
    supportedHttpStatusCodes: supportedHttpStatusCodes || [],

    populateTypeFields(source) {
        this.url = source?.url || '';
        this.sensitiveUrl = source?.sensitiveUrl ?? false;
        this.sslExpiryThreshold = source?.sslExpiryThreshold || 30;
        this.uptimeCheckInterval = source?.uptimeCheckInterval || 60;
        this.sslCheckEnabled = source?.sslCheckEnabled ?? false;
        this.latencyHistoryEnabled = source?.latencyHistoryEnabled ?? true;
        this.forceNoCache = source?.forceNoCache ?? true;
        this.followRedirects = source?.followRedirects ?? true;
        this.crossOriginHeaderPropagation = source?.crossOriginHeaderPropagation ?? false;
        this.requestMethod = source?.requestMethod || 'GET';
        this.selectedHttpStatusCodes = source?.expectedStatusCodes?.map(code => code.toString()) || [];
        this.expectedKeyword = source?.expectedKeyword || null;
        this.expectedKeywordCaseSensitive = source?.expectedKeywordCaseSensitive || false;
        this.expectedKeywordNegated = source?.expectedKeywordNegated || false;
        this.responseTimeThresholdMillis = source?.responseTimeThresholdMillis || null;
        this.requestHeaders = source?.requestHeaders || {};
        this.expectedHeaders = source?.expectedHeaders || {};
        this.requestBody = source?.requestBody || null;
        this.newRequestHeaderKey = '';
        this.newRequestHeaderValue = '';
        this.isRequestHeaderAddable = false;
        this.newExpectedHeaderKey = '';
        this.newExpectedHeaderValue = '';
        this.isExpectedHeaderAddable = false;

        resetTomSelectState(acceptedStatusCodeSelectId, (ts) => {
            this.selectedHttpStatusCodes.forEach(code => ts.addItem(code, true));
        });
    },

    isValidHttpHeaderName(headerName) {
        if (headerName === null || headerName === undefined || headerName === '') return true;
        const headerPattern = /^[a-zA-Z0-9!#$'*+-.^`|~_&%]+$/;
        return headerPattern.test(headerName);
    },

    validateNewHeader(headerKey, errorKey) {
        const isValidHeaderName = this.isValidHttpHeaderName(headerKey);
        this.errors[errorKey] = isValidHeaderName ? null : this.errorMessages.requestHeaderInvalid;
        return isValidHeaderName;
    },

    validateNewRequestHeader() {
        const isValidHeader = this.validateNewHeader(this.newRequestHeaderKey, 'newRequestHeader');
        this.isRequestHeaderAddable = isValidHeader && this.newRequestHeaderKey.trim() !== '' && this.newRequestHeaderValue.trim() !== '';
    },

    validateNewExpectedHeader() {
        const isValidHeader = this.validateNewHeader(this.newExpectedHeaderKey, 'newExpectedHeader');
        this.isExpectedHeaderAddable = isValidHeader && this.newExpectedHeaderKey.trim() !== '' && this.newExpectedHeaderValue.trim() !== '';
    },

    addRequestHeader() {
        this.validateNewRequestHeader();
        if (this.errors.newRequestHeader) return;
        this.requestHeaders[this.newRequestHeaderKey] = this.newRequestHeaderValue;
        this.newRequestHeaderKey = '';
        this.newRequestHeaderValue = '';
    },

    addExpectedHeader() {
        this.validateNewExpectedHeader();
        if (this.errors.newExpectedHeader) return;
        this.expectedHeaders[this.newExpectedHeaderKey] = this.newExpectedHeaderValue;
        this.newExpectedHeaderKey = '';
        this.newExpectedHeaderValue = '';
    },

    removeRequestHeader(key) {
        delete this.requestHeaders[key];
    },

    removeExpectedHeader(key) {
        delete this.expectedHeaders[key];
    },

    validateTypeFields() {
        this.validateUrl();
        this.validateSslExpiryThreshold();
        this.validateUptimeCheckInterval();
        this.validateResponseTimeThreshold();
        this.validateRequestBody();
    },

    validateUrl() {
        if (!this.url) {
            this.errors.url = this.errorMessages.urlRequired;
        } else {
            this.errors.url = isValidUrl(this.url) ? null : this.errorMessages.urlInvalid;
        }
    },

    validateSslExpiryThreshold() {
        this.errors.sslExpiryThreshold =
            rangeError(this.sslExpiryThreshold, 0, Infinity, this.errorMessages.sslExpiryThresholdInvalid);
    },

    validateUptimeCheckInterval() {
        this.errors.uptimeCheckInterval =
            rangeError(this.uptimeCheckInterval, 5, Infinity, this.errorMessages.uptimeCheckIntervalInvalid);
    },

    validateResponseTimeThreshold() {
        this.errors.responseTimeThresholdMillis = this.responseTimeThresholdMillis === null
            ? null
            : rangeError(this.responseTimeThresholdMillis, 1, 30000, this.errorMessages.responseTimeThresholdInvalid);
    },

    validateRequestBody() {
        if (!this.requestBody || this.requestBody.trim() === '') {
            this.requestBody = null;
            this.errors.requestBody = null;
            return;
        }
        try {
            JSON.parse(this.requestBody);
            this.errors.requestBody = null;
        } catch (e) {
            this.errors.requestBody = this.errorMessages.requestBodyInvalid;
        }
    },

    typeRequestBody() {
        return {
            url: this.url,
            sensitiveUrl: this.sensitiveUrl,
            sslCheckEnabled: this.sslCheckEnabled,
            latencyHistoryEnabled: this.latencyHistoryEnabled,
            sslExpiryThreshold: this.sslExpiryThreshold,
            forceNoCache: this.forceNoCache,
            followRedirects: this.followRedirects,
            crossOriginHeaderPropagation: this.crossOriginHeaderPropagation,
            uptimeCheckInterval: this.uptimeCheckInterval,
            requestMethod: this.requestMethod,
            expectedStatusCodes: this.selectedHttpStatusCodes,
            expectedKeyword: sanitizeTextInput(this.expectedKeyword),
            expectedKeywordCaseSensitive: this.expectedKeywordCaseSensitive,
            expectedKeywordNegated: this.expectedKeywordNegated,
            responseTimeThresholdMillis: this.responseTimeThresholdMillis,
            requestHeaders: this.requestHeaders,
            expectedHeaders: this.expectedHeaders,
            requestBody: sanitizeTextInput(this.requestBody),
        };
    },
});

const upsertPushMonitorForm = (monitor, errorMessages, categorySelectId, isNameLocked, globalIntegrationCount) => ({
    ...monitorForm({
        api: pushMonitorApi,
        pagePath: '/push-monitors',
        monitor,
        errorMessages,
        categorySelectId,
        isNameLocked,
        globalIntegrationCount,
    }),

    populateTypeFields(source) {
        this.heartbeatInterval = source?.heartbeatInterval || 10;
        this.gracePeriod = source?.gracePeriod || 0;
        this.clientSecret = source?.clientSecret || createRandomSecret();
    },

    regenerateUniqueFields() {
        this.clientSecret = createRandomSecret();
    },

    generateNewClientSecret() {
        this.clientSecret = createRandomSecret();
        this.validateClientSecret();
    },

    copyClientSecretToClipboard() {
        const baseUrl = window.location.protocol + '//' + window.location.host;
        navigator.clipboard.writeText(baseUrl + '/api/v2/push-monitors/heartbeats/' + this.clientSecret);
    },

    validateTypeFields() {
        this.validateHeartbeatInterval();
        this.validateGracePeriod();
        this.validateClientSecret();
    },

    validateHeartbeatInterval() {
        this.errors.heartbeatInterval =
            rangeError(this.heartbeatInterval, 10, Infinity, this.errorMessages.heartbeatIntervalInvalid);
    },

    validateGracePeriod() {
        const isInvalid = this.gracePeriod === undefined || this.gracePeriod === '' || isNaN(this.gracePeriod) || this.gracePeriod < 0;
        this.errors.gracePeriod = isInvalid ? this.errorMessages.gracePeriodInvalid : null;
    },

    validateClientSecret() {
        this.clientSecret = sanitizeTextInput(this.clientSecret);
        const isInvalid = !this.clientSecret || this.clientSecret.length < 36;
        this.errors.clientSecret = isInvalid ? this.errorMessages.clientSecretInvalid : null;
    },

    handleConflict() {
        this.errors.name = this.errorMessages.nameOrClientSecretAlreadyExists;
        this.errors.clientSecret = this.errorMessages.nameOrClientSecretAlreadyExists;
    },

    typeRequestBody() {
        return {
            heartbeatInterval: this.heartbeatInterval,
            gracePeriod: this.gracePeriod,
            clientSecret: this.clientSecret,
        };
    },
});

const upsertIcmpMonitorForm = (monitor, errorMessages, categorySelectId, isNameLocked, globalIntegrationCount) => ({
    ...monitorForm({
        api: icmpMonitorApi,
        pagePath: '/icmp-monitors',
        monitor,
        errorMessages,
        categorySelectId,
        isNameLocked,
        globalIntegrationCount,
    }),

    populateTypeFields(source) {
        this.host = source?.host || '';
        this.uptimeCheckInterval = source?.uptimeCheckInterval || 60;
        this.packetCount = source?.packetCount || 3;
        this.timeoutSeconds = source?.timeoutSeconds || 5;
        this.packetLossThreshold = source?.packetLossThreshold || 100;
        this.metricsHistoryEnabled = source?.metricsHistoryEnabled ?? true;
    },

    validateTypeFields() {
        this.validateHost();
        this.validateUptimeCheckInterval();
        this.validatePacketCount();
        this.validateTimeoutSeconds();
        this.validatePacketLossThreshold();
    },

    validateHost() {
        this.errors.host = blankError(this.host, this.errorMessages.hostRequired);
    },

    validateUptimeCheckInterval() {
        this.errors.uptimeCheckInterval =
            rangeError(this.uptimeCheckInterval, 5, Infinity, this.errorMessages.uptimeCheckIntervalInvalid);
    },

    validatePacketCount() {
        this.errors.packetCount = rangeError(this.packetCount, 1, 10, this.errorMessages.packetCountInvalid);
    },

    validateTimeoutSeconds() {
        this.errors.timeoutSeconds = rangeError(this.timeoutSeconds, 1, 30, this.errorMessages.timeoutSecondsInvalid);
    },

    validatePacketLossThreshold() {
        this.errors.packetLossThreshold =
            rangeError(this.packetLossThreshold, 1, 100, this.errorMessages.packetLossThresholdInvalid);
    },

    typeRequestBody() {
        return {
            host: this.host,
            uptimeCheckInterval: this.uptimeCheckInterval,
            packetCount: this.packetCount,
            timeoutSeconds: this.timeoutSeconds,
            packetLossThreshold: this.packetLossThreshold,
            metricsHistoryEnabled: this.metricsHistoryEnabled,
        };
    },
});

const upsertTcpMonitorForm = (monitor, errorMessages, categorySelectId, isNameLocked, globalIntegrationCount) => ({
    ...monitorForm({
        api: tcpMonitorApi,
        pagePath: '/tcp-monitors',
        monitor,
        errorMessages,
        categorySelectId,
        isNameLocked,
        globalIntegrationCount,
    }),

    populateTypeFields(source) {
        this.host = source?.host || '';
        this.port = source?.port || '';
        this.uptimeCheckInterval = source?.uptimeCheckInterval || 60;
        this.timeoutMs = source?.timeoutMs || 5000;
        this.latencyThresholdMs = source?.latencyThresholdMs ?? '';
        this.metricsHistoryEnabled = source?.metricsHistoryEnabled ?? true;
    },

    validateTypeFields() {
        this.validateHost();
        this.validatePort();
        this.validateUptimeCheckInterval();
        this.validateTimeoutMs();
        this.validateLatencyThreshold();
    },

    validateHost() {
        this.errors.host = blankError(this.host, this.errorMessages.hostRequired);
    },

    validatePort() {
        this.errors.port = rangeError(this.port, 1, 65535, this.errorMessages.portInvalid);
    },

    validateUptimeCheckInterval() {
        this.errors.uptimeCheckInterval =
            rangeError(this.uptimeCheckInterval, 5, Infinity, this.errorMessages.uptimeCheckIntervalInvalid);
    },

    validateTimeoutMs() {
        this.errors.timeoutMs = rangeError(this.timeoutMs, 1, 30000, this.errorMessages.timeoutMsInvalid);
    },

    validateLatencyThreshold() {
        this.errors.latencyThresholdMs = isBlankNumber(this.latencyThresholdMs)
            ? null
            : rangeError(this.latencyThresholdMs, 1, Infinity, this.errorMessages.latencyThresholdInvalid);
    },

    typeRequestBody() {
        return {
            host: this.host,
            port: parseInt(this.port),
            uptimeCheckInterval: this.uptimeCheckInterval,
            timeoutMs: this.timeoutMs,
            latencyThresholdMs: isBlankNumber(this.latencyThresholdMs) ? null : parseInt(this.latencyThresholdMs),
            metricsHistoryEnabled: this.metricsHistoryEnabled,
        };
    },
});

const upsertDnsMonitorForm = (monitor, errorMessages, categorySelectId, isNameLocked, globalIntegrationCount) => ({
    ...monitorForm({
        api: dnsMonitorApi,
        pagePath: '/dns-monitors',
        monitor,
        errorMessages,
        categorySelectId,
        isNameLocked,
        globalIntegrationCount,
    }),

    populateTypeFields(source) {
        this.host = source?.host || '';
        this.resolverHost = source?.resolverHost ?? '';
        this.resolverPort = source?.resolverPort || 53;
        this.transport = source?.transport || 'UDP';
        this.recordMatchers = source?.recordMatchers ? JSON.parse(JSON.stringify(source.recordMatchers)) : [];
        this.expectedResponseCode = source?.expectedResponseCode || 'NOERROR';
        this.driftDetectionEnabled = source?.driftDetectionEnabled ?? false;
        this.driftRecordTypes = source?.driftRecordTypes ? [...source.driftRecordTypes] : [];
        this.uptimeCheckInterval = source?.uptimeCheckInterval || 60;
        this.timeoutMs = source?.timeoutMs || 5000;
        this.latencyThresholdMs = source?.latencyThresholdMs ?? '';
        this.metricsHistoryEnabled = source?.metricsHistoryEnabled ?? true;
        this.newMatcherRecordType = 'A';
        this.newMatcherMatchType = 'CONTAINS';
        this.newMatcherValue = '';
        this.isMatcherAddable = false;
    },

    validateTypeFields() {
        this.validateHost();
        this.validateResolverPort();
        this.validateUptimeCheckInterval();
        this.validateTimeoutMs();
        this.validateLatencyThreshold();
        this.validateResponseCodeMatchers();
    },

    validateHost() {
        this.errors.host = blankError(this.host, this.errorMessages.hostRequired);
    },

    validateResolverPort() {
        this.errors.resolverPort = rangeError(this.resolverPort, 1, 65535, this.errorMessages.resolverPortInvalid);
    },

    validateUptimeCheckInterval() {
        this.errors.uptimeCheckInterval =
            rangeError(this.uptimeCheckInterval, 5, Infinity, this.errorMessages.uptimeCheckIntervalInvalid);
    },

    validateTimeoutMs() {
        this.errors.timeoutMs = rangeError(this.timeoutMs, 1, 30000, this.errorMessages.timeoutMsInvalid);
    },

    validateLatencyThreshold() {
        this.errors.latencyThresholdMs = isBlankNumber(this.latencyThresholdMs)
            ? null
            : rangeError(this.latencyThresholdMs, 1, Infinity, this.errorMessages.latencyThresholdInvalid);
    },

    isValidRegex(value) {
        try {
            new RegExp(value);
            return true;
        } catch (e) {
            return false;
        }
    },

    validateNewMatcher() {
        const value = (this.newMatcherValue || '').trim();
        const isRegexValid = this.newMatcherMatchType !== 'REGEX' || this.isValidRegex(value);
        this.errors.newMatcher = value !== '' && !isRegexValid ? this.errorMessages.recordMatcherInvalid : null;
        this.isMatcherAddable = value !== '' && isRegexValid;
    },

    validateResponseCodeMatchers() {
        const isConflicting = this.expectedResponseCode !== 'NOERROR' && this.recordMatchers.length > 0;
        this.errors.recordMatchers = isConflicting ? this.errorMessages.responseCodeMatchersConflict : null;
    },

    addMatcher() {
        this.validateNewMatcher();
        if (!this.isMatcherAddable) return;
        const candidate = {
            recordType: this.newMatcherRecordType,
            matchType: this.newMatcherMatchType,
            value: this.newMatcherValue.trim(),
        };
        // An identical matcher would only be evaluated twice, so adding it again is a no-op (the server dedupes
        // the list as well, which would otherwise make the saved monitor differ from what the form shows)
        const isDuplicate = this.recordMatchers.some(matcher =>
            matcher.recordType === candidate.recordType &&
            matcher.matchType === candidate.matchType &&
            matcher.value === candidate.value
        );
        if (!isDuplicate) {
            this.recordMatchers.push(candidate);
        }
        this.newMatcherValue = '';
        this.isMatcherAddable = false;
        this.validateResponseCodeMatchers();
    },

    removeMatcher(index) {
        this.recordMatchers.splice(index, 1);
        this.validateResponseCodeMatchers();
    },

    typeRequestBody() {
        return {
            host: this.host,
            resolverHost: this.resolverHost === '' || this.resolverHost == null ? null : this.resolverHost,
            resolverPort: parseInt(this.resolverPort),
            transport: this.transport,
            recordMatchers: this.recordMatchers,
            expectedResponseCode: this.expectedResponseCode,
            driftDetectionEnabled: this.driftDetectionEnabled,
            driftRecordTypes: this.driftRecordTypes,
            uptimeCheckInterval: this.uptimeCheckInterval,
            timeoutMs: this.timeoutMs,
            latencyThresholdMs: isBlankNumber(this.latencyThresholdMs) ? null : parseInt(this.latencyThresholdMs),
            metricsHistoryEnabled: this.metricsHistoryEnabled,
        };
    },
});

const upsertStatusPageForm = (
    statusPage,
    errorMessages,
    monitorSelectId,
    selectableMonitors,
    categorySelectId,
) => ({
    ...upsertForm({
        api: statusPageApi,
        entity: statusPage,
        errorMessages,
        pagePath: '/status-pages',
        entityLabel: 'status page',
    }),
    selectableMonitors: selectableMonitors || [],
    /*
     The persisted categories have to be in the DOM as options before TomSelect takes the select over, because
     the rest of them only arrives when the fetch resolves. Never reassigned, so it cannot loop with x-model.
    */
    initialCategories: statusPage?.categories || [],
    imagePreviewState: {},

    resetState() {
        this.discardEntityLoad();
        this.setEditTarget(statusPage || null, null);
        this.populateFrom(statusPage || null);
    },

    populateFrom(source) {
        this.title = source?.title || '';
        this.slug = source?.slug || '';
        this.customLogoUrl = source?.customLogoUrl || null;
        this.customFaviconUrl = source?.customFaviconUrl || null;
        this.selectedMonitors = source?.monitors || [];
        this.selectedCategories = source?.categories || [];
        this.displayCategories = source?.displayCategories ?? true;
        this.public = source?.public ?? false;
        this.errors = {};
        this.formError = null;

        resetTomSelectState(monitorSelectId, (ts) => {
            this.selectedMonitors.forEach(monitor => ts.addItem(monitor, true));
        });
        resetCategoryMultiSelect(categorySelectId, this.selectedCategories);
    },

    validate() {
        this.errors = {};
        this.formError = null;
        this.validateTitle();
        this.validateSlug();
    },

    validateTitle() {
        this.errors.title = this.title ? null : this.errorMessages.titleRequired;
    },

    validateSlug() {
        if (!this.slug) {
            this.errors.slug = this.errorMessages.slugRequired;
        } else {
            this.errors.slug = isValidSlug(this.slug) ? null : this.errorMessages.slugInvalid;
        }
    },

    handleConflict() {
        this.errors.slug = this.errorMessages.slugAlreadyExists;
    },

    buildRequestBody() {
        return {
            title: this.title,
            slug: this.slug,
            customLogoUrl: this.customLogoUrl,
            customFaviconUrl: this.customFaviconUrl,
            monitors: this.selectedMonitors,
            categories: this.selectedCategories,
            displayCategories: this.displayCategories,
            public: this.public,
        };
    },
});

const integrationListItem = (integrationId) => {
    return {
        integrationId: integrationId,
        wasTestRequestExecuted: false,
        isTestRequestLoading: false,
        testRequestError: null,

        async sendTestRequest() {
            if (this.isTestRequestLoading || this.wasTestRequestExecuted) return; // Prevent multiple clicks
            this.isTestRequestLoading = true;
            const encodedIntegrationId = encodeURIComponent(this.integrationId);
            const response = await fetch('/api/v2/integrations/' + encodedIntegrationId + '/test', {
                method: 'POST',
                headers: jsonContentHeaders
            })
            if (response.ok) {
                const data = await response.json();
                if (data.success) {
                    this.testRequestError = null;
                    showToast(this.integrationId, data.message, 'status-green', true);
                } else {
                    this.testRequestError = data.message || 'Unknown error';
                    showToast(this.integrationId, this.testRequestError, 'status-red', false);
                }
                this.wasTestRequestExecuted = true;
                this.isTestRequestLoading = false;
            } else {
                this.wasTestRequestExecuted = false;
                this.isTestRequestLoading = false;
                console.error('Error sending test request:', response.statusText);
                alert('An error occurred during sending a test request, refer to the console logs for the details.');
            }
        }
    }
};

// TomSelect helpers & renderers

/*
 Resetting the TomSelect component, otherwise the state of the select won't be cleared
 Without this, the previously selected values will remain in the select box, and also in alpine's state,
 because TomSelect re-initializes the select element, which then will be bound to alpine, and we're in a
 loop then.
*/
const resetTomSelectState = (elementId, afterReset = (tomSelectInstance) => {
}) => {
    const monitorSelect = document.getElementById(elementId);
    monitorSelect?.tomselect?.clear(true);
    if (monitorSelect?.tomselect instanceof TomSelect) {
        afterReset(monitorSelect.tomselect);
    }
};

/*
 Loads every category the instance knows about: the ones in use by a monitor, plus the ones that are only referenced
 by a status page or a maintenance window. Fails open with an empty list: the selects take a brand new category
 anyway, so an unreachable endpoint must not block the form.
*/
const fetchCategories = async () => {
    try {
        const response = await fetch('/api/internal/categories');
        return response.ok ? await response.json() : [];
    } catch (error) {
        console.error('Error fetching the categories:', error);
        return [];
    }
};

// The "add a brand new one" row of a category select, shared by the single and the multi value variants
const categoryCreateRenderer = (addLabel) => ({
    option_create: (data, escape) => `<div class="create">${escape(addLabel)}: <strong>${escape(data.input)}</strong></div>`
});

/*
 The options are loaded when the modal opens, not when it's rendered. The dashboard renders the create modal of all
 five monitor types at once, and a form nobody opens shouldn't cost a request.
*/
const loadCategoryOptions = (tomSelect) => {
    const loadOptions = () => fetchCategories().then(categories => {
        // Already known options are ignored by TomSelect, so re-opening the modal just picks up the new ones
        tomSelect.addOptions(categories.map(category => ({value: category, text: category})));
    });
    const modal = tomSelect.input.closest('.modal');
    if (modal) {
        modal.addEventListener('show.bs.modal', loadOptions);
    } else {
        loadOptions();
    }
};

/*
 The single value category select of the monitor forms: it offers the already existing categories with an
 autocomplete, but a brand new one can be typed in as well.
*/
const initCategorySelect = (selector, addLabel) => {
    const tomSelect = new TomSelect(selector, {
        create: true,
        persist: false,
        maxItems: 1,
        plugins: ['clear_button'],
        render: categoryCreateRenderer(addLabel)
    });
    loadCategoryOptions(tomSelect);
};

/*
 The multi value category select of the status page and maintenance window forms, where the categories select the
 covered monitors in addition to the ones picked explicitly. A category that is not in use by any monitor yet is
 accepted here too, it simply covers nothing until a monitor is tagged with it.
*/
const initCategoryMultiSelect = (selector, addLabel) => {
    const tomSelect = new TomSelect(selector, {
        create: true,
        persist: false,
        maxOptions: null,
        plugins: ['clear_button', 'remove_button'],
        render: categoryCreateRenderer(addLabel),
        onItemAdd: function () {
            this.setTextboxValue('');
        }
    });
    loadCategoryOptions(tomSelect);
};

/*
 Restores the category of the form after a reset (edit, clone). The option itself has to be re-added, because a
 category that isn't persisted yet is dropped by TomSelect as soon as the selection is cleared (persist: false).
*/
const resetCategorySelect = (elementId, category) => {
    resetTomSelectState(elementId, (ts) => {
        if (category) {
            ts.addOption({value: category, text: category});
            ts.setValue(category, true);
        }
    });
};

/*
 The multi value counterpart of resetCategorySelect. The options have to be re-added for the same reason: a category
 that the endpoint does not offer (yet) is dropped by TomSelect as soon as the selection is cleared.
*/
const resetCategoryMultiSelect = (elementId, categories) => {
    resetTomSelectState(elementId, (ts) => {
        (categories || []).forEach(category => {
            ts.addOption({value: category, text: category});
            ts.addItem(category, true);
        });
    });
};

const renderMonitorOption = (data, escape) => {
    const parts = splitWithLimit(data.value, ':', 2);
    const type = parts[0];
    const name = parts[1];
    const badgeColor = type === 'http' ? 'bg-blue-lt text-blue-lt-fg' : type === 'push' ? 'bg-red-lt text-red-lt-fg' : type === 'icmp' ? 'bg-orange-lt text-orange-lt-fg' : type === 'tcp' ? 'bg-purple-lt text-purple-lt-fg' : type === 'dns' ? 'bg-cyan-lt text-cyan-lt-fg' : '';
    return `<div><span class="badge me-2 ${badgeColor}">${escape(type.toUpperCase())}</span>${escape(name)}</div>`;
};

const renderStatusCodeOption = (data, escape) => {
    const statusClass = statusCodeToBadgeClass(data.value);
    return `<div><span class="status-dot ${statusClass} me-2"></span>${escape(data.text)}</div>`;
};

const renderStatusCodeItem = (data, escape) => {
    const statusClass = statusCodeToBadgeClass(data.value);
    return `<div><span class="status-dot ${statusClass} me-2"></span>${escape(data.value)}</div>`;
};

// Shared Alpine component for every YAML import modal
const importForm = (config) => {
    return {
        file: null,
        dryRun: true,
        isRequestLoading: false,
        error: null,
        result: null,
        importCompleted: false,
        errors: {},
        labels: config.labels || {},

        resetState() {
            this.file = null;
            this.dryRun = true;
            this.isRequestLoading = false;
            this.error = null;
            this.result = null;
            this.importCompleted = false;
            this.errors = {};
            const fileInput = document.getElementById(config.fileInputId);
            if (fileInput) {
                fileInput.value = '';
            }
        },

        handleFileChange(event) {
            this.file = event.target.files[0] || null;
            this.errors = {};
            this.error = null;
        },

        get submitButtonLabel() {
            return this.dryRun ? this.labels.previewButton : this.labels.importButton;
        },

        // Flat single-entity result
        formatResult(result) {
            return result.receivedCnt + ' ' + this.labels.countReceivedLabel + ' / ' +
                result.imported.length + ' ' + this.labels.countImportedLabel + ' / ' +
                result.deleted.length + ' ' + this.labels.countDeletedLabel;
        },

        // Per-monitor-type result (monitors)
        formatTypeResult(typeResult) {
            let typeLabel;
            switch (typeResult.monitorType) {
                case 'HTTP_SSL':
                    typeLabel = this.labels.typeHttpLabel;
                    break;
                case 'PUSH':
                    typeLabel = this.labels.typePushLabel;
                    break;
                case 'ICMP':
                    typeLabel = this.labels.typeIcmpLabel;
                    break;
                case 'TCP':
                    typeLabel = this.labels.typeTcpLabel;
                    break;
                case 'DNS':
                    typeLabel = this.labels.typeDnsLabel;
                    break;
                default:
                    typeLabel = typeResult.monitorType;
            }
            return typeLabel + ': ' +
                typeResult.receivedCnt + ' ' + this.labels.countReceivedLabel + ' / ' +
                typeResult.imported.length + ' ' + this.labels.countImportedLabel + ' / ' +
                typeResult.deleted.length + ' ' + this.labels.countDeletedLabel;
        },

        async submitForm() {
            this.errors = {};

            if (!this.file) {
                this.errors.file = this.labels.fileRequired;
                return;
            }

            this.isRequestLoading = true;
            this.error = null;
            this.result = null;

            const formData = new FormData();
            formData.append('file', this.file);

            try {
                const response = await fetch(config.endpoint + '?dryRun=' + this.dryRun, {
                    method: 'POST',
                    body: formData
                });

                const data = await response.json();

                if (response.ok) {
                    this.result = data;
                    if (!this.dryRun) {
                        this.importCompleted = true;
                    }
                } else {
                    this.error = data.message || this.labels.importFailed;
                }
            } catch (err) {
                console.error('Import failed:', err);
                this.error = this.labels.importFailed;
            } finally {
                this.isRequestLoading = false;
            }
        }
    };
};

const monitorImportForm = (labels) => importForm({
    labels: labels,
    endpoint: '/api/v2/monitors/import/yaml',
    fileInputId: 'monitor-import-file-input',
});

const statusPageImportForm = (labels) => importForm({
    labels: labels,
    endpoint: '/api/v2/status-pages/import/yaml',
    fileInputId: 'status-page-import-file-input',
});

const maintenanceWindowImportForm = (labels) => importForm({
    labels: labels,
    endpoint: '/api/v2/maintenance-windows/import/yaml',
    fileInputId: 'maintenance-window-import-file-input',
});

// ---------------------------------------------------------------------------
// Maintenance windows
// ---------------------------------------------------------------------------

const MAINTENANCE_WINDOW_TYPES = {MANUAL: 'MANUAL', CRON: 'CRON', SINGLE: 'SINGLE'};

// Matches a positive ISO-8601 duration (weeks/days/time components), e.g. PT1H30M, P1DT2H, PT45S
const isoDurationRegex = /^P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$/;

const isValidIsoDuration = (value) => {
    if (!value || !isoDurationRegex.test(value)) return false;
    const numbers = value.match(/\d+(?:\.\d+)?/g);
    return !!numbers && numbers.some(n => parseFloat(n) > 0);
};

// The milliseconds of the week, day, hour, minute and second components, in the order of isoDurationRegex's groups
const ISO_DURATION_COMPONENT_MILLIS = [7 * 24 * 60 * 60 * 1000, 24 * 60 * 60 * 1000, 60 * 60 * 1000, 60 * 1000, 1000];

const isoDurationToMillis = (value) => value.match(isoDurationRegex)
    .slice(1)
    .reduce((millis, amount, index) => millis + parseFloat(amount ?? 0) * ISO_DURATION_COMPONENT_MILLIS[index], 0);

// Validates a cron expression server-side against Micronaut's CronExpression parser (the single source of truth).
// Returns true when valid and false on a 400; network/other errors fail open so the authoritative submit can decide.
const isValidCronExpression = async (value) => {
    try {
        const response = await fetch('/api/internal/validation/cron?value=' + encodeURIComponent(value));
        return response.status !== 400;
    } catch (error) {
        console.error('Error validating cron expression:', error);
        return true;
    }
};

// Converts an ISO timestamp into the value expected by a datetime-local input (in the browser's local time)
const toDateTimeLocalValue = (isoString) => {
    if (!isoString) return '';
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '';
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
        `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

const resolveMaintenanceWindowType = (window) => {
    if (!window) return MAINTENANCE_WINDOW_TYPES.MANUAL;
    if (window.cron) return MAINTENANCE_WINDOW_TYPES.CRON;
    if (window.start) return MAINTENANCE_WINDOW_TYPES.SINGLE;
    return MAINTENANCE_WINDOW_TYPES.MANUAL;
};

const refreshMaintenanceWindowList = () => {
    sendHtmxEvent('#maintenance-window-list', 'refresh-maintenance-window-list');
};

const refreshMaintenanceWindowDetailStatus = () => {
    sendHtmxEvent('#maintenance-window-detail-heading', 'refresh-maintenance-window-detail-status');
};

const maintenanceWindowListItem = (maintenanceWindowId, isMaintenanceWindowEnabled, clonedFields, editTitle) => ({
    maintenanceWindowId,
    isMaintenanceWindowEnabled,
    clonedFields,
    editTitle,
    isRequestLoading: false,
    cloneMaintenanceWindow() {
        this.$dispatch('clone-maintenance-window', {id: this.maintenanceWindowId, fields: this.clonedFields});
    },
    editMaintenanceWindow() {
        this.$dispatch('edit-maintenance-window', {id: this.maintenanceWindowId, title: this.editTitle});
    },
    toggleMaintenanceWindow() {
        maintenanceWindowApi.patch(
            this.maintenanceWindowId,
            {enabled: !this.isMaintenanceWindowEnabled},
            () => this.isRequestLoading = true,
            () => refreshMaintenanceWindowList(),
            () => this.isRequestLoading = false
        );
    },
    deleteMaintenanceWindow() {
        maintenanceWindowApi.remove(
            this.maintenanceWindowId,
            () => this.isRequestLoading = true,
            () => refreshMaintenanceWindowList(),
            () => this.isRequestLoading = false
        );
    }
});

const maintenanceWindowDetails = (maintenanceWindowId, isMaintenanceWindowEnabled) => ({
    maintenanceWindowId,
    isMaintenanceWindowEnabled,
    isRequestLoading: false,
    toggleMaintenanceWindow() {
        maintenanceWindowApi.patch(
            this.maintenanceWindowId,
            {enabled: !this.isMaintenanceWindowEnabled},
            () => this.isRequestLoading = true,
            () => {
                this.isRequestLoading = false;
                this.isMaintenanceWindowEnabled = !this.isMaintenanceWindowEnabled;
                refreshMaintenanceWindowDetailStatus();
            },
            () => this.isRequestLoading = false
        );
    },
    deleteMaintenanceWindow() {
        maintenanceWindowApi.remove(
            this.maintenanceWindowId,
            () => this.isRequestLoading = true,
            () => window.location.href = '/maintenance-windows',
            () => this.isRequestLoading = false
        );
    }
});

const upsertMaintenanceWindowForm = (
    maintenanceWindow,
    errorMessages,
    monitorSelectId,
    selectableMonitors,
    categorySelectId,
) => ({
    ...upsertForm({
        api: maintenanceWindowApi,
        entity: maintenanceWindow,
        errorMessages,
        pagePath: '/maintenance-windows',
        entityLabel: 'maintenance window',
    }),
    selectableMonitors: selectableMonitors || [],
    // See the same field on upsertStatusPageForm
    initialCategories: maintenanceWindow?.categories || [],
    // The integrations accordion expects this; maintenance windows never auto-apply global integrations
    globalIntegrationCount: 0,

    resetState() {
        this.discardEntityLoad();
        this.setEditTarget(maintenanceWindow || null, null);
        this.populateFrom(maintenanceWindow || null);
    },

    populateFrom(source) {
        this.name = source?.name || '';
        this.description = source?.description || null;
        this.type = resolveMaintenanceWindowType(source);
        this.cron = source?.cron || '';
        this.start = toDateTimeLocalValue(source?.start);
        this.duration = source?.duration || '';
        this.enabled = source?.enabled ?? true;
        this.global = source?.global ?? false;
        this.showOnStatusPages = source?.showOnStatusPages ?? false;
        this.selectedMonitors = source?.monitors || [];
        this.selectedCategories = source?.categories || [];
        this.integrations = source?.integrations || [];
        this.errors = {};
        this.formError = null;

        resetTomSelectState(monitorSelectId, (ts) => {
            this.selectedMonitors.forEach(monitor => ts.addItem(monitor, true));
        });
        resetCategoryMultiSelect(categorySelectId, this.selectedCategories);
    },

    validate() {
        this.errors = {};
        this.formError = null;
        this.validateName();
        this.validateCronPresence();
        this.validateStart();
        this.validateDuration();
    },

    // Clears the values of fields that don't belong to the freshly selected type, so we never send
    // non-sense values to the backend and never keep a hidden validation error on an invisible field
    onTypeChange() {
        if (this.type !== MAINTENANCE_WINDOW_TYPES.CRON) {
            this.cron = '';
        }
        if (this.type !== MAINTENANCE_WINDOW_TYPES.SINGLE) {
            this.start = '';
        }
        if (this.type === MAINTENANCE_WINDOW_TYPES.MANUAL) {
            this.duration = '';
        }
        this.validate();
    },

    validateName() {
        this.errors.name = this.name ? null : this.errorMessages.nameRequired;
    },

    // Synchronous part of the cron validation: clears the error for non-cron windows and flags a blank value
    validateCronPresence() {
        const isMissing = this.type === MAINTENANCE_WINDOW_TYPES.CRON && !this.cron;
        this.errors.cron = isMissing ? this.errorMessages.cronRequired : null;
    },

    // Full cron validation including the server-side format check; runs when the field is left or on submit
    async validateCron() {
        this.validateCronPresence();
        if (this.errors.cron || this.type !== MAINTENANCE_WINDOW_TYPES.CRON) {
            return;
        }
        const valid = await isValidCronExpression(this.cron);
        this.errors.cron = valid ? null : this.errorMessages.cronInvalid;
    },

    validateStart() {
        const isMissing = this.type === MAINTENANCE_WINDOW_TYPES.SINGLE && !this.start;
        this.errors.start = isMissing ? this.errorMessages.startRequired : null;
    },

    // Fills the duration input with a predefined ISO-8601 value coming from a quick-select button
    setDuration(value) {
        this.duration = value;
        this.validateDuration();
    },

    validateDuration() {
        if (this.type === MAINTENANCE_WINDOW_TYPES.MANUAL) {
            this.errors.duration = null;
        } else if (!this.duration) {
            this.errors.duration = this.errorMessages.durationRequired;
        } else if (!isValidIsoDuration(this.duration)) {
            this.errors.duration = this.errorMessages.durationInvalid;
        } else {
            this.errors.duration = null;
        }
    },

    async submitForm() {
        this.validate();
        await this.validateCron();
        if (!hasNonNullValue(this.errors)) {
            this.upsert();
        }
    },

    buildRequestBody() {
        const isManual = this.type === MAINTENANCE_WINDOW_TYPES.MANUAL;
        const isCron = this.type === MAINTENANCE_WINDOW_TYPES.CRON;
        const isSingle = this.type === MAINTENANCE_WINDOW_TYPES.SINGLE;
        return {
            name: this.name,
            description: this.description || null,
            enabled: this.enabled,
            global: this.global,
            showOnStatusPages: this.showOnStatusPages,
            cron: isCron ? this.cron : null,
            start: isSingle && this.start ? new Date(this.start).toISOString() : null,
            duration: isManual ? null : this.duration,
            monitors: this.selectedMonitors,
            categories: this.selectedCategories,
            integrations: this.integrations,
        };
    },
});

// Exposes helpers and Alpine x-data factories for the Node-based unit tests (see ui/src/jsTest and the :ui:jsTest task)
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        // Pure, DOM-free helpers
        MAINTENANCE_WINDOW_TYPES,
        sanitizeTextInput,
        splitWithLimit,
        statusCodeToBadgeClass,
        escapeHtml,
        buildToastMarkup,
        hasNonNullValue,
        formatChartTimestamp,
        buildIncidentAnnotations,
        isValidUrl,
        isValidSlug,
        isValidIsoDuration,
        isoDurationToMillis,
        toDateTimeLocalValue,
        resolveMaintenanceWindowType,
        createRandomSecret,
        // Helpers of the category select
        fetchCategories,
        resetCategorySelect,
        resetCategoryMultiSelect,
        // Alpine x-data component factories
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
    };
}
