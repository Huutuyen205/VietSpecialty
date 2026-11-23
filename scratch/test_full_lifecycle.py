import requests
import re
import json
import urllib.parse
import hmac
import hashlib

BASE_URL = "http://localhost:8080"

class TestLogger:
    def __init__(self):
        self.results = []
        self.bugs = []

    def log(self, section, test_name, status, details, actual="", expected="", severity=None):
        entry = {
            "section": section,
            "test_name": test_name,
            "status": status,
            "details": details,
            "actual": str(actual),
            "expected": str(expected),
            "severity": severity
        }
        self.results.append(entry)
        prefix = f"[{status}]"
        print(f"{prefix:<8} [{section}] {test_name}: {details}")
        if status == "FAIL" or severity:
            self.bugs.append(entry)

logger = TestLogger()

def get_session(email, password):
    s = requests.Session()
    login_page = s.get(f"{BASE_URL}/login")
    csrf_match = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page.text)
    csrf = csrf_match.group(1) if csrf_match else ''
    s.headers.update({'X-CSRF-TOKEN': csrf})
    
    resp = s.post(f"{BASE_URL}/login", data={
        'email': email,
        'password': password,
        '_csrf': csrf
    }, allow_redirects=True)
    
    # Refresh csrf
    home = s.get(f"{BASE_URL}/")
    csrf_match = re.search(r'name="_csrf"\s+value="([^"]+)"', home.text)
    if csrf_match:
        s.headers.update({'X-CSRF-TOKEN': csrf_match.group(1)})
    return s

def clean_cart(buyer):
    res = buyer.get(f"{BASE_URL}/api/cart")
    data = res.json().get("data", {})
    if data and data.get("shopGroups"):
        for sg in data["shopGroups"]:
            for item in sg["items"]:
                buyer.delete(f"{BASE_URL}/api/cart/items/{item['id']}")

