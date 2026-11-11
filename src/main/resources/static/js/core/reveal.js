/**
 * VietSpecialty - Scroll Reveal Animation (IntersectionObserver)
 * Chỉ animate transform & opacity, tự tắt khi prefers-reduced-motion
 */
document.addEventListener('DOMContentLoaded', () => {
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) return;

    const revealElements = document.querySelectorAll('[data-reveal]');
    if (!revealElements.length) return;

    const observer = new IntersectionObserver((entries, obs) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const el = entry.target;
                const delay = el.getAttribute('data-reveal-delay') || '0';
                setTimeout(() => {
                    el.classList.add('animate-fade-in');
                    el.style.opacity = '1';
                }, parseInt(delay, 10));
                obs.unobserve(el);
            }
        });
    }, { threshold: 0.1, rootMargin: '0px 0px -40px 0px' });

    revealElements.forEach(el => {
        el.style.opacity = '0';
        observer.observe(el);
    });
});

