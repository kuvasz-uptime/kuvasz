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
        if (currentPath == linkPath || (currentPath.startsWith(linkPath) && linkPath !== "/")) {
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
            delay: { show: 50, hide: 50 },
            html: tooltipTriggerEl.getAttribute("data-bs-html") === "true" ?? false,
            placement: tooltipTriggerEl.getAttribute('data-bs-placement') ?? 'auto'
        };
        return new tabler.Tooltip(tooltipTriggerEl, options);
    });
};

// --------- Alpine.js x-data ---------
const monitorList = (monitorId, isMonitorEnabled) => {
    return {
        monitorId: monitorId,
        isMonitorEnabled: isMonitorEnabled,
        isRequestLoading: false,
        toggleMonitor() {
            this.isRequestLoading = true;
            fetch('/api/v1/monitors/' + this.monitorId, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ enabled: !this.isMonitorEnabled })
            }).then(response => {
                if (response.ok) {
                    refreshMonitorList();
                } else {
                    console.error('Error toggling monitor:', response.statusText);
                    alert('An error occurred while toggling the monitor.');
                }
            }).catch(error => {
                this.isRequestLoading = false;
                console.error('Error toggling monitor:', error);
                alert('An error occurred while toggling the monitor.');
            });
        },
        deleteMonitor() {
            this.isRequestLoading = true;
            fetch('/api/v1/monitors/' + this.monitorId, {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' }
            }).then(response => {
                if (response.ok) {
                    refreshMonitorList();
                } else {
                    console.error('Error deleting monitor:', response.statusText);
                    alert('An error occurred while deleting the monitor.');
                }
            });
        }
    }
};

const monitorDetails = (monitorId, isMonitorEnabled) => {
    return {
        monitorId,
        isMonitorEnabled,
        isRequestLoading: false,

        toggleMonitor() {
            this.isRequestLoading = true;
            fetch('/api/v1/monitors/' + this.monitorId, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ enabled: !this.isMonitorEnabled })
            }).then(response => {
                if (response.ok) {
                    this.isRequestLoading = false;
                    this.isMonitorEnabled = !this.isMonitorEnabled;
                    this.$dispatch(this.isMonitorEnabled ? 'monitor-enabled' : 'monitor-disabled');
                    console.debug('Monitor enabled status changed:', this.isMonitorEnabled);
                    refreshMonitorDetailStatus();
                } else {
                    console.error('Error toggling monitor:', response.statusText);
                    alert('An error occurred while toggling the monitor, refer to the console for more details');
                    this.isRequestLoading = false;
                }
            })
                .catch(error => {
                    this.isRequestLoading = false;
                    console.error('Error toggling monitor:', error);
                    alert('An error occurred while toggling the monitor. Please try again.');
                })
        },

        deleteMonitor() {
            this.isRequestLoading = true;
            fetch('/api/v1/monitors/' + this.monitorId, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json'
                }
            })
                .then(response => {
                    if (response.ok) {
                        window.location.href = '/monitors';
                    } else {
                        this.isRequestLoading = false;
                        console.error('Error deleting monitor:', response.statusText);
                        alert('An error occurred while deleting the monitor, refer to the console for more details');
                    }
                })
        }
    }
};

// Refreshes the monitor detail page's dynamic status blocks by triggering an HTMX event (OOB swap)
const refreshMonitorDetailStatus = () => {
    sendHtmxEvent('#monitor-detail-heading', 'refresh-monitor-detail-status');
};

// Refreshes the monitor list by triggering an HTMX event
const refreshMonitorList = () => {
    sendHtmxEvent('#monitors-list', 'refresh-monitor-list');
};

const latencyBlock = (monitorId, isMonitorEnabled, uptimeCheckInterval, noDataLabel) => {
    return {
        isMonitorEnabled,
        chart: null,
        previousData: null,
        endpointUrl: '/api/v1/monitors/' + monitorId + '/stats?period=1d',
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
}

const upsertMonitorForm = (monitor, errorMessages) => {
    const originalMonitor = monitor || null;
    return {
        errorMessages: errorMessages || {},
        isRequestLoading: false,
        isUpdate: !!monitor,

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
            this.errors = {};
        },

        validate() {
            this.errors = {};
            this.validateName();
            this.validateUrl();
            this.validateSslExpiryThreshold();
            this.validateUptimeCheckInterval();
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
                };
                if (!this.isUpdate) {
                    body.enabled = true; // Default enabled, can be paused later
                }

                console.debug('Submitting monitor form with data:', body);

                const url = this.isUpdate ? '/api/v1/monitors/' + monitor.id : '/api/v1/monitors';
                const method = this.isUpdate ? 'PATCH' : 'POST';

                const response = await fetch(url, {
                    method: method,
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(body)
                });

                if (response.ok) {
                    this.isRequestLoading = false;
                    const responseData = await response.json();
                    console.debug('Monitor was created/updated successfully, redirecting to monitor', responseData);

                    if (this.isUpdate) {
                        window.location.reload();
                    } else {
                        window.location.href = '/monitors/' + responseData.id;
                    }
                } else {
                    if (response.status === 409) {
                        this.isRequestLoading = false;
                        console.debug('Monitor with this name already exists');
                        this.errors.name = this.errorMessages.nameAlreadyExists;
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