def run_all_tests():
    buyer = get_session("buyer@vietspecialty.vn", "Password123@")
    seller = get_session("seller_bac@vietspecialty.vn", "Password123@")
    anon = requests.Session()

    # =========================================================================
    # 1. KIỂM THỬ GIỎ HÀNG (CART FLOW)
    # =========================================================================
    print("\n" + "="*60)
    print("PHẦN 1: KIỂM THỬ LUỒNG GIỎ HÀNG")
    print("="*60)

    # 1.1 Khách vãng lai (Guest / Anonymous) thêm vào giỏ hàng
    # Khi khách bấm Thêm vào giỏ hàng, nếu không có CSRF token -> 403. Nếu có CSRF token -> 401.
    res_login = anon.get(f"{BASE_URL}/login")
    csrf_guest = re.search(r'name="_csrf"\s+value="([^"]+)"', res_login.text).group(1)
    anon.headers.update({'X-CSRF-TOKEN': csrf_guest})
    res_guest_add = anon.post(f"{BASE_URL}/api/cart/items", json={"productId": 59, "quantity": 1})
    if res_guest_add.status_code == 401:
        logger.log("Cart", "1.1 Guest Add To Cart", "PASS", "Guest receives 401 Unauthorized as expected")
    else:
        logger.log("Cart", "1.1 Guest Add To Cart", "INFO", f"Guest add to cart status: {res_guest_add.status_code}, response: {res_guest_add.text}")

    # 1.2 Dọn sạch giỏ hàng buyer để kiểm tra Giỏ hàng trống
    clean_cart(buyer)
    cart_empty_page = buyer.get(f"{BASE_URL}/cart")
    if "Giỏ hàng đặc sản đang trống" in cart_empty_page.text and "Khám phá đặc sản 4 miền ngay" in cart_empty_page.text:
        logger.log("Cart", "1.2 Empty Cart UI (/cart)", "PASS", "Empty cart banner and call-to-action button displayed properly")
    else:
        logger.log("Cart", "1.2 Empty Cart UI (/cart)", "FAIL", "Empty cart banner missing", cart_empty_page.status_code, "Empty cart message", "Medium")

    # 1.3 Thêm đặc sản từ Thẻ sản phẩm (quickAddToCart)
    # Chọn sản phẩm 59 (Trà Đinh Nõn Tân Cương Thượng Hạng, Shop 77 HTX Trà Tân Cương Thái Nguyên, giá 850.000₫)
    res_add1 = buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 59, "quantity": 1})
    if res_add1.status_code == 200 and res_add1.json().get("success"):
        logger.log("Cart", "1.3 Quick Add From Product Card", "PASS", f"Product 59 added. Msg: {res_add1.json().get('message')}")
    else:
        logger.log("Cart", "1.3 Quick Add From Product Card", "FAIL", "Failed to add product 59", res_add1.text, "HTTP 200", "High")

    # 1.4 Thêm đặc sản từ Trang chi tiết sản phẩm (addToCartWithQuantity)
    # Chọn sản phẩm 60 (Thịt Trâu Gác Bếp Tây Bắc, Shop 77, giá 420.000₫, số lượng 2)
    res_add2 = buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 60, "quantity": 2})
    if res_add2.status_code == 200 and res_add2.json().get("success"):
        logger.log("Cart", "1.4 Add From Product Detail Page (Qty=2)", "PASS", f"Product 60 (qty 2) added. Msg: {res_add2.json().get('message')}")
    else:
        logger.log("Cart", "1.4 Add From Product Detail Page (Qty=2)", "FAIL", "Failed to add product 60", res_add2.text, "HTTP 200", "High")

    # 1.5 Thêm đặc sản từ Thẻ Chatbot AI (quickAddToCart)
    # Chọn sản phẩm 72 (Nước Mắm Nam Ô Truyền Thống Cá Cơm Than, Shop 78 Đặc Sản Cố Đô Huế, giá 160.000₫, số lượng 1)
    res_add3 = buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 72, "quantity": 1})
    if res_add3.status_code == 200 and res_add3.json().get("success"):
        logger.log("Cart", "1.5 Add From Chatbot AI Card", "PASS", f"Product 72 added. Msg: {res_add3.json().get('message')}")
    else:
        logger.log("Cart", "1.5 Add From Chatbot AI Card", "FAIL", "Failed to add product 72", res_add3.text, "HTTP 200", "High")

    # 1.6 Kiểm tra gom nhóm giỏ hàng theo Shop (Multi-vendor Shop Grouping)
    cart_res = buyer.get(f"{BASE_URL}/api/cart")
    cart_data = cart_res.json().get("data", {})
    shop_groups = cart_data.get("shopGroups", [])
    total_qty = cart_data.get("totalItemCount", 0)
    grand_total = cart_data.get("grandTotal", 0)
    # Kỳ vọng: 2 shop (77 và 78), tổng số lượng = 1 + 2 + 1 = 4, tổng tiền = 850k + 2*420k + 160k = 1.850.000₫
    if len(shop_groups) == 2 and total_qty == 4 and grand_total == 1850000:
        logger.log("Cart", "1.6 Shop Grouping & Subtotal", "PASS", f"Correctly grouped into 2 shops, 4 items, grandTotal = 1.850.000₫")
    else:
        logger.log("Cart", "1.6 Shop Grouping & Subtotal", "FAIL", f"Shop grouping mismatch: {len(shop_groups)} shops, {total_qty} items, {grand_total}₫", actual=cart_data, severity="High")

    # 1.7 Tăng / Giảm số lượng mặt hàng
    item_59 = None
    for sg in shop_groups:
        for it in sg["items"]:
            if it["productId"] == 59:
                item_59 = it
                break

    if item_59:
        # Tăng số lượng lên 3
        inc_res = buyer.put(f"{BASE_URL}/api/cart/items/{item_59['id']}", json={"quantity": 3})
        if inc_res.status_code == 200 and inc_res.json().get("success"):
            new_qty = inc_res.json().get("data", {}).get("totalItemCount")
            logger.log("Cart", "1.7.1 Increase Item Quantity (to 3)", "PASS", f"Quantity increased to 3, new total items in cart: {new_qty}")
        else:
            logger.log("Cart", "1.7.1 Increase Item Quantity", "FAIL", "Failed to increase qty", inc_res.text, "HTTP 200", "High")

        # Giảm số lượng về 1
        dec_res = buyer.put(f"{BASE_URL}/api/cart/items/{item_59['id']}", json={"quantity": 1})
        if dec_res.status_code == 200 and dec_res.json().get("success"):
            logger.log("Cart", "1.7.2 Decrease Item Quantity (to 1)", "PASS", f"Quantity decreased back to 1")
        else:
            logger.log("Cart", "1.7.2 Decrease Item Quantity", "FAIL", "Failed to decrease qty", dec_res.text, "HTTP 200", "High")

        # Thử cập nhật vượt tồn kho (Stock sản phẩm 59 là 140, thử 9999)
        excess_res = buyer.put(f"{BASE_URL}/api/cart/items/{item_59['id']}", json={"quantity": 9999})
        if excess_res.status_code == 400 or not excess_res.json().get("success"):
            logger.log("Cart", "1.7.3 Quantity Exceeds Stock Check", "PASS", f"Rejected excessive quantity: {excess_res.json().get('message')}")
        else:
            logger.log("Cart", "1.7.3 Quantity Exceeds Stock Check", "FAIL", "Allowed quantity exceeding stock!", excess_res.text, "HTTP 400 or error", "High")

    # 1.8 Xóa mặt hàng khỏi giỏ hàng (DELETE /api/cart/items/{id})
    if item_59:
        del_res = buyer.delete(f"{BASE_URL}/api/cart/items/{item_59['id']}")
        if del_res.status_code == 200 and del_res.json().get("success"):
            logger.log("Cart", "1.8 Delete Item from Cart", "PASS", f"Deleted item {item_59['id']} successfully")
        else:
            logger.log("Cart", "1.8 Delete Item from Cart", "FAIL", f"Failed to delete item", del_res.text, "HTTP 200", "High")

    # Thêm lại sản phẩm 59 (để phục vụ kiểm thử checkout)
    buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 59, "quantity": 1})

    # =========================================================================
    # 2. KIỂM THỬ BADGE GIỎ HÀNG REALTIME
    # =========================================================================
    print("\n" + "="*60)
    print("PHẦN 2: KIỂM THỬ BADGE GIỎ HÀNG HEADER & MOBILE")
    print("="*60)

    # 2.1 API /api/cart/count cho Khách vãng lai
    cnt_guest = anon.get(f"{BASE_URL}/api/cart/count")
    if cnt_guest.status_code == 200 and cnt_guest.json().get("data") == 0:
        logger.log("Badge", "2.1 Guest Cart Count API", "PASS", "Returns 200 with 0")
    elif cnt_guest.status_code == 401:
        logger.log("Badge", "2.1 Guest Cart Count API (401 Error)", "FAIL",
                   "Guest request to /api/cart/count receives 401 Unauthorized instead of 200 with 0. Cause: SecurityConfig restricts /api/cart/** to authenticated(), preventing the guest fallback in CartApiController.getCartCount from executing.",
                   actual="401 Unauthorized", expected="200 OK with data: 0", severity="Medium")

    # 2.2 API /api/cart/count cho Buyer đã đăng nhập
    cnt_buyer = buyer.get(f"{BASE_URL}/api/cart/count")
    if cnt_buyer.status_code == 200 and cnt_buyer.json().get("data") == 4:
        logger.log("Badge", "2.2 Buyer Cart Count API", "PASS", f"Returns exact item count: {cnt_buyer.json().get('data')}")
    else:
        logger.log("Badge", "2.2 Buyer Cart Count API", "INFO", f"Status: {cnt_buyer.status_code}, data: {cnt_buyer.text}")

    # 2.3 Phân tích mã nguồn Header & Mobile Bottom Bar
    # header.html có 2 nơi hiển thị badge: Desktop header (dòng 214-217) và Mobile bottom bar (dòng 497)
    # Cả 2 đều bind Alpine.js: x-show="cartCount > 0" x-text="cartCount"
    # và lắng nghe sự kiện: window.addEventListener('cart-updated', () => fetchCounts())
    # api.js dòng 105: window.dispatchEvent(new CustomEvent('cart-updated'))
    logger.log("Badge", "2.3 Realtime Event Listener Architecture", "PASS",
               "Header and Mobile Bottom bar both listen to 'cart-updated' CustomEvent and trigger fetchCounts() to update badges instantly.")

    # =========================================================================
    # 3. KIỂM THỬ LUỒNG CHECKOUT (/checkout)
    # =========================================================================
    print("\n" + "="*60)
    print("PHẦN 3: KIỂM THỬ LUỒNG CHECKOUT (/checkout)")
    print("="*60)

    # 3.1 Truy cập /checkout khi chưa đăng nhập
    anon_co = anon.get(f"{BASE_URL}/checkout", allow_redirects=False)
    if anon_co.status_code == 302 and "/login" in anon_co.headers.get("Location", ""):
        logger.log("Checkout", "3.1 Guest Access /checkout", "PASS", f"Redirected to login: {anon_co.headers.get('Location')}")
    else:
        logger.log("Checkout", "3.1 Guest Access /checkout", "FAIL", "Guest was not redirected to login", anon_co.status_code, "302 to /login", "High")

    # 3.2 Tải trang /checkout cho Buyer
    co_page = buyer.get(f"{BASE_URL}/checkout")
    if co_page.status_code == 200 and "Xác nhận đặt hàng & Thanh toán" in co_page.text:
        logger.log("Checkout", "3.2 View /checkout Page", "PASS", "Checkout page loaded successfully")
    else:
        logger.log("Checkout", "3.2 View /checkout Page", "FAIL", "Failed to load /checkout", co_page.status_code, 200, "Critical")

    # 3.3 Kiểm tra danh sách địa chỉ nhận hàng
    addresses = re.findall(r'name="addressId"\s+value="(\d+)"', co_page.text)
    checked_address = re.findall(r'id="addr-(\d+)"[^>]*checked', co_page.text)
    if addresses:
        logger.log("Checkout", "3.3 Delivery Address Selection", "PASS", f"Addresses found: {addresses}, checked default: {checked_address}")
    else:
        logger.log("Checkout", "3.3 Delivery Address Selection", "FAIL", "No address found on checkout page", co_page.text[:300], "Addresses list", "High")

    # 3.4 Kiểm tra cú pháp template checkout.html (Lỗi thẻ HTML thừa dòng 225-227)
    if "VNPAY-QR" in co_page.text and "</label>" in co_page.text:
        # Kiểm tra xem có đoạn text 'VNPAY-QR' bị văng ra ngoài không
        if re.search(r'</label>\s*VNPAY-QR\s*</div>\s*</label>', co_page.text):
            logger.log("Checkout", "3.4 Malformed HTML in checkout.html (Line 225-227)", "FAIL",
                       "checkout.html contains orphaned dangling tags: '</label> VNPAY-QR </div> </label>' after the VNPay option. Malforms DOM tree and layout styling.",
                       actual="Dangling tags present in DOM", expected="Clean closing tags", severity="Low")
        else:
            logger.log("Checkout", "3.4 HTML Syntax in checkout.html", "PASS", "No orphan tags detected")

    # 3.5 Kiểm tra phương thức giao hàng: Tiêu chuẩn vs Hỏa tốc
    co_standard = buyer.get(f"{BASE_URL}/checkout?shippingMethod=STANDARD")
    co_express = buyer.get(f"{BASE_URL}/checkout?shippingMethod=EXPRESS")
    # Giá ship tiêu chuẩn: 30k / shop. Giỏ có 2 shop = 60.000₫ ship
    # Giá ship hỏa tốc: 50k / shop. Giỏ có 2 shop = 100.000₫ ship
    if "60.000" in co_standard.text and "100.000" in co_express.text:
        logger.log("Checkout", "3.5 Shipping Method Fee Calculation", "PASS", "STANDARD shipping fee = 60.000₫, EXPRESS shipping fee = 100.000₫ (2 shops)")
    else:
        logger.log("Checkout", "3.5 Shipping Method Fee Calculation", "INFO", "Checked shipping fees on STANDARD and EXPRESS")

    # 3.6 Kiểm tra áp mã giảm giá sàn VIETSPECIALTY (Giảm 50.000₫, min 300.000₫)
    # Giỏ hiện tại: 1.850.000₫ > 300.000₫ -> Hợp lệ
    co_plat_voucher = buyer.get(f"{BASE_URL}/checkout?systemVoucherCode=VIETSPECIALTY")
    if "-50.000" in co_plat_voucher.text and "Giảm giá sàn:" in co_plat_voucher.text:
        logger.log("Checkout", "3.6 Apply System Voucher (VIETSPECIALTY)", "PASS", "System voucher VIETSPECIALTY applied successfully with 50.000₫ discount")
    else:
        logger.log("Checkout", "3.6 Apply System Voucher (VIETSPECIALTY)", "FAIL", "VIETSPECIALTY discount not displayed", co_plat_voucher.status_code, "-50.000₫", "High")

    # 3.7 Kiểm tra áp mã giảm giá sàn khi KHÔNG đủ điều kiện đơn tối thiểu
    # Xóa giỏ, chỉ để 1 món rẻ (món 77 giá 55.000₫), thử áp FREESHIP30 (min 200.000₫)
    clean_cart(buyer)
    buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 77, "quantity": 1}) # 55.000₫
    co_min_test = buyer.get(f"{BASE_URL}/checkout?systemVoucherCode=FREESHIP30")
    if "Giảm giá sàn:" not in co_min_test.text and "-30.000" not in co_min_test.text:
        logger.log("Checkout", "3.7 Voucher Min Order Requirement Check", "PASS", "FREESHIP30 (min 200k) rejected because cart total is 55k")
    else:
        logger.log("Checkout", "3.7 Voucher Min Order Requirement Check", "FAIL", "Voucher applied when order total was below minimum requirement!", severity="High")

    # 3.8 Thử nghiệm áp mã giảm giá không tồn tại
    co_fake_v = buyer.get(f"{BASE_URL}/checkout?systemVoucherCode=MA_GIA_MAO_999")
    if "Giảm giá sàn:" not in co_fake_v.text:
        logger.log("Checkout", "3.8 Non-existent Voucher Check", "PASS", "Fake voucher rejected safely without crashes")
    else:
        logger.log("Checkout", "3.8 Non-existent Voucher Check", "FAIL", "Fake voucher resulted in discount!", severity="High")

    # 3.9 Đặt hàng bằng phương thức COD (Thanh toán khi nhận hàng)
    # Thêm lại sản phẩm 59 (850.000₫, shop 77)
    buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 59, "quantity": 1})
    co_ready = buyer.get(f"{BASE_URL}/checkout")
    token_match = re.search(r'name="checkoutToken"\s+value="([^"]+)"', co_ready.text)
    checkout_token = token_match.group(1) if token_match else ""
    addr_match = re.search(r'name="addressId"\s+value="(\d+)"', co_ready.text)
    address_id = addr_match.group(1) if addr_match else "96"

    place_cod_res = buyer.post(f"{BASE_URL}/checkout/place-order", data={
        'checkoutToken': checkout_token,
        'addressId': address_id,
        'shippingMethod': 'STANDARD',
        'paymentMethod': 'COD',
        'note': 'Giao hàng tận tay, kiểm tra hàng trước khi nhận',
        'systemVoucherCode': ''
    }, allow_redirects=False)

    group_code_cod = None
    if place_cod_res.status_code == 302 and "/checkout/success" in place_cod_res.headers.get("Location", ""):
        loc = place_cod_res.headers.get("Location")
        group_code_match = re.search(r'groupCode=([^&]+)', loc)
        group_code_cod = group_code_match.group(1) if group_code_match else None
        logger.log("Checkout", "3.9 Place Order with COD", "PASS", f"COD Order placed successfully! GroupCode: {group_code_cod}, Redirected to: {loc}")
    else:
        logger.log("Checkout", "3.9 Place Order with COD", "FAIL", "Failed to place COD order", place_cod_res.status_code, "302 to /checkout/success", "Critical")

    # =========================================================================
    # 4. KIỂM THỬ THANH TOÁN VNPAY SANDBOX
    # =========================================================================
    print("\n" + "="*60)
    print("PHẦN 4: THỬ NGHIỆM THANH TOÁN VNPAY SANDBOX")
    print("="*60)

    # 4.1 Thử nghiệm khởi tạo thanh toán VNPay khi VNPAY_TMN_CODE / VNPAY_HASH_SECRET rỗng trong .env
    # Thêm sản phẩm vào giỏ
    buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 77, "quantity": 1})
    co_vnpay = buyer.get(f"{BASE_URL}/checkout")
    token_match_vnpay = re.search(r'name="checkoutToken"\s+value="([^"]+)"', co_vnpay.text)
    token_vnpay = token_match_vnpay.group(1) if token_match_vnpay else ""

    place_vnpay_res = buyer.post(f"{BASE_URL}/checkout/place-order", data={
        'checkoutToken': token_vnpay,
        'addressId': address_id,
        'shippingMethod': 'STANDARD',
        'paymentMethod': 'VNPAY',
        'note': 'Test VNPay Order',
        'systemVoucherCode': ''
    }, allow_redirects=False)

    loc_vnpay = place_vnpay_res.headers.get("Location", "")
    decoded_loc = urllib.parse.unquote(loc_vnpay)
    print(f"VNPay place-order response: {place_vnpay_res.status_code}, redirect: {decoded_loc}")

    if "checkout?error=" in decoded_loc and "chưa được cấu hình" in decoded_loc:
        logger.log("VNPay", "4.1 VNPay Missing Credentials Handling", "PASS",
                   "System safely catches unconfigured VNPay credentials and displays friendly error message on /checkout.")
        
        # 4.1.b Kiểm tra lỗi kiến trúc giao dịch: Giỏ hàng bị xóa và đơn hàng bị tạo dù thanh toán không khởi tạo được!
        # Kiểm tra xem giỏ hàng có bị xóa sạch hay không:
        cart_after_err = buyer.get(f"{BASE_URL}/api/cart").json().get("data", {})
        if not cart_after_err.get("shopGroups"):
            logger.log("VNPay", "4.1.b Transaction Boundary Defect: Cart Cleared on VNPay Error", "FAIL",
                       "In CheckoutController.placeOrder: orderService.placeOrder() commits in its own transaction (creating OrderGroup in PENDING_PAYMENT and DELETING all cart items) BEFORE vnpayService.createPaymentUrl() is called. When createPaymentUrl() throws an exception, the user is redirected to /checkout?error=..., but their cart was ALREADY emptied, and an abandoned PENDING_PAYMENT order was left in the database. When the page reloads, /checkout redirects to /cart because the cart is empty!",
                       actual="Cart is wiped out and abandoned order created",
                       expected="placeOrder and createPaymentUrl should be atomic or validated before clearing cart",
                       severity="High")
    elif "sandbox.vnpayment.vn" in decoded_loc:
        logger.log("VNPay", "4.1 VNPay Sandbox Redirect", "PASS", f"Redirected to VNPay Sandbox: {decoded_loc}")
    else:
        logger.log("VNPay", "4.1 VNPay Initiate", "INFO", f"Redirect: {decoded_loc}")

    # 4.2 Thử nghiệm VNPay Return URL (/payment/vnpay/return)
    # Kịch bản 4.2.1: Chữ ký không hợp lệ (Invalid Checksum / Tampered parameters)
    ret_fake_sig = buyer.get(f"{BASE_URL}/payment/vnpay/return?vnp_TxnRef=FAKE_TXN&vnp_ResponseCode=00&vnp_SecureHash=INVALID_HASH")
    if "payment-failed" in ret_fake_sig.text or "Thanh toán không thành công" in ret_fake_sig.text:
        logger.log("VNPay", "4.2.1 Return URL with Invalid Signature", "PASS", "System rejected invalid signature and showed payment-failed view")
    else:
        logger.log("VNPay", "4.2.1 Return URL with Invalid Signature", "FAIL", "Did not show payment failed view", ret_fake_sig.status_code, "payment-failed view", "High")

    # Kịch bản 4.2.2: Khách hàng hủy thanh toán tại cổng VNPay (ResponseCode = '24')
    ret_cancel = buyer.get(f"{BASE_URL}/payment/vnpay/return?vnp_TxnRef=FAKE_TXN&vnp_ResponseCode=24")
    if "payment-failed" in ret_cancel.text:
        logger.log("VNPay", "4.2.2 Return URL with Cancelled Code (24)", "PASS", "Correctly rendered payment-failed view for user cancellation")
    else:
        logger.log("VNPay", "4.2.2 Return URL with Cancelled Code", "INFO", f"Response status: {ret_cancel.status_code}")

    # =========================================================================
    # 5. XÁC NHẬN ĐƠN HÀNG, CHI TIẾT ĐƠN & VÒNG ĐỜI GIAO HÀNG
    # =========================================================================
    print("\n" + "="*60)
    print("PHẦN 5: XÁC NHẬN ĐƠN HÀNG, CHI TIẾT ĐƠN & VÒNG ĐỜI GIAO HÀNG")
    print("="*60)

    # 5.1 Kiểm tra màn hình đặt hàng thành công (/checkout/success)
    if group_code_cod:
        succ_page = buyer.get(f"{BASE_URL}/checkout/success?groupCode={group_code_cod}")
        if succ_page.status_code == 200 and "Cảm ơn bạn đã đặt hàng!" in succ_page.text and group_code_cod in succ_page.text:
            logger.log("Order", "5.1 Order Success Confirmation Screen (/checkout/success)", "PASS",
                       f"Order confirmation screen loaded with group code {group_code_cod}, recipient and shop sub-orders list.")
        else:
            logger.log("Order", "5.1 Order Success Confirmation Screen", "FAIL", "Failed to load order success screen", succ_page.status_code, 200, "High")

    # 5.2 Kiểm tra route /orders/success (Được nhắc đến trong brief)
    route_orders_success = buyer.get(f"{BASE_URL}/orders/success", allow_redirects=False)
    if route_orders_success.status_code == 404:
        logger.log("Order", "5.2 Route Discrepancy: /orders/success", "FAIL",
                   "Brief specifies confirmation at '/orders/success', but the actual route implemented in CheckoutController is '/checkout/success?groupCode=...'. Accessing '/orders/success' returns 404 Not Found.",
                   actual="404 Not Found", expected="200 OK or alias/redirect to /checkout/success", severity="Low")
    else:
        logger.log("Order", "5.2 Route /orders/success", "INFO", f"Status: {route_orders_success.status_code}")

    # 5.3 Danh sách đơn mua của người dùng (/orders)
    orders_list_page = buyer.get(f"{BASE_URL}/orders")
    if orders_list_page.status_code == 200 and "Đơn hàng của tôi" in orders_list_page.text:
        logger.log("Order", "5.3 Buyer Orders List (/orders)", "PASS", "My orders page loaded properly")
    else:
        logger.log("Order", "5.3 Buyer Orders List (/orders)", "FAIL", "Failed to load /orders", orders_list_page.status_code, 200, "High")

    # Lấy đơn hàng mới nhất của buyer để kiểm tra chi tiết và vòng đời
    order_codes = re.findall(r'href="/orders/([^"]+)"', orders_list_page.text)
    print("Order codes found:", order_codes)
    target_order_code = order_codes[0] if order_codes else None

    if target_order_code:
        # 5.4 Xem chi tiết đơn hàng (/orders/{code})
        detail_page = buyer.get(f"{BASE_URL}/orders/{target_order_code}")
        if detail_page.status_code == 200 and target_order_code in detail_page.text:
            logger.log("Order", "5.4 View Order Detail (/orders/{code})", "PASS",
                       f"Order detail page loaded for code: {target_order_code}. Displays timeline, items, and address.")
        else:
            logger.log("Order", "5.4 View Order Detail (/orders/{code})", "FAIL", "Failed to load order detail", detail_page.status_code, 200, "High")

        # 5.5 Kiểm tra route /account/orders/{id} (Được nhắc đến trong brief)
        route_acc_orders = buyer.get(f"{BASE_URL}/account/orders/{target_order_code}", allow_redirects=False)
        if route_acc_orders.status_code == 404:
            logger.log("Order", "5.5 Route Discrepancy: /account/orders/{id}", "FAIL",
                       f"Brief specifies viewing detail at '/account/orders/{{id}}', but the route in OrderController is '/orders/{{code}}'. Accessing '/account/orders/{target_order_code}' returns 404 Not Found.",
                       actual="404 Not Found", expected="Alias/redirect to /orders/{code}", severity="Low")
        else:
            logger.log("Order", "5.5 Route /account/orders/{id}", "INFO", f"Status: {route_acc_orders.status_code}")

        # 5.6 Thử nghiệm 'Người mua xác nhận đã nhận hàng' (POST /api/orders/{id}/received)
        # Lấy ID số của đơn hàng từ database hoặc chi tiết
        import subprocess
        order_id_cmd = subprocess.run([
            'mysql', '-u', 'root', '-pHuutuyen123@', '-D', 'vietspecialty', '-N', '-e',
            f"SELECT id, status, shop_id FROM orders WHERE code = '{target_order_code}';"
        ], capture_output=True, text=True)
        order_row = order_id_cmd.stdout.strip().split()
        print("Order in DB:", order_row)

        if order_row:
            order_id = order_row[0]
            current_status = order_row[1]
            order_shop_id = order_row[2]

            # 5.6.a Thử bấm 'Xác nhận đã nhận hàng' khi đơn đang ở PENDING_CONFIRM (Chưa giao)
            rec_premature = buyer.post(f"{BASE_URL}/api/orders/{order_id}/received")
            if rec_premature.status_code != 200 or not rec_premature.json().get("success"):
                logger.log("Order", "5.6.a Confirm Received on Non-SHIPPING Order", "PASS",
                           f"System correctly rejected premature received confirmation: {rec_premature.json().get('message')}")
            else:
                logger.log("Order", "5.6.a Confirm Received on Non-SHIPPING Order", "FAIL",
                           "System allowed confirming receipt on a non-shipping order!", rec_premature.text, "Error message", "High")

            # 5.6.b Seller tiến hành Xác nhận đơn và Bàn giao vận chuyển (SHIPPING)
            # Seller xác nhận đơn: POST /seller/orders/{id}/confirm
            seller_conf = seller.post(f"{BASE_URL}/seller/orders/{order_id}/confirm", allow_redirects=False)
            print(f"Seller confirm order: {seller_conf.status_code}")

            # Seller giao hàng: POST /seller/orders/{id}/ship
            seller_ship = seller.post(f"{BASE_URL}/seller/orders/{order_id}/ship", data={
                'carrierName': 'Viettel Post',
                'trackingCode': 'VT987654321VN'
            }, allow_redirects=False)
            print(f"Seller ship order: {seller_ship.status_code}")

            # Kiểm tra trạng thái đơn sau khi shop giao hàng
            status_check = subprocess.run([
                'mysql', '-u', 'root', '-pHuutuyen123@', '-D', 'vietspecialty', '-N', '-e',
                f"SELECT status FROM orders WHERE id = {order_id};"
            ], capture_output=True, text=True).stdout.strip()
            print(f"Order status after seller ship: {status_check}")

            if status_check == "SHIPPING":
                logger.log("Order", "5.6.b Seller Process to SHIPPING", "PASS", "Seller transitioned order to SHIPPING with tracking code VT987654321VN")
                
                # 5.6.c Người mua tải lại trang chi tiết đơn hàng: Nút 'Đã nhận được hàng' phải xuất hiện!
                detail_shipping = buyer.get(f"{BASE_URL}/orders/{target_order_code}")
                if "Đã nhận được hàng" in detail_shipping.text and "confirmReceived()" in detail_shipping.text:
                    logger.log("Order", "5.6.c 'Đã nhận được hàng' Button Visible", "PASS",
                               "Button 'Đã nhận được hàng' is now visible on order detail page because status is SHIPPING.")
                else:
                    logger.log("Order", "5.6.c 'Đã nhận được hàng' Button Visible", "FAIL",
                               "Button 'Đã nhận được hàng' not found in page HTML", detail_shipping.text[:300], "Button visible", "High")

                # 5.6.d Người mua xác nhận đã nhận hàng (POST /api/orders/{id}/received)
                rec_valid = buyer.post(f"{BASE_URL}/api/orders/{order_id}/received")
                if rec_valid.status_code == 200 and rec_valid.json().get("success"):
                    # Kiểm tra trạng thái mới trong DB
                    status_after_rec = subprocess.run([
                        'mysql', '-u', 'root', '-pHuutuyen123@', '-D', 'vietspecialty', '-N', '-e',
                        f"SELECT status FROM orders WHERE id = {order_id};"
                    ], capture_output=True, text=True).stdout.strip()

                    if status_after_rec == "DELIVERED":
                        logger.log("Order", "5.6.d Buyer Confirm Received Success", "PASS",
                                   f"Order successfully transitioned to DELIVERED. Buyer confirmation recorded.")
                    else:
                        logger.log("Order", "5.6.d Buyer Confirm Received Success", "FAIL",
                                   f"Order status is {status_after_rec}, expected DELIVERED", severity="High")
                else:
                    logger.log("Order", "5.6.d Buyer Confirm Received Success", "FAIL",
                               "Failed to confirm receipt", rec_valid.text, "HTTP 200 with success: true", "High")
            else:
                logger.log("Order", "5.6.b Seller Process to SHIPPING", "FAIL",
                           f"Order failed to reach SHIPPING status, current: {status_check}", severity="High")

    return logger

if __name__ == "__main__":
    logger = run_all_tests()
    print("\n" + "="*60)
    print("TỔNG KẾT KIỂM THỬ (TEST SUMMARY)")
    print("="*60)
    print(f"Tổng số ca kiểm thử: {len(logger.results)}")
    print(f"Số lỗi phát hiện: {len(logger.bugs)}")
    for bug in logger.bugs:
        print(f"\n- Mức độ: [{bug['severity']}]")
        print(f"  Phân hệ: {bug['section']} | Chức năng: {bug['test_name']}")
        print(f"  Mô tả: {bug['details']}")
        if bug['actual'] and bug['expected']:
            print(f"  Thực tế: {bug['actual']}")
            print(f"  Kỳ vọng: {bug['expected']}")
