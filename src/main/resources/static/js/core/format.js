/**
 * VietSpecialty - Client-side Formatters
 */
const format = {
    vnd(amount) {
        if (amount == null || isNaN(amount)) return '0₫';
        return new Intl.NumberFormat('vi-VN').format(amount) + '₫';
    },

    date(isoString) {
        if (!isoString) return '';
        const d = new Date(isoString);
        return new Intl.DateTimeFormat('vi-VN', {
            hour: '2-digit',
            minute: '2-digit',
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
        }).format(d);
    }
};

window.format = format;

