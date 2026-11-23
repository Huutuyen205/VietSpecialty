/**
 * VietSpecialty - Core API Client Wrapper
 * Tự động gắn CSRF token, xử lý phân quyền và thông báo lỗi tập trung.
 */
const api = {
    getCsrfToken() {
        const meta = document.querySelector('meta[name="_csrf"]');
        return meta ? meta.getAttribute('content') : '';
    },

    getCsrfHeader() {
        const meta = document.querySelector('meta[name="_csrf_header"]');
        return meta ? meta.getAttribute('content') : 'X-CSRF-TOKEN';
    },

    async request(url, options = {}) {
        const headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            ...(options.headers || {})
        };

        // Gắn CSRF Token cho các phương thức ghi (POST, PUT, PATCH, DELETE)
        const method = (options.method || 'GET').toUpperCase();
        if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
            const token = this.getCsrfToken();
            const headerName = this.getCsrfHeader();
            if (token && headerName) {
                headers[headerName] = token;
            }
        }

        try {
            const response = await fetch(url, { ...options, headers });

            // 401: Chưa đăng nhập -> Chuyển hướng sang trang đăng nhập
            if (response.status === 401) {
                window.location.href = '/login?redirect=' + encodeURIComponent(window.location.pathname + window.location.search);
                return null;
            }

            // 403: Không có quyền truy cập
            if (response.status === 403) {
                if (window.toast) {
                    window.toast.error('Bạn không có quyền thực hiện thao tác này');
                }
                throw new Error('Access Denied');
            }

            const data = await response.json();

            if (!response.ok || !data.success) {
                const message = data.message || 'Yêu cầu không thể hoàn thành';
                throw { status: response.status, message, errors: data.errors, raw: data };
            }

            return data;
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    },

    get(url, options = {}) {
        return this.request(url, { ...options, method: 'GET' });
    },

    post(url, body = {}, options = {}) {
        return this.request(url, {
            ...options,
            method: 'POST',
            body: JSON.stringify(body)
        });
    },

    put(url, body = {}, options = {}) {
        return this.request(url, {
            ...options,
            method: 'PUT',
            body: JSON.stringify(body)
        });
    },

    patch(url, body = {}, options = {}) {
        return this.request(url, {
            ...options,
            method: 'PATCH',
            body: JSON.stringify(body)
        });
    },

    delete(url, options = {}) {
        return this.request(url, { ...options, method: 'DELETE' });
    }
};

window.api = api;

/**
 * Thao tác thêm nhanh vào giỏ hàng dùng chung toàn sàn
 */
window.quickAddToCart = async function(productId, quantity = 1) {
    if (!productId) return;
    try {
        const response = await api.post('/api/cart/items', {
            productId: Number(productId),
            quantity: quantity
        });
        if (response && response.success) {
            if (window.toast) {
                window.toast.success(response.message || 'Đã thêm đặc sản vào giỏ hàng!');
            }
            window.dispatchEvent(new CustomEvent('cart-updated'));
        }
    } catch (err) {
        if (err && err.status === 401) {
            return;
        }
        if (window.toast) {
            window.toast.error(err.message || 'Không thể thêm sản phẩm vào giỏ hàng');
        }
    }
};

window.refreshCartBadge = function() {
    window.dispatchEvent(new CustomEvent('cart-updated'));
};

/**
 * Quản lý danh sách Yêu thích (Wishlist) đồng bộ LocalStorage & Server Session
 */
window.toggleWishlistItem = async function(productId, btnEl) {
    if (!productId) return;
    const id = Number(productId);
    let ids = [];
    try {
        ids = JSON.parse(localStorage.getItem('vs_wishlist_ids') || '[]');
        if (!Array.isArray(ids)) ids = [];
    } catch (e) {
        ids = [];
    }

    const idx = ids.indexOf(id);
    let added = false;
    if (idx >= 0) {
        ids.splice(idx, 1);
        added = false;
    } else {
        ids.push(id);
        added = true;
    }
    localStorage.setItem('vs_wishlist_ids', JSON.stringify(ids));
    window.syncWishlistButtons();
    window.dispatchEvent(new CustomEvent('wishlist-updated'));

    try {
        const token = api.getCsrfToken();
        const headerName = api.getCsrfHeader();
        const headers = { 'Accept': 'application/json' };
        if (token && headerName) headers[headerName] = token;
        await fetch('/wishlist/toggle/' + id, { method: 'POST', headers });
    } catch (e) {}

    if (window.toast) {
        window.toast.success(added ? 'Đã lưu đặc sản vào danh sách yêu thích' : 'Đã bỏ khỏi danh sách yêu thích');
    }
};

window.syncWishlistButtons = function() {
    let ids = [];
    try {
        ids = JSON.parse(localStorage.getItem('vs_wishlist_ids') || '[]');
    } catch (e) {
        ids = [];
    }
    document.querySelectorAll('[data-wishlist-id]').forEach(btn => {
        const pid = Number(btn.getAttribute('data-wishlist-id'));
        const isFav = ids.includes(pid);
        if (isFav) {
            btn.classList.add('text-danger', 'border-danger/40');
            btn.classList.remove('text-ink-muted');
            const svg = btn.querySelector('svg');
            if (svg) svg.classList.add('fill-current');
        } else {
            btn.classList.remove('text-danger', 'border-danger/40');
            btn.classList.add('text-ink-muted');
            const svg = btn.querySelector('svg');
            if (svg) svg.classList.remove('fill-current');
        }
    });
};

document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => window.syncWishlistButtons(), 100);
});
