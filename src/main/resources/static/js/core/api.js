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

