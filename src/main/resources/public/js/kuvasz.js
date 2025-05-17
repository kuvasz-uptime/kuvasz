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
        if (linkPath === currentPath) {
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
		delay: {show: 50, hide: 50},
		html: tooltipTriggerEl.getAttribute("data-bs-html") === "true" ?? false,
		placement: tooltipTriggerEl.getAttribute('data-bs-placement') ?? 'auto'
	};
	return new tabler.Tooltip(tooltipTriggerEl, options);
});
}
