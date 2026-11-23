/**
 * VietSpecialty - Toast Notification Component
 * Hiển thị thông báo góc trên bên phải màn hình, thanh tiến trình tự biến mất sau 3s.
 */
class ToastManager {
    constructor() {
        this.container = null;
    }

    getContainer() {
        if (this.container && document.body && document.body.contains(this.container)) {
            return this.container;
        }
        this.container = document.getElementById('toast-container');
        if (!this.container && document.body) {
            this.container = document.createElement('div');
            this.container.id = 'toast-container';
            this.container.className = 'fixed top-5 right-5 z-50 flex flex-col space-y-3 pointer-events-none max-w-sm w-full px-4';
            document.body.appendChild(this.container);
        }
        return this.container;
    }

    show(type, message, title = '') {
        const container = this.getContainer();
        if (!container) return;
        const toastEl = document.createElement('div');
        toastEl.className = 'pointer-events-auto bg-surface rounded-2xl border border-line shadow-lift p-4 flex flex-col overflow-hidden transform transition-all duration-300 translate-x-full opacity-0';

        const colorMap = {
            success: { bar: 'bg-primary-600', icon: 'text-primary-600', defaultTitle: 'Thành công' },
            error: { bar: 'bg-danger', icon: 'text-danger', defaultTitle: 'Lỗi' },
            warning: { bar: 'bg-secondary-500', icon: 'text-secondary-500', defaultTitle: 'Lưu ý' },
            info: { bar: 'bg-blue-600', icon: 'text-blue-600', defaultTitle: 'Thông tin' }
        };

        const config = colorMap[type] || colorMap.info;
        const displayTitle = title || config.defaultTitle;

        toastEl.innerHTML = `
            <div class="flex items-start gap-3">
                <div class="flex-1">
                    <h4 class="text-sm font-semibold text-ink">${displayTitle}</h4>
                    <p class="text-xs text-ink-muted mt-0.5 leading-relaxed">${message}</p>
                </div>
                <button class="text-ink-muted hover:text-ink transition p-1" onclick="this.closest('.pointer-events-auto').remove()">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
                </button>
            </div>
            <div class="h-1 w-full bg-surface-muted mt-3 rounded-full overflow-hidden">
                <div class="h-full ${config.bar} transition-all duration-[3000ms] ease-linear w-full toast-progress"></div>
            </div>
        `;

        container.appendChild(toastEl);

        // Hiệu ứng trượt vào
        requestAnimationFrame(() => {
            toastEl.classList.remove('translate-x-full', 'opacity-0');
            const progress = toastEl.querySelector('.toast-progress');
            if (progress) {
                progress.style.width = '0%';
            }
        });

        // Tự biến mất sau 3 giây
        setTimeout(() => {
            toastEl.classList.add('translate-x-full', 'opacity-0');
            setTimeout(() => toastEl.remove(), 300);
        }, 3000);
    }

    success(message, title) { this.show('success', message, title); }
    error(message, title) { this.show('error', message, title); }
    warning(message, title) { this.show('warning', message, title); }
    info(message, title) { this.show('info', message, title); }
}

window.toast = new ToastManager();
window.Toast = window.toast;

