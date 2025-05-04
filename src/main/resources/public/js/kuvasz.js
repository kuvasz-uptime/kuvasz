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
