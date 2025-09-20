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
            || (currentPath.startsWith('/http-monitors') && linkPath === '#navbar-monitors')
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
    let tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        // If the tooltip is already initialized, dispose it
        const tooltipInstance = tabler.Tooltip.getInstance(tooltipTriggerEl);
        if (tooltipInstance) {
            tooltipInstance.dispose();
        }
        let options = {
            delay: {show: 50, hide: 50},
            html: tooltipTriggerEl.getAttribute("data-bs-html") === "true" ?? false,
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

// --------- Alpine.js x-data ---------
const httpMonitorListItem = (monitorId, isMonitorEnabled, assignedToStatusPage) => {
    return {
        monitorId: monitorId,
        isMonitorEnabled: isMonitorEnabled,
        assignedToStatusPage: assignedToStatusPage,
        isRequestLoading: false,
        toggleMonitor() {
            patchHttpMonitorRequest(
                this.monitorId,
                {enabled: !this.isMonitorEnabled},
                () => this.isRequestLoading = true,
                () => refreshHttpMonitorList(),
                () => this.isRequestLoading = false
            );
        },
        deleteMonitor() {
            deleteHttpMonitorRequest(
                this.monitorId,
                () => this.isRequestLoading = true,
                () => refreshHttpMonitorList(),
            );
        }
    }
};

const statusPageListItem = (statusPageId, isStatusPagePublic) => {
    return {
        statusPageId: statusPageId,
        isStatusPagePublic: isStatusPagePublic,
        isRequestLoading: false,
        toggleStatusPageVisibility() {
            patchStatusPageRequest(
                this.statusPageId,
                {public: !this.isStatusPagePublic},
                () => this.isRequestLoading = true,
                () => refreshStatusPageList(),
                () => this.isRequestLoading = false
            );
        },
        deleteStatusPage() {
            deleteStatusPageRequest(
                this.statusPageId,
                () => this.isRequestLoading = true,
                () => refreshStatusPageList(),
            );
        }
    }
};

const httpMonitorDetails = (monitorId, isMonitorEnabled) => {
    return {
        monitorId,
        isMonitorEnabled,
        isRequestLoading: false,

        toggleMonitor() {
            patchHttpMonitorRequest(
                this.monitorId,
                {enabled: !this.isMonitorEnabled},
                () => this.isRequestLoading = true,
                () => {
                    this.isRequestLoading = false;
                    this.isMonitorEnabled = !this.isMonitorEnabled;
                    this.$dispatch(this.isMonitorEnabled ? 'monitor-enabled' : 'monitor-disabled');
                    console.debug('Monitor enabled status changed:', this.isMonitorEnabled);
                    refreshHttpMonitorDetailStatus();
                },
                () => this.isRequestLoading = false
            );
        },

        deleteMonitor() {
            deleteHttpMonitorRequest(
                this.monitorId,
                () => this.isRequestLoading = true,
                () => window.location.href = '/http-monitors',
                () => this.isRequestLoading = false
            );
            this.isRequestLoading = true;
        }
    }
};

const statusPageDetails = (statusPageId, isStatusPagePublic) => {
    return {
        statusPageId,
        isStatusPagePublic,
        isRequestLoading: false,

        toggleStatusPageVisibility() {
            patchStatusPageRequest(
                this.statusPageId,
                {public: !this.isStatusPagePublic},
                () => this.isRequestLoading = true,
                () => window.location.reload(),
                () => this.isRequestLoading = false
            );
        },

        deleteStatusPage() {
            deleteStatusPageRequest(
                this.statusPageId,
                () => this.isRequestLoading = true,
                () => window.location.href = '/status-pages',
                () => this.isRequestLoading = false
            );
        }
    }
};

// Refreshes the monitor detail page's dynamic status blocks by triggering an HTMX event (OOB swap)
const refreshHttpMonitorDetailStatus = () => {
    sendHtmxEvent('#monitor-detail-heading', 'refresh-monitor-detail-status');
};

// Refreshes the monitor list by triggering an HTMX event
const refreshHttpMonitorList = () => {
    sendHtmxEvent('#monitors-list', 'refresh-monitor-list');
};

// Refreshes the status page list by triggering an HTMX event
const refreshStatusPageList = () => {
    sendHtmxEvent('#status-page-list', 'refresh-status-page-list');
};

// Refreshes the dashboard by triggering an HTMX event
const refreshDashboard = () => {
    sendHtmxEvent('#monitoring-dashboard', 'refresh-dashboard');
};

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
                console.debug('Auto-refresh setting changed:', value);
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
            const options = {
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
                    text: this.noDataLabel,
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
                        formatter: function (val) {
                            return val + " ms";
                        },
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
            };
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
            console.debug('Updating chart with new data:', newData);
            this.chart.updateOptions({
                labels: newData.labels,
                series: newData.series,
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
            console.debug('Monitor form initialized:', this.isUpdate ? 'Update mode' : 'Create mode');
        },

        resetState() {
            this.name = originalMonitor?.name || '';
            this.url = originalMonitor?.url || '';
            this.sslExpiryThreshold = originalMonitor?.sslExpiryThreshold || 30;
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
            const headerPattern = /^[a-zA-Z][a-zA-Z0-9-]*$/;
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
            this.validateUptimeCheckInterval();
            this.validateResponseTimeThreshold();
        },

        validateName() {
            if (!this.name) {
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
                console.debug('Form validation failed:', this.errors);
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
                    sslCheckEnabled: this.sslCheckEnabled,
                    latencyHistoryEnabled: this.latencyHistoryEnabled,
                    sslExpiryThreshold: this.sslExpiryThreshold,
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

                console.debug('Submitting monitor form with data:', body);

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
                    console.debug('Monitor was created/updated successfully, redirecting to monitor', responseData);

                    if (this.isUpdate) {
                        window.location.reload();
                    } else {
                        window.location.href = '/http-monitors/' + responseData.id;
                    }
                } else {
                    if (response.status === 409) {
                        this.isRequestLoading = false;
                        console.debug('Monitor with this name already exists');
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
            console.debug('Status page form initialized:', this.isUpdate ? 'Update mode' : 'Create mode');
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
            console.log(this.customFaviconUrl, this.customLogoUrl, this.imagePreviewState);
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
                console.debug('Form validation failed:', this.errors);
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

                console.debug('Submitting status page form with data:', body);

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
                    console.debug('Status page was created/updated successfully, redirecting to the details', responseData);

                    if (this.isUpdate) {
                        window.location.reload();
                    } else {
                        window.location.href = '/status-pages/' + responseData.id;
                    }
                } else {
                    if (response.status === 409) {
                        this.isRequestLoading = false;
                        console.debug('Status page with this name already exists');
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
    const badgeColor = type === 'http' ? 'bg-blue-lt text-blue-lt-fg' : '';
    return `<div><span class="badge me-2 ${badgeColor}">${type.toUpperCase()}</span>${name}</div>`;
};

const renderStatusCodeOption = (data, escape) => {
    const statusClass = statusCodeToBadgeClass(data.value);
    return `<div><span class="status-dot ${statusClass} me-2"></span>${escape(data.text)}</div>`;
};

const renderStatusCodeItem = (data, escape) => {
    const statusClass = statusCodeToBadgeClass(data.value);
    return `<div><span class="status-dot ${statusClass} me-2"></span>${escape(data.value)}</div>`;
};

// API calls
const jsonContentHeaders = {'Content-Type': 'application/json'};

const deleteHttpMonitorRequest = (
    monitorId,
    beforeRequest = () => {
    },
    onSuccess = () => {
    },
    onError = () => {
    }
) => {
    beforeRequest();
    fetch('/api/v2/http-monitors/' + monitorId, {
        method: 'DELETE',
        headers: jsonContentHeaders
    }).then(response => {
        if (response.ok) {
            onSuccess();
        } else {
            onError();
            console.error('Error deleting monitor:', response.statusText);
            alert('An error occurred while deleting the monitor.');
        }
    });
};

const patchHttpMonitorRequest = (
    monitorId,
    body,
    beforeRequest = () => {
    },
    onSuccess = () => {
    },
    onError = () => {
    }
) => {
    this.isRequestLoading = true;
    fetch('/api/v2/http-monitors/' + monitorId, {
        method: 'PATCH',
        headers: jsonContentHeaders,
        body: JSON.stringify(body)
    }).then(response => {
        if (response.ok) {
            onSuccess();
        } else {
            onError();
            console.error('Error toggling monitor:', response.statusText);
            alert('An error occurred while toggling the monitor.');
        }
    }).catch(error => {
        onError();
        console.error('Error toggling monitor:', error);
        alert('An error occurred while toggling the monitor.');
    });
};

const deleteStatusPageRequest = (
    statusPageId,
    beforeRequest = () => {},
    onSuccess = () => {},
    onError = () => {}
) => {
    beforeRequest();
    fetch('/api/v2/status-pages/' + statusPageId, {
        method: 'DELETE',
        headers: jsonContentHeaders
    }).then(response => {
        if (response.ok) {
            onSuccess();
        } else {
            onError();
            console.error('Error deleting status page:', response.statusText);
            alert('An error occurred while deleting the status page.');
        }
    });
};

const patchStatusPageRequest = (
    statusPageId,
    body,
    beforeRequest = () => {},
    onSuccess = () => {},
    onError = () => {}
) => {
    beforeRequest();
    fetch('/api/v2/status-pages/' + statusPageId, {
        method: 'PATCH',
        headers: jsonContentHeaders,
        body: JSON.stringify(body)
    }).then(response => {
        if (response.ok) {
            onSuccess();
        } else {
            onError();
            console.error('Error toggling status page:', response.statusText);
            alert('An error occurred while toggling the status page.');
        }
    }).catch(error => {
        onError();
        console.error('Error toggling status page:', error);
        alert('An error occurred while toggling the status page.');
    });
};
