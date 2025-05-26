// Dark/light mode toggle
function setTheme(theme) {
    document.documentElement.setAttribute('data-bs-theme', theme);
    localStorage.setItem('kuvasz-theme', theme);
}

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
function sendHtmxEvent(target, eventName) {
    htmx.trigger(target, eventName);
}

// Reinitialize Bootstrap tooltips (useful after HTMX content swap)
function reInitTooltips() {
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
}

// --------- Alpine.js x-data ---------
function monitorList(monitorId, isMonitorEnabled) {
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
}

function monitorDetails(monitorId, isMonitorEnabled) {
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
}

// Refreshes the monitor detail page's dynamic status blocks by triggering an HTMX event (OOB swap)
function refreshMonitorDetailStatus() {
    sendHtmxEvent('#monitor-detail-heading','refresh-monitor-detail-status');
}

// Refreshes the monitor list by triggering an HTMX event
function refreshMonitorList() {
    sendHtmxEvent('#monitors-list','refresh-monitor-list');
}
