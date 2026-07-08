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

// Shows a toast notification using Bootstrap's toast component
const showToast = (header, content, backgroundClass, autoHide) => {
    const html =
        `<div class="toast fade ${backgroundClass}" role="alert" aria-live="assertive" aria-atomic="true" data-bs-autohide="${autoHide}" ${autoHide ? 'data-bs-delay="3000"' : ''}>
            <div class="toast-header">
                <strong class="me-auto">${header}</strong>
                <button type="button" class="ms-2 btn-close" data-bs-dismiss="toast" aria-label="Close"></button>
            </div>
            <div class="toast-body">${content}</div>
        </div>`.trim();
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
            onSuccess(response);
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

// Builds the PATCH/DELETE helpers for a given entity, keeping the URL and user-facing labels in one place
const crudRequests = (basePath, entityLabel) => ({
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
const statusPageApi = crudRequests('/api/v2/status-pages', 'status page');
const maintenanceWindowApi = crudRequests('/api/v2/maintenance-windows', 'maintenance window');

// --------- HTMX refresh triggers ---------
// Refreshes a monitor detail page's dynamic status blocks by triggering an HTMX event (OOB swap)
const refreshHttpMonitorDetailStatus = () => sendHtmxEvent('#http-monitor-detail-heading', 'refresh-monitor-detail-status');
const refreshPushMonitorDetailStatus = () => sendHtmxEvent('#push-monitor-detail-heading', 'refresh-monitor-detail-status');
const refreshIcmpMonitorDetailStatus = () => sendHtmxEvent('#icmp-monitor-detail-heading', 'refresh-monitor-detail-status');

// Refreshes a monitor list by triggering an HTMX event
const refreshHttpMonitorList = () => sendHtmxEvent('#http-monitors-list', 'refresh-monitor-list');
const refreshPushMonitorList = () => sendHtmxEvent('#push-monitors-list', 'refresh-monitor-list');
const refreshIcmpMonitorList = () => sendHtmxEvent('#icmp-monitors-list', 'refresh-monitor-list');

// Refreshes the status page list by triggering an HTMX event
const refreshStatusPageList = () => sendHtmxEvent('#status-page-list', 'refresh-status-page-list');

// Refreshes the dashboard by triggering an HTMX event
const refreshDashboard = () => {
    sendHtmxEvent('#http-monitoring-dashboard', 'refresh-dashboard');
    sendHtmxEvent('#push-monitoring-dashboard', 'refresh-dashboard');
    sendHtmxEvent('#icmp-monitoring-dashboard', 'refresh-dashboard');
};

// --------- Alpine.js x-data ---------

// Shared list-row component for every monitor type; only the API binding and list refresh differ
const monitorListItem = (api, refreshList) => (monitorId, isMonitorEnabled, assignedToStatusPage) => ({
    monitorId,
    isMonitorEnabled,
    assignedToStatusPage,
    isRequestLoading: false,
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
const pushMonitorListItem = monitorListItem(pushMonitorApi, refreshPushMonitorList);

const statusPageListItem = (statusPageId, isStatusPagePublic) => ({
    statusPageId,
    isStatusPagePublic,
    isRequestLoading: false,
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

// Shared ApexCharts config for the latency/packet-loss area charts on the monitor detail pages
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
        colors: ["color-mix(in srgb, transparent, var(--tblr-primary) 16%)", "color-mix(in srgb, transparent, var(--tblr-primary) 16%)"],
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
            left: -4,
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
        labels: {
            padding: 4,
        },
    },
    labels: [],
    colors: ["color-mix(in srgb, transparent, var(--tblr-primary) 100%)"],
    legend: {
        show: false,
    },
});

const httpMetricsBlock = (monitorId, isMonitorEnabled, uptimeCheckInterval, noDataLabel, statPeriodInHours) => {
    return {
        isMonitorEnabled,
        chart: null,
        previousData: null,
        endpointUrl: `/api/v2/http-monitors/${monitorId}/stats?period=PT${statPeriodInHours}H`,
        pollInterval: uptimeCheckInterval * 1000,
        isAutoRefreshEnabled: false,
        intervalId: null,
        lastResponse: null,
        noDataLabel,

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
            const options = baseAreaChartOptions(this.noDataLabel, (val) => val + " ms");
            this.chart = new ApexCharts(document.getElementById("monitor-details-latency-chart"), options);
            this.chart.render();
        },

        async pollEndpoint() {
            try {
                const response = await fetch(this.endpointUrl);
                if (!response.ok) {
                    console.error('Error fetching data:', response.status);
                    return;
                }
                const rawData = await response.json();
                this.lastResponse = rawData;
                const transformedData = this.transformData(rawData);

                if (!this.previousData || JSON.stringify(transformedData) !== JSON.stringify(this.previousData)) {
                    this.updateChart(transformedData);
                    this.previousData = transformedData;
                }
            } catch (error) {
                console.error('Error during polling:', error);
            }
        },

        transformData(rawData) {
            const newLabels = [];
            const newData = [];

            rawData.latencyLogs.forEach(item => {
                newLabels.push(new Date(item.createdAt).toString());
                newData.push(parseInt(item.latencyInMs));
            });

            return {
                labels: newLabels,
                series: [{
                    name: 'Latency',
                    data: newData,
                }],
            };
        },

        updateChart(newData) {
            this.chart.updateOptions({
                labels: newData.labels,
                series: newData.series,
            });
        },
    };
};

const icmpMetricsBlock = (monitorId, isMonitorEnabled, uptimeCheckInterval, noDataLabel, statPeriodInHours) => {
    return {
        isMonitorEnabled,
        latencyChart: null,
        packetLossChart: null,
        previousData: null,
        endpointUrl: `/api/v2/icmp-monitors/${monitorId}/stats?period=PT${statPeriodInHours}H`,
        pollInterval: uptimeCheckInterval * 1000,
        isAutoRefreshEnabled: false,
        intervalId: null,
        lastResponse: null,
        noDataLabel,

        init() {
            this.initializeCharts();
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

        initializeCharts() {
            const latencyOptions = baseAreaChartOptions(this.noDataLabel, (val) => val + " ms");
            this.latencyChart = new ApexCharts(document.getElementById("icmp-monitor-details-latency-chart"), latencyOptions);
            this.latencyChart.render();

            const packetLossOptions = baseAreaChartOptions(this.noDataLabel, (val) => val + "%");
            this.packetLossChart = new ApexCharts(document.getElementById("icmp-monitor-details-packet-loss-chart"), packetLossOptions);
            this.packetLossChart.render();
        },

        async pollEndpoint() {
            try {
                const response = await fetch(this.endpointUrl);
                if (!response.ok) {
                    console.error('Error fetching data:', response.status);
                    return;
                }
                const rawData = await response.json();
                this.lastResponse = rawData;
                const transformedData = this.transformData(rawData);

                if (!this.previousData || JSON.stringify(transformedData) !== JSON.stringify(this.previousData)) {
                    this.updateCharts(transformedData);
                    this.previousData = transformedData;
                }
            } catch (error) {
                console.error('Error during polling:', error);
            }
        },

        transformData(rawData) {
            const latencyLabels = [];
            const latencyData = [];
            const packetLossLabels = [];
            const packetLossData = [];

            rawData.metricsLogs.forEach(item => {
                const timestamp = new Date(item.createdAt).toString();
                latencyLabels.push(timestamp);
                latencyData.push(item.latencyInMs !== null ? parseInt(item.latencyInMs) : null);
                packetLossLabels.push(timestamp);
                packetLossData.push(parseInt(item.packetLossPercentage));
            });

            return {
                latency: {
                    labels: latencyLabels,
                    series: [{name: 'Latency', data: latencyData}],
                },
                packetLoss: {
                    labels: packetLossLabels,
                    series: [{name: 'Packet loss', data: packetLossData}],
                },
            };
        },

        updateCharts(newData) {
            this.latencyChart.updateOptions({
                labels: newData.latency.labels,
                series: newData.latency.series,
            });
            this.packetLossChart.updateOptions({
                labels: newData.packetLoss.labels,
                series: newData.packetLoss.series,
            });
        },
    };
};

const hasNonNullValue = (obj) => Object.values(obj).some(value => value !== null);

const isValidUrl = (url) => {
    const urlPattern = /^(https?):\/\/[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]$/;
    return urlPattern.test(url);
};

const isValidSlug = (slug) => {
    const slugPattern = /^[a-z0-9_-]{1,50}/;
    return slugPattern.test(slug);
}

const upsertHttpMonitorForm = (
    monitor,
    errorMessages,
    acceptedStatusCodeSelectId,
    supportedHttpStatusCodes,
    globalIntegrationCount
) => {
    const originalMonitor = monitor || null;
    return {
        errorMessages: errorMessages || {},
        isRequestLoading: false,
        isUpdate: !!monitor,
        supportedHttpStatusCodes: supportedHttpStatusCodes || [],
        globalIntegrationCount: globalIntegrationCount || 0,

        init() {
            this.resetState();
        },

        resetState() {
            this.name = originalMonitor?.name || '';
            this.url = originalMonitor?.url || '';
            this.sensitiveUrl = (originalMonitor?.sensitiveUrl != null ? originalMonitor?.sensitiveUrl : false);
            this.sslExpiryThreshold = originalMonitor?.sslExpiryThreshold || 30;
            this.failureCountThreshold = originalMonitor?.failureCountThreshold || 1;
            this.uptimeCheckInterval = originalMonitor?.uptimeCheckInterval || 60;
            this.sslCheckEnabled = (originalMonitor?.sslCheckEnabled != null ? originalMonitor?.sslCheckEnabled : false);
            this.latencyHistoryEnabled = (originalMonitor?.latencyHistoryEnabled != null ? originalMonitor?.latencyHistoryEnabled : true);
            this.forceNoCache = (originalMonitor?.forceNoCache != null ? originalMonitor?.forceNoCache : true);
            this.followRedirects = (originalMonitor?.followRedirects != null ? originalMonitor?.followRedirects : true);
            this.requestMethod = originalMonitor?.requestMethod || 'GET';
            this.integrations = originalMonitor?.integrations || [];
            this.selectedHttpStatusCodes = originalMonitor?.expectedStatusCodes?.map(code => code.toString()) || [];
            this.expectedKeyword = originalMonitor?.expectedKeyword || null;
            this.expectedKeywordCaseSensitive = originalMonitor?.expectedKeywordCaseSensitive || false;
            this.expectedKeywordNegated = originalMonitor?.expectedKeywordNegated || false;
            this.responseTimeThresholdMillis = originalMonitor?.responseTimeThresholdMillis || null;
            this.requestHeaders = originalMonitor?.requestHeaders || {};
            this.expectedHeaders = originalMonitor?.expectedHeaders || {};
            this.requestBody = originalMonitor?.requestBody || null;
            this.newRequestHeaderKey = '';
            this.newRequestHeaderValue = '';
            this.isRequestHeaderAddable = false;
            this.newExpectedHeaderKey = '';
            this.newExpectedHeaderValue = '';
            this.isExpectedHeaderAddable = false;
            this.errors = {};

            resetTomSelectState(acceptedStatusCodeSelectId, (ts) => {
                this.selectedHttpStatusCodes.forEach(code => {
                    ts.addItem(code, true);
                });
            });
        },

        isValidHttpHeaderName(headerName) {
            if (headerName === null || headerName === undefined || headerName === '') return true;
            const headerPattern = /^[a-zA-Z0-9!#$'*+-.^`|~_&%]+$/;
            return headerPattern.test(headerName);
        },

        validateNewHeader(headerKey, headerValue, errorKey) {
            const isValidHeaderName = this.isValidHttpHeaderName(headerKey)
            if (!isValidHeaderName) {
                this.errors[errorKey] = this.errorMessages.requestHeaderInvalid;
            } else {
                this.errors[errorKey] = null;
            }
            return isValidHeaderName;
        },

        validateNewRequestHeader() {
            const isValidHeader = this.validateNewHeader(this.newRequestHeaderKey, this.newRequestHeaderValue, 'newRequestHeader');
            this.isRequestHeaderAddable = isValidHeader && this.newRequestHeaderKey.trim() !== '' && this.newRequestHeaderValue.trim() !== '';
        },

        validateNewExpectedHeader() {
            const isValidHeader = this.validateNewHeader(this.newExpectedHeaderKey, this.newExpectedHeaderValue, 'newExpectedHeader');
            this.isExpectedHeaderAddable = isValidHeader && this.newExpectedHeaderKey.trim() !== '' && this.newExpectedHeaderValue.trim() !== '';
        },

        addRequestHeader() {
            this.validateNewRequestHeader();
            if (this.errors.newRequestHeader) return
            this.requestHeaders[this.newRequestHeaderKey] = this.newRequestHeaderValue;
            this.newRequestHeaderKey = '';
            this.newRequestHeaderValue = '';
        },

        addExpectedHeader() {
            this.validateNewExpectedHeader();
            if (this.errors.newExpectedHeader) return
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

        validate() {
            this.errors = {};
            this.validateName();
            this.validateUrl();
            this.validateSslExpiryThreshold();
            this.validateFailureCountThreshold();
            this.validateUptimeCheckInterval();
            this.validateResponseTimeThreshold();
        },

        validateName() {
            if (!this.name || this.name.trim() === '') {
                this.errors.name = errorMessages.nameRequired;
            } else {
                this.errors.name = null;
            }
        },

        validateUrl() {
            if (!this.url) {
                this.errors.url = errorMessages.urlRequired;
            } else if (!isValidUrl(this.url)) {
                this.errors.url = errorMessages.urlInvalid;
            } else {
                this.errors.url = null;
            }
        },

        validateSslExpiryThreshold() {
            if (!this.sslExpiryThreshold || isNaN(this.sslExpiryThreshold) || this.sslExpiryThreshold < 0) {
                this.errors.sslExpiryThreshold = this.errorMessages.sslExpiryThresholdInvalid;
            } else {
                this.errors.sslExpiryThreshold = null;
            }
        },

        validateFailureCountThreshold() {
            if (!this.failureCountThreshold || isNaN(this.failureCountThreshold) || this.failureCountThreshold < 1) {
                this.errors.failureCountThreshold = this.errorMessages.failureCountThresholdInvalid;
            } else {
                this.errors.failureCountThreshold = null;
            }
        },

        validateUptimeCheckInterval() {
            if (!this.uptimeCheckInterval || isNaN(this.uptimeCheckInterval) || this.uptimeCheckInterval < 5) {
                this.errors.uptimeCheckInterval = this.errorMessages.uptimeCheckIntervalInvalid;
            } else {
                this.errors.uptimeCheckInterval = null;
            }
        },

        validateResponseTimeThreshold() {
            if (this.responseTimeThresholdMillis !== null && (isNaN(this.responseTimeThresholdMillis) || this.responseTimeThresholdMillis < 1 || this.responseTimeThresholdMillis > 30000)) {
                this.errors.responseTimeThresholdMillis = this.errorMessages.responseTimeThresholdInvalid;
            } else {
                this.errors.responseTimeThresholdMillis = null;
            }
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

        submitForm() {
            this.validate();
            if (hasNonNullValue(this.errors)) {
                return;
            }

            this.upsertMonitor();
        },

        async upsertMonitor() {
            try {
                this.isRequestLoading = true;
                const body = {
                    name: this.name,
                    url: this.url,
                    sensitiveUrl: this.sensitiveUrl,
                    sslCheckEnabled: this.sslCheckEnabled,
                    latencyHistoryEnabled: this.latencyHistoryEnabled,
                    sslExpiryThreshold: this.sslExpiryThreshold,
                    failureCountThreshold: this.failureCountThreshold,
                    forceNoCache: this.forceNoCache,
                    followRedirects: this.followRedirects,
                    uptimeCheckInterval: this.uptimeCheckInterval,
                    requestMethod: this.requestMethod,
                    integrations: this.integrations,
                    expectedStatusCodes: this.selectedHttpStatusCodes,
                    expectedKeyword: sanitizeTextInput(this.expectedKeyword),
                    expectedKeywordCaseSensitive: this.expectedKeywordCaseSensitive,
                    expectedKeywordNegated: this.expectedKeywordNegated,
                    responseTimeThresholdMillis: this.responseTimeThresholdMillis,
                    requestHeaders: this.requestHeaders,
                    expectedHeaders: this.expectedHeaders,
                    requestBody: sanitizeTextInput(this.requestBody)
                };
                if (!this.isUpdate) {
                    body.enabled = true; // Default enabled, can be paused later
                }

                const url = this.isUpdate ? '/api/v2/http-monitors/' + monitor.id : '/api/v2/http-monitors';
                const method = this.isUpdate ? 'PATCH' : 'POST';

                const response = await fetch(url, {
                    method: method,
                    headers: jsonContentHeaders,
                    body: JSON.stringify(body)
                });

                if (response.ok) {
                    this.isRequestLoading = false;
                    const responseData = await response.json();

                    if (this.isUpdate) {
                        window.location.reload();
                    } else {
                        window.location.href = '/http-monitors/' + responseData.id;
                    }
                } else {
                    if (response.status === 409) {
                        this.isRequestLoading = false;
                        this.errors.name = this.errorMessages.nameAlreadyExists;
                    } else if (response.status === 400) {
                        const errorData = await response.json();
                        this.isRequestLoading = false;
                        if (errorData.errorCode === 'MONITOR_NAME_CANNOT_BE_CHANGED') {
                            this.errors.name = this.errorMessages.nameCannotBeChanged;
                        }
                    } else {
                        console.error('Error creating/updating monitor:', response.statusText);
                        alert('An error occurred while creating/updating the monitor, refer to the console for more details');
                        this.isRequestLoading = false;
                    }
                }
            } catch (error) {
                this.isRequestLoading = false;
                console.error('Error creating monitor:', error);
                alert('An error occurred while creating/updating the monitor. Please try again.');
            }
        }
    }
};

const upsertPushMonitorForm = (
    monitor,
    errorMessages,
    globalIntegrationCount
) => {
    const originalMonitor = monitor || null;
    return {
        errorMessages: errorMessages || {},
        isRequestLoading: false,
        isUpdate: !!monitor,
        globalIntegrationCount: globalIntegrationCount || 0,

        init() {
            this.resetState();
        },

        resetState() {
            this.name = originalMonitor?.name || '';
            this.heartbeatInterval = originalMonitor?.heartbeatInterval || 10;
            this.gracePeriod = originalMonitor?.gracePeriod || 0;
            this.failureCountThreshold = originalMonitor?.failureCountThreshold || 1;
            this.clientSecret = originalMonitor?.clientSecret || createRandomSecret();
            this.integrations = originalMonitor?.integrations || [];
            this.errors = {};
        },

        generateNewClientSecret() {
            this.clientSecret = createRandomSecret();
            this.validateClientSecret()
        },

        copyClientSecretToClipboard() {
            const baseUrl = window.location.protocol + '//' + window.location.host
            const absoluteUrl = baseUrl + '/api/v2/push-monitors/heartbeats/' + this.clientSecret;
            navigator.clipboard.writeText(absoluteUrl);
        },

        validate() {
            this.errors = {};
            this.validateName();
            this.validateHeartbeatInterval();
            this.validateGracePeriod();
            this.validateClientSecret();
            this.validateFailureCountThreshold();
        },

        validateName() {
            if (!this.name || this.name.trim() === '') {
                this.errors.name = errorMessages.nameRequired;
            } else {
                this.errors.name = null;
            }
        },

        validateHeartbeatInterval() {
            if (!this.heartbeatInterval || isNaN(this.heartbeatInterval) || this.heartbeatInterval < 10) {
                this.errors.heartbeatInterval = this.errorMessages.heartbeatIntervalInvalid;
            } else {
                this.errors.heartbeatInterval = null;
            }
        },

        validateGracePeriod() {
            if (this.gracePeriod === undefined || this.gracePeriod === '' || isNaN(this.gracePeriod) || this.gracePeriod < 0) {
                this.errors.gracePeriod = this.errorMessages.gracePeriodInvalid;
            } else {
                this.errors.gracePeriod = null;
            }
        },

        validateClientSecret() {
            this.clientSecret = sanitizeTextInput(this.clientSecret);
            if (!this.clientSecret || this.clientSecret.length < 36) {
                this.errors.clientSecret = this.errorMessages.clientSecretInvalid;
            } else {
                this.errors.clientSecret = null;
            }
        },

        validateFailureCountThreshold() {
            if (!this.failureCountThreshold || isNaN(this.failureCountThreshold) || this.failureCountThreshold < 1) {
                this.errors.failureCountThreshold = this.errorMessages.failureCountThresholdInvalid;
            } else {
                this.errors.failureCountThreshold = null;
            }
        },

        submitForm() {
            this.validate();
            if (hasNonNullValue(this.errors)) {
                return;
            }

            this.upsertMonitor();
        },

        async upsertMonitor() {
            try {
                this.isRequestLoading = true;
                const body = {
                    name: this.name,
                    heartbeatInterval: this.heartbeatInterval,
                    gracePeriod: this.gracePeriod,
                    clientSecret: this.clientSecret,
                    integrations: this.integrations,
                    failureCountThreshold: this.failureCountThreshold
                };
                if (!this.isUpdate) {
                    body.enabled = true; // Default enabled, can be paused later
                }

                const url = this.isUpdate ? '/api/v2/push-monitors/' + monitor.id : '/api/v2/push-monitors';
                const method = this.isUpdate ? 'PATCH' : 'POST';

                const response = await fetch(url, {
                    method: method,
                    headers: jsonContentHeaders,
                    body: JSON.stringify(body)
                });

                if (response.ok) {
                    this.isRequestLoading = false;
                    const responseData = await response.json();

                    if (this.isUpdate) {
                        window.location.reload();
                    } else {
                        window.location.href = '/push-monitors/' + responseData.id;
                    }
                } else {
                    if (response.status === 409) {
                        this.isRequestLoading = false;
                        this.errors.name = this.errorMessages.nameOrClientSecretAlreadyExists;
                        this.errors.clientSecret = this.errorMessages.nameOrClientSecretAlreadyExists;
                    } else if (response.status === 400) {
                        const errorData = await response.json();
                        this.isRequestLoading = false;
                        if (errorData.errorCode === 'MONITOR_NAME_CANNOT_BE_CHANGED') {
                            this.errors.name = this.errorMessages.nameCannotBeChanged;
                        }
                    } else {
                        console.error('Error creating/updating monitor:', response.statusText);
                        alert('An error occurred while creating/updating the monitor, refer to the console for more details');
                        this.isRequestLoading = false;
                    }
                }
            } catch (error) {
                this.isRequestLoading = false;
                console.error('Error creating monitor:', error);
                alert('An error occurred while creating/updating the monitor. Please try again.');
            }
        }
    }
};

const upsertIcmpMonitorForm = (
    monitor,
    errorMessages,
    globalIntegrationCount
) => {
    const originalMonitor = monitor || null;
    return {
        errorMessages: errorMessages || {},
        isRequestLoading: false,
        isUpdate: !!monitor,
        globalIntegrationCount: globalIntegrationCount || 0,

        init() {
            this.resetState();
        },

        resetState() {
            this.name = originalMonitor?.name || '';
            this.host = originalMonitor?.host || '';
            this.uptimeCheckInterval = originalMonitor?.uptimeCheckInterval || 60;
            this.packetCount = originalMonitor?.packetCount || 3;
            this.timeoutSeconds = originalMonitor?.timeoutSeconds || 5;
            this.packetLossThreshold = originalMonitor?.packetLossThreshold || 100;
            this.failureCountThreshold = originalMonitor?.failureCountThreshold || 1;
            this.integrations = originalMonitor?.integrations || [];
            this.metricsHistoryEnabled = (originalMonitor?.metricsHistoryEnabled != null ? originalMonitor?.metricsHistoryEnabled : true);
            this.errors = {};
        },

        validate() {
            this.errors = {};
            this.validateName();
            this.validateHost();
            this.validateUptimeCheckInterval();
            this.validatePacketCount();
            this.validateTimeoutSeconds();
            this.validatePacketLossThreshold();
            this.validateFailureCountThreshold();
        },

        validateName() {
            if (!this.name || this.name.trim() === '') {
                this.errors.name = this.errorMessages.nameRequired;
            } else {
                this.errors.name = null;
            }
        },

        validateHost() {
            if (!this.host || this.host.trim() === '') {
                this.errors.host = this.errorMessages.hostRequired;
            } else {
                this.errors.host = null;
            }
        },

        validateUptimeCheckInterval() {
            if (!this.uptimeCheckInterval || isNaN(this.uptimeCheckInterval) || this.uptimeCheckInterval < 5) {
                this.errors.uptimeCheckInterval = this.errorMessages.uptimeCheckIntervalInvalid;
            } else {
                this.errors.uptimeCheckInterval = null;
            }
        },

        validatePacketCount() {
            if (!this.packetCount || isNaN(this.packetCount) || this.packetCount < 1 || this.packetCount > 10) {
                this.errors.packetCount = this.errorMessages.packetCountInvalid;
            } else {
                this.errors.packetCount = null;
            }
        },

        validateTimeoutSeconds() {
            if (!this.timeoutSeconds || isNaN(this.timeoutSeconds) || this.timeoutSeconds < 1 || this.timeoutSeconds > 30) {
                this.errors.timeoutSeconds = this.errorMessages.timeoutSecondsInvalid;
            } else {
                this.errors.timeoutSeconds = null;
            }
        },

        validatePacketLossThreshold() {
            if (!this.packetLossThreshold || isNaN(this.packetLossThreshold) || this.packetLossThreshold < 1 || this.packetLossThreshold > 100) {
                this.errors.packetLossThreshold = this.errorMessages.packetLossThresholdInvalid;
            } else {
                this.errors.packetLossThreshold = null;
            }
        },

        validateFailureCountThreshold() {
            if (!this.failureCountThreshold || isNaN(this.failureCountThreshold) || this.failureCountThreshold < 1) {
                this.errors.failureCountThreshold = this.errorMessages.failureCountThresholdInvalid;
            } else {
                this.errors.failureCountThreshold = null;
            }
        },

        submitForm() {
            this.validate();
            if (hasNonNullValue(this.errors)) {
                return;
            }
            this.upsertMonitor();
        },

        async upsertMonitor() {
            try {
                this.isRequestLoading = true;
                const body = {
                    name: this.name,
                    host: this.host,
                    uptimeCheckInterval: this.uptimeCheckInterval,
                    packetCount: this.packetCount,
                    timeoutSeconds: this.timeoutSeconds,
                    packetLossThreshold: this.packetLossThreshold,
                    failureCountThreshold: this.failureCountThreshold,
                    integrations: this.integrations,
                    metricsHistoryEnabled: this.metricsHistoryEnabled,
                };
                if (!this.isUpdate) {
                    body.enabled = true;
                }

                const url = this.isUpdate ? '/api/v2/icmp-monitors/' + monitor.id : '/api/v2/icmp-monitors';
                const method = this.isUpdate ? 'PATCH' : 'POST';

                const response = await fetch(url, {
                    method: method,
                    headers: jsonContentHeaders,
                    body: JSON.stringify(body)
                });

                if (response.ok) {
                    this.isRequestLoading = false;
                    const responseData = await response.json();

                    if (this.isUpdate) {
                        window.location.reload();
                    } else {
                        window.location.href = '/icmp-monitors/' + responseData.id;
                    }
                } else {
                    if (response.status === 409) {
                        this.isRequestLoading = false;
                        this.errors.name = this.errorMessages.nameAlreadyExists;
                    } else if (response.status === 400) {
                        const errorData = await response.json();
                        this.isRequestLoading = false;
                        if (errorData.errorCode === 'MONITOR_NAME_CANNOT_BE_CHANGED') {
                            this.errors.name = this.errorMessages.nameCannotBeChanged;
                        }
                    } else {
                        console.error('Error creating/updating ICMP monitor:', response.statusText);
                        alert('An error occurred while creating/updating the monitor, refer to the console for more details');
                        this.isRequestLoading = false;
                    }
                }
            } catch (error) {
                this.isRequestLoading = false;
                console.error('Error creating ICMP monitor:', error);
                alert('An error occurred while creating/updating the monitor. Please try again.');
            }
        }
    }
};

const upsertStatusPageForm = (
    statusPage,
    errorMessages,
    monitorSelectId,
    selectableMonitors,
) => {
    const originalStatusPage = statusPage || null;
    return {
        errorMessages: errorMessages || {},
        isRequestLoading: false,
        isUpdate: !!statusPage,
        selectableMonitors: selectableMonitors || [],
        imagePreviewState: {},

        init() {
            this.resetState();
        },

        resetState() {
            this.title = originalStatusPage?.title || '';
            this.slug = originalStatusPage?.slug || '';
            this.customLogoUrl = originalStatusPage?.customLogoUrl || null;
            this.customFaviconUrl = originalStatusPage?.customFaviconUrl || null;
            this.selectedMonitors = originalStatusPage?.monitors || [];
            this.public = (originalStatusPage?.public != null ? originalStatusPage?.public : false);
            this.errors = {};

            resetTomSelectState(monitorSelectId, (ts) => {
                this.selectedMonitors.forEach(monitor => {
                    ts.addItem(monitor, true);
                });
            });
        },

        validate() {
            this.errors = {};
            this.validateTitle();
            this.validateSlug();
        },

        validateTitle() {
            if (!this.title) {
                this.errors.title = errorMessages.titleRequired;
            } else {
                this.errors.title = null;
            }
        },

        validateSlug() {
            if (!this.slug) {
                this.errors.slug = errorMessages.slugRequired;
            } else if (!isValidSlug(this.slug)) {
                this.errors.slug = errorMessages.slugInvalid;
            } else {
                this.errors.slug = null;
            }
        },

        submitForm() {
            this.validate();
            if (hasNonNullValue(this.errors)) {
                return;
            }

            this.upsertStatusPage();
        },

        async upsertStatusPage() {
            try {
                this.isRequestLoading = true;
                const body = {
                    title: this.title,
                    slug: this.slug,
                    customLogoUrl: this.customLogoUrl,
                    customFaviconUrl: this.customFaviconUrl,
                    monitors: this.selectedMonitors,
                    public: this.public
                };

                const url = this.isUpdate ? '/api/v2/status-pages/' + statusPage.id : '/api/v2/status-pages';
                const method = this.isUpdate ? 'PATCH' : 'POST';

                const response = await fetch(url, {
                    method: method,
                    headers: jsonContentHeaders,
                    body: JSON.stringify(body)
                });

                if (response.ok) {
                    this.isRequestLoading = false;
                    const responseData = await response.json();

                    if (this.isUpdate) {
                        window.location.reload();
                    } else {
                        window.location.href = '/status-pages/' + responseData.id;
                    }
                } else {
                    if (response.status === 409) {
                        this.isRequestLoading = false;
                        this.errors.slug = this.errorMessages.slugAlreadyExists;
                    } else {
                        this.isRequestLoading = false;
                        console.error('Error creating/updating status page:', response.statusText);
                        alert('An error occurred while creating/updating the status page, refer to the console for more details');
                    }
                }
            } catch (error) {
                this.isRequestLoading = false;
                console.error('Error creating status page:', error);
                alert('An error occurred while creating/updating the status page. Please try again.');
            }
        }
    }
};

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
                    showToast(this.integrationId, data.message, 'bg-success', true);
                } else {
                    this.testRequestError = data.message || 'Unknown error';
                    showToast(this.integrationId, this.testRequestError, 'bg-danger', false);
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

const renderMonitorOption = (data, escape) => {
    const parts = splitWithLimit(data.value, ':', 2);
    const type = parts[0];
    const name = parts[1];
    const badgeColor = type === 'http' ? 'bg-blue-lt text-blue-lt-fg' : type === 'push' ? 'bg-red-lt text-red-lt-fg' : type === 'icmp' ? 'bg-orange-lt text-orange-lt-fg' : '';
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
const isoDurationRegex = /^P(?:\d+W)?(?:\d+D)?(?:T(?:\d+H)?(?:\d+M)?(?:\d+(?:\.\d+)?S)?)?$/;

const isValidIsoDuration = (value) => {
    if (!value || !isoDurationRegex.test(value)) return false;
    const numbers = value.match(/\d+(?:\.\d+)?/g);
    return !!numbers && numbers.some(n => parseFloat(n) > 0);
};

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

const maintenanceWindowListItem = (maintenanceWindowId, isMaintenanceWindowEnabled) => ({
    maintenanceWindowId,
    isMaintenanceWindowEnabled,
    isRequestLoading: false,
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
) => {
    const originalWindow = maintenanceWindow || null;
    return {
        errorMessages: errorMessages || {},
        isRequestLoading: false,
        isUpdate: !!maintenanceWindow,
        selectableMonitors: selectableMonitors || [],
        // The integrations accordion expects this; maintenance windows never auto-apply global integrations
        globalIntegrationCount: 0,

        init() {
            this.resetState();
        },

        resetState() {
            this.name = originalWindow?.name || '';
            this.description = originalWindow?.description || null;
            this.type = resolveMaintenanceWindowType(originalWindow);
            this.cron = originalWindow?.cron || '';
            this.start = toDateTimeLocalValue(originalWindow?.start);
            this.duration = originalWindow?.duration || '';
            this.enabled = (originalWindow?.enabled != null ? originalWindow.enabled : true);
            this.global = (originalWindow?.global != null ? originalWindow.global : false);
            this.showOnStatusPages =
                (originalWindow?.showOnStatusPages != null ? originalWindow.showOnStatusPages : false);
            this.selectedMonitors = originalWindow?.monitors || [];
            this.integrations = originalWindow?.integrations || [];
            this.errors = {};

            resetTomSelectState(monitorSelectId, (ts) => {
                this.selectedMonitors.forEach(monitor => {
                    ts.addItem(monitor, true);
                });
            });
        },

        validate() {
            this.errors = {};
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
            if (this.type !== MAINTENANCE_WINDOW_TYPES.CRON || this.cron) {
                this.errors.cron = null;
            } else {
                this.errors.cron = this.errorMessages.cronRequired;
            }
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
            if (this.type === MAINTENANCE_WINDOW_TYPES.SINGLE && !this.start) {
                this.errors.start = this.errorMessages.startRequired;
            } else {
                this.errors.start = null;
            }
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
            this.errors = {};
            this.validateName();
            this.validateStart();
            this.validateDuration();
            // The cron format check hits the server, so await it before deciding whether the form is valid
            await this.validateCron();
            if (hasNonNullValue(this.errors)) {
                return;
            }
            this.upsertMaintenanceWindow();
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
                integrations: this.integrations
            };
        },

        async upsertMaintenanceWindow() {
            try {
                this.isRequestLoading = true;
                const body = this.buildRequestBody();
                const url = this.isUpdate
                    ? '/api/v2/maintenance-windows/' + maintenanceWindow.id
                    : '/api/v2/maintenance-windows';
                const method = this.isUpdate ? 'PATCH' : 'POST';

                const response = await fetch(url, {
                    method: method,
                    headers: jsonContentHeaders,
                    body: JSON.stringify(body)
                });

                if (response.ok) {
                    this.isRequestLoading = false;
                    const responseData = await response.json();

                    if (this.isUpdate) {
                        window.location.reload();
                    } else {
                        window.location.href = '/maintenance-windows/' + responseData.id;
                    }
                } else if (response.status === 409) {
                    this.isRequestLoading = false;
                    this.errors.name = this.errorMessages.nameAlreadyExists;
                } else {
                    this.isRequestLoading = false;
                    console.error('Error creating/updating maintenance window:', response.statusText);
                    alert('An error occurred while creating/updating the maintenance window, refer to the console for more details');
                }
            } catch (error) {
                this.isRequestLoading = false;
                console.error('Error creating/updating maintenance window:', error);
                alert('An error occurred while creating/updating the maintenance window. Please try again.');
            }
        }
    }
};

// Exposes helpers and Alpine x-data factories for the Node-based unit tests (see ui/src/jsTest and the :ui:jsTest task)
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        // Pure, DOM-free helpers
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
        createRandomSecret,
        // Alpine x-data component factories
        upsertHttpMonitorForm,
        upsertPushMonitorForm,
        upsertIcmpMonitorForm,
        upsertMaintenanceWindowForm,
        httpMetricsBlock,
        icmpMetricsBlock,
    };
}
