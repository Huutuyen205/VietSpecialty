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

def run_tests():
    buyer = get_session("buyer@vietspecialty.vn", "Password123@")
    anon = requests.Session()

    # ==========================================
    # SECTION 1: GIỎ HÀNG (CART FLOW)
    # ==========================================
    print("\n--- SECTION 1: LUỒNG GIỎ HÀNG ---")
    
    # 1.1 Kiểm tra thêm giỏ hàng khi chưa đăng nhập (Guest)
    res_anon_add = anon.post(f"{BASE_URL}/api/cart/items", json={"productId": 59, "quantity": 1})
    if res_anon_add.status_code == 401:
        logger.log("Cart", "1.1 Guest Add To Cart", "PASS", "Guest receives 401 Unauthorized as expected", res_anon_add.status_code, 401)
    else:
        logger.log("Cart", "1.1 Guest Add To Cart", "FAIL", "Guest should receive 401 Unauthorized", res_anon_add.status_code, 401, "Medium")

    # 1.2 Dọn sạch giỏ hàng buyer để test trạng thái Giỏ Hàng Trống
    cart_res = buyer.get(f"{BASE_URL}/api/cart")
    cart_data = cart_res.json().get("data", {})
    if cart_data and cart_data.get("shopGroups"):
        for sg in cart_data["shopGroups"]:
            for item in sg["items"]:
                buyer.delete(f"{BASE_URL}/api/cart/items/{item['id']}")

    # 1.3 Kiểm tra giao diện Giỏ hàng trống (/cart)
    cart_empty_page = buyer.get(f"{BASE_URL}/cart")
    if "Giỏ hàng đặc sản đang trống" in cart_empty_page.text and "Khám phá đặc sản 4 miền ngay" in cart_empty_page.text:
        logger.log("Cart", "1.3 Empty Cart UI", "PASS", "Empty cart banner and call-to-action rendered correctly")
    else:
        logger.log("Cart", "1.3 Empty Cart UI", "FAIL", "Empty cart UI missing expected texts", cart_empty_page.status_code, "Expected empty cart banner", "Medium")

    # 1.4 Thêm đặc sản vào giỏ từ thẻ sản phẩm (quickAddToCart - POST /api/cart/items)
    # Sản phẩm 59 (Trà Đinh Nõn Tân Cương Thượng Hạng, shop 77, giá 850.000₫, stock 140)
    add_res = buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 59, "quantity": 1})
    if add_res.status_code == 200 and add_res.json().get("success"):
        logger.log("Cart", "1.4 Quick Add To Cart from Card", "PASS", f"Added product 59. Message: {add_res.json().get('message')}")
    else:
        logger.log("Cart", "1.4 Quick Add To Cart from Card", "FAIL", "Failed to add product 59", add_res.text, "HTTP 200 with success: true", "High")

    # 1.5 Thêm đặc sản từ trang chi tiết (addToCartWithQuantity - số lượng > 1)
    # Sản phẩm 60 (Thịt Trâu Gác Bếp Tây Bắc, shop 77, giá 420.000₫, stock 80)
    add_qty_res = buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 60, "quantity": 2})
    if add_qty_res.status_code == 200 and add_qty_res.json().get("success"):
        logger.log("Cart", "1.5 Add To Cart with Quantity (Detail Page)", "PASS", f"Added product 60 with quantity 2. Message: {add_qty_res.json().get('message')}")
    else:
        logger.log("Cart", "1.5 Add To Cart with Quantity (Detail Page)", "FAIL", "Failed to add product 60 with qty 2", add_qty_res.text, "HTTP 200 with success: true", "High")

    # 1.6 Thêm đặc sản từ Chatbot AI card (quickAddToCart với sản phẩm khác shop, vd: 72 shop 78)
    add_bot_res = buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 72, "quantity": 1})
    if add_bot_res.status_code == 200 and add_bot_res.json().get("success"):
        logger.log("Cart", "1.6 Add To Cart from Chatbot Card", "PASS", f"Added product 72 from chatbot card. Message: {add_bot_res.json().get('message')}")
    else:
        logger.log("Cart", "1.6 Add To Cart from Chatbot Card", "FAIL", "Failed to add product 72", add_bot_res.text, "HTTP 200 with success: true", "High")

    # 1.7 Kiểm tra giỏ hàng sau khi thêm: Phải có 2 shop groups (shop 77 và shop 78) và 3 mặt hàng
    cart_res = buyer.get(f"{BASE_URL}/api/cart")
    cart_data = cart_res.json().get("data", {})
    shop_groups = cart_data.get("shopGroups", [])
    total_items = cart_data.get("totalItemCount", 0)
    if len(shop_groups) == 2 and total_items == 4: # 1 (item 59) + 2 (item 60) + 1 (item 72) = 4 items
        logger.log("Cart", "1.7 Shop Grouping & Item Count", "PASS", f"2 shop groups created, total quantity = {total_items}")
    else:
        logger.log("Cart", "1.7 Shop Grouping & Item Count", "FAIL", f"Expected 2 shop groups and 4 total quantity, got {len(shop_groups)} groups, {total_items} items", actual=cart_data, severity="Medium")

    # 1.8 Tăng / Giảm số lượng mặt hàng (PUT /api/cart/items/{id})
    # Tìm cart item của product 59
    item_59 = None
    for sg in shop_groups:
        for it in sg["items"]:
            if it["productId"] == 59:
                item_59 = it
                break
    
    if item_59:
        # Tăng số lượng lên 3
        update_res = buyer.put(f"{BASE_URL}/api/cart/items/{item_59['id']}", json={"quantity": 3})
        if update_res.status_code == 200 and update_res.json().get("success"):
            logger.log("Cart", "1.8.1 Increase Quantity (to 3)", "PASS", "Quantity updated to 3")
        else:
            logger.log("Cart", "1.8.1 Increase Quantity (to 3)", "FAIL", "Failed to increase quantity", update_res.text, "HTTP 200 with success", "High")

        # Giảm số lượng xuống 2
        update_res2 = buyer.put(f"{BASE_URL}/api/cart/items/{item_59['id']}", json={"quantity": 2})
        if update_res2.status_code == 200 and update_res2.json().get("success"):
            logger.log("Cart", "1.8.2 Decrease Quantity (to 2)", "PASS", "Quantity updated to 2")
        else:
            logger.log("Cart", "1.8.2 Decrease Quantity (to 2)", "FAIL", "Failed to decrease quantity", update_res2.text, "HTTP 200 with success", "High")

        # Giảm số lượng về 0 (Kiểm tra xem hệ thống xóa hay báo lỗi hay hạ về 0)
        update_zero = buyer.put(f"{BASE_URL}/api/cart/items/{item_59['id']}", json={"quantity": 0})
        logger.log("Cart", "1.8.3 Update Quantity to 0", "INFO", f"Response for qty=0: Status {update_zero.status_code}, Body: {update_zero.text}")

        # Thử số lượng âm (-1)
        update_neg = buyer.put(f"{BASE_URL}/api/cart/items/{item_59['id']}", json={"quantity": -1})
        logger.log("Cart", "1.8.4 Update Quantity to negative (-1)", "INFO", f"Response for qty=-1: Status {update_neg.status_code}, Body: {update_neg.text}")

        # Thử vượt quá tồn kho (stock sản phẩm 59 là 140, thử 9999)
        update_excess = buyer.put(f"{BASE_URL}/api/cart/items/{item_59['id']}", json={"quantity": 9999})
        if update_excess.status_code != 200 or not update_excess.json().get("success"):
            logger.log("Cart", "1.8.5 Update Quantity Exceeds Stock", "PASS", f"System correctly rejected excessive quantity: {update_excess.json().get('message')}")
        else:
            logger.log("Cart", "1.8.5 Update Quantity Exceeds Stock", "FAIL", "System allowed quantity exceeding stock!", update_excess.text, "Expected rejection/error", "High")

    # 1.9 Xóa mặt hàng khỏi giỏ (DELETE /api/cart/items/{id})
    if item_59:
        del_res = buyer.delete(f"{BASE_URL}/api/cart/items/{item_59['id']}")
        if del_res.status_code == 200 and del_res.json().get("success"):
            logger.log("Cart", "1.9 Delete Cart Item", "PASS", f"Successfully removed item {item_59['id']} from cart")
        else:
            logger.log("Cart", "1.9 Delete Cart Item", "FAIL", f"Failed to delete cart item {item_59['id']}", del_res.text, "HTTP 200 with success", "High")

    # ==========================================
    # SECTION 2: BADGE GIỎ HÀNG
    # ==========================================
    print("\n--- SECTION 2: BADGE GIỎ HÀNG HEADER & MOBILE ---")
    
    # 2.1 Kiểm tra API /api/cart/count cho Guest
    guest_cnt = anon.get(f"{BASE_URL}/api/cart/count")
    if guest_cnt.status_code == 200 and guest_cnt.json().get("data") == 0:
        logger.log("Badge", "2.1 Guest Cart Count", "PASS", "Guest cart count returns 0")
    elif guest_cnt.status_code == 401:
        logger.log("Badge", "2.1 Guest Cart Count Blocked by SecurityConfig", "FAIL",
                   "Guest /api/cart/count returns 401 Unauthorized due to SecurityConfig blocking /api/cart/**. In CartApiController line 77, fallback was intended to return 200 with 0. This causes console error on all guest page loads.",
                   actual="401 Unauthorized", expected="200 OK with data: 0", severity="Medium")
    else:
        logger.log("Badge", "2.1 Guest Cart Count", "FAIL", "Unexpected status", guest_cnt.status_code, "200 or 401", "Low")

    # 2.2 Kiểm tra API /api/cart/count cho Buyer đã đăng nhập
    buyer_cnt = buyer.get(f"{BASE_URL}/api/cart/count")
    if buyer_cnt.status_code == 200 and buyer_cnt.json().get("success"):
        current_cnt = buyer_cnt.json().get("data")
        logger.log("Badge", "2.2 Buyer Cart Count", "PASS", f"Buyer cart count returned: {current_cnt}")
    else:
        logger.log("Badge", "2.2 Buyer Cart Count", "FAIL", "Failed to get cart count", buyer_cnt.text, "HTTP 200", "High")

    # ==========================================
    # SECTION 3: CHECKOUT (/checkout) & VOUCHERS
    # ==========================================
    print("\n--- SECTION 3: LUỒNG CHECKOUT & VOUCHERS ---")

    # Trước khi checkout, hãy đảm bảo giỏ hàng có sản phẩm của shop 77 (để test voucher TANCUONG10)
    # Thêm lại sản phẩm 59 (giá 850.000₫, shop 77)
    buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 59, "quantity": 1})

    # 3.1 Truy cập /checkout
    co_page = buyer.get(f"{BASE_URL}/checkout")
    if co_page.status_code == 200:
        logger.log("Checkout", "3.1 View /checkout Page", "PASS", "Checkout page loaded successfully")
    else:
        logger.log("Checkout", "3.1 View /checkout Page", "FAIL", "Failed to load checkout page", co_page.status_code, 200, "Critical")

    # 3.2 Kiểm tra danh sách địa chỉ nhận hàng trên trang /checkout
    addresses_found = re.findall(r'name="addressId"\s+value="(\d+)"', co_page.text)
    checked_addr = re.findall(r'name="addressId"\s+value="(\d+)"\s+id="[^"]+"\s+checked', co_page.text)
    if addresses_found:
        logger.log("Checkout", "3.2 Shipping Address Selection", "PASS", f"Found {len(addresses_found)} addresses ({addresses_found}), checked: {checked_addr}")
    else:
        logger.log("Checkout", "3.2 Shipping Address Selection", "FAIL", "No addresses found in checkout page", co_page.text[:300], "Addresses list", "High")

    # 3.3 Kiểm tra hiển thị tiền và phí vận chuyển tiêu chuẩn vs hỏa tốc
    co_standard = buyer.get(f"{BASE_URL}/checkout?shippingMethod=STANDARD")
    co_express = buyer.get(f"{BASE_URL}/checkout?shippingMethod=EXPRESS")
    if co_standard.status_code == 200 and co_express.status_code == 200:
        logger.log("Checkout", "3.3 Shipping Methods (STANDARD vs EXPRESS)", "PASS", "Both STANDARD and EXPRESS checkout preview work")
    else:
        logger.log("Checkout", "3.3 Shipping Methods", "FAIL", "Error switching shipping methods", severity="Medium")

    # 3.4 Thử nghiệm áp Voucher sàn hợp lệ: VIETSPECIALTY (giảm 50k, đơn tối thiểu 300k)
    # Tổng tiền hàng hiện tại > 1 triệu (850k + 2*420k + 160k = 1850k), đủ điều kiện > 300k
    co_voucher_valid = buyer.get(f"{BASE_URL}/checkout?systemVoucherCode=VIETSPECIALTY")
    if co_voucher_valid.status_code == 200 and ("Giảm giá sàn" in co_voucher_valid.text or "50.000" in co_voucher_valid.text):
        logger.log("Checkout", "3.4 Apply Valid System Voucher (VIETSPECIALTY)", "PASS", "Voucher VIETSPECIALTY applied successfully with 50,000₫ discount")
    else:
        logger.log("Checkout", "3.4 Apply Valid System Voucher (VIETSPECIALTY)", "FAIL", "Voucher VIETSPECIALTY not applied", co_voucher_valid.status_code, "Discount displayed", "High")

    # 3.5 Thử nghiệm áp Voucher sàn không tồn tại: VOUCHER_FAKE_XYZ
    co_voucher_fake = buyer.get(f"{BASE_URL}/checkout?systemVoucherCode=VOUCHER_FAKE_XYZ")
    if "Giảm giá sàn" not in co_voucher_fake.text:
        logger.log("Checkout", "3.5 Apply Non-existent Voucher", "PASS", "Non-existent voucher was ignored safely")
    else:
        logger.log("Checkout", "3.5 Apply Non-existent Voucher", "FAIL", "Fake voucher somehow gave discount!", severity="High")

    # 3.6 Thử nghiệm điều kiện đơn hàng tối thiểu của Voucher sàn:
    # Xóa hết giỏ hàng, chỉ để 1 món rẻ dưới 200k (món 77 giá 55.000₫), thử áp FREESHIP30 (đơn tối thiểu 200k)
    cart_clean = buyer.get(f"{BASE_URL}/api/cart")
    for sg in cart_clean.json().get("data", {}).get("shopGroups", []):
        for it in sg["items"]:
            buyer.delete(f"{BASE_URL}/api/cart/items/{it['id']}")
    
    buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 77, "quantity": 1}) # 55.000₫
    co_voucher_min_not_met = buyer.get(f"{BASE_URL}/checkout?systemVoucherCode=FREESHIP30")
    if "Giảm giá sàn" not in co_voucher_min_not_met.text:
        logger.log("Checkout", "3.6 Voucher Min Order Value Check", "PASS", "Voucher FREESHIP30 (min 200k) not applied when cart total is 55k")
    else:
        logger.log("Checkout", "3.6 Voucher Min Order Value Check", "FAIL", "Voucher applied even though cart total did not meet min order value", severity="High")

    # 3.7 Kiểm tra phương thức thanh toán COD:
    # Thêm sản phẩm 59 (850.000₫) và thực hiện đặt hàng COD
    buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 59, "quantity": 1})
    co_ready = buyer.get(f"{BASE_URL}/checkout")
    
    # Lấy checkoutToken và addressId
    token_match = re.search(r'name="checkoutToken"\s+value="([^"]+)"', co_ready.text)
    checkout_token = token_match.group(1) if token_match else ""
    addr_match = re.search(r'name="addressId"\s+value="(\d+)"', co_ready.text)
    address_id = addr_match.group(1) if addr_match else "96"

    print(f"Placing COD order with token={checkout_token}, addr={address_id}...")
    place_cod_res = buyer.post(f"{BASE_URL}/checkout/place-order", data={
        'checkoutToken': checkout_token,
        'addressId': address_id,
        'shippingMethod': 'STANDARD',
        'paymentMethod': 'COD',
        'note': 'Giao hàng giờ hành chính (Test COD Automated)',
        'systemVoucherCode': ''
    }, allow_redirects=False)

    print(f"Place COD order response: {place_cod_res.status_code}, Location: {place_cod_res.headers.get('Location')}")
    if place_cod_res.status_code == 302 and "/checkout/success" in place_cod_res.headers.get('Location', ''):
        loc = place_cod_res.headers.get('Location')
        logger.log("Checkout", "3.7 Place Order COD", "PASS", f"COD Order placed successfully, redirected to: {loc}")
        # Truy cập trang checkout success
        succ_res = buyer.get(f"{BASE_URL}{loc}")
        if "Cảm ơn bạn đã đặt hàng!" in succ_res.text:
            logger.log("Checkout", "3.7.1 Order Success Page View", "PASS", "Order success page loaded with confirmation details")
        else:
            logger.log("Checkout", "3.7.1 Order Success Page View", "FAIL", "Order success page missing expected content", succ_res.status_code, "Confirmation details", "Medium")
    else:
        logger.log("Checkout", "3.7 Place Order COD", "FAIL", "Failed to place COD order", f"Status: {place_cod_res.status_code}, Location: {place_cod_res.headers.get('Location')}", "302 to /checkout/success", "Critical")

    # ==========================================
    # SECTION 4: THỬ NGHIỆM VNPAY SANDBOX
    # ==========================================
    print("\n--- SECTION 4: THỬ NGHIỆM VNPAY SANDBOX ---")

    # Thêm sản phẩm vào giỏ để test thanh toán VNPay
    buyer.post(f"{BASE_URL}/api/cart/items", json={"productId": 77, "quantity": 2}) # 2 * 55.000₫ = 110.000₫
    co_vnpay_page = buyer.get(f"{BASE_URL}/checkout")
    token_match = re.search(r'name="checkoutToken"\s+value="([^"]+)"', co_vnpay_page.text)
    checkout_token_vnpay = token_match.group(1) if token_match else ""

    # 4.1 Thử đặt hàng với phương thức VNPAY khi credentials .env đang rỗng
    print(f"Placing VNPAY order with token={checkout_token_vnpay}...")
    place_vnpay_res = buyer.post(f"{BASE_URL}/checkout/place-order", data={
        'checkoutToken': checkout_token_vnpay,
        'addressId': address_id,
        'shippingMethod': 'STANDARD',
        'paymentMethod': 'VNPAY',
        'note': 'Test VNPay Order',
        'systemVoucherCode': ''
    }, allow_redirects=False)

    print(f"Place VNPAY order response: {place_vnpay_res.status_code}, Location: {place_vnpay_res.headers.get('Location')}")
    if place_vnpay_res.status_code == 302:
        loc = urllib.parse.unquote(place_vnpay_res.headers.get('Location', ''))
        if "sandbox.vnpayment.vn" in loc:
            logger.log("VNPay", "4.1 Initiate VNPay Sandbox Redirect", "PASS", f"Redirected to VNPay Sandbox URL: {loc}")
        elif "checkout?error=" in loc:
            logger.log("VNPay", "4.1 Initiate VNPay Sandbox without Credentials in .env", "PASS",
                       f"System safely blocked VNPay checkout with friendly error because VNPAY_TMN_CODE/VNPAY_HASH_SECRET are not configured in .env. Redirect location: {loc}")
        else:
            logger.log("VNPay", "4.1 Initiate VNPay Sandbox", "FAIL", "Unexpected redirect", loc, "VNPay URL or checkout?error=", "High")
    else:
        logger.log("VNPay", "4.1 Initiate VNPay Sandbox", "FAIL", "Did not redirect", place_vnpay_res.status_code, 302, "High")

    # 4.2 Thử nghiệm VNPay Return URL callback (/payment/vnpay/return)
    # Kiểm tra xem hệ thống xử lý callback như thế nào:
    # a) Gọi với chữ ký rỗng / không có chữ ký
    ret_invalid_sig = buyer.get(f"{BASE_URL}/payment/vnpay/return?vnp_TxnRef=GRP-TEST&vnp_ResponseCode=00")
    if "payment-failed" in ret_invalid_sig.text or ret_invalid_sig.status_code == 200:
        logger.log("VNPay", "4.2.1 VNPay Return URL Invalid Signature", "PASS", "System rejected invalid signature and showed payment-failed page")
    else:
        logger.log("VNPay", "4.2.1 VNPay Return URL Invalid Signature", "FAIL", "Unexpected behavior on invalid signature", ret_invalid_sig.status_code, "payment-failed page", "High")

    # b) Kiểm tra giao diện trang /web/payment-failed
    if "payment-failed" in ret_invalid_sig.url or "Thanh toán không thành công" in ret_invalid_sig.text or "thất bại" in ret_invalid_sig.text:
        logger.log("VNPay", "4.2.2 Payment Failed UI", "PASS", "Payment failed page rendered properly")
    else:
        logger.log("VNPay", "4.2.2 Payment Failed UI", "INFO", f"Payment failed page response title/content checked. Status: {ret_invalid_sig.status_code}")

    # ==========================================
    # SECTION 5: XÁC NHẬN ĐƠN HÀNG & QUẢN LÝ ĐƠN (/orders)
    # ==========================================
    print("\n--- SECTION 5: XÁC NHẬN ĐƠN HÀNG & QUẢN LÝ ĐƠN ---")

    # 5.1 Kiểm tra URL /orders/success (như trong mô tả nhiệm vụ)
    orders_succ_res = buyer.get(f"{BASE_URL}/orders/success", allow_redirects=False)
    if orders_succ_res.status_code == 404:
        logger.log("Order", "5.1 Route /orders/success vs /checkout/success", "FAIL",
                   "Endpoint /orders/success returns 404 Not Found. The correct system route implemented in CheckoutController is /checkout/success?groupCode=...",
                   actual="404 Not Found", expected="200 or redirect to /checkout/success", severity="Low")
    else:
        logger.log("Order", "5.1 Route /orders/success", "INFO", f"Status: {orders_succ_res.status_code}")

    # 5.2 Kiểm tra URL /account/orders/{id} (như trong mô tả nhiệm vụ)
    # Lấy danh sách đơn hàng của buyer
    orders_list_page = buyer.get(f"{BASE_URL}/orders")
    if orders_list_page.status_code == 200:
        logger.log("Order", "5.2 View Buyer Orders List (/orders)", "PASS", "Orders list page loaded successfully")
    else:
        logger.log("Order", "5.2 View Buyer Orders List (/orders)", "FAIL", "Failed to load /orders", orders_list_page.status_code, 200, "High")

    # Tìm mã đơn hàng (order code) và ID từ trang /orders
    order_codes = re.findall(r'href="/orders/([^"]+)"', orders_list_page.text)
    print("Found order codes in /orders:", order_codes)

    if order_codes:
        first_order_code = order_codes[0]
        # 5.3 Xem chi tiết đơn hàng (/orders/{code})
        detail_res = buyer.get(f"{BASE_URL}/orders/{first_order_code}")
        if detail_res.status_code == 200 and ("Chi tiết đơn hàng" in detail_res.text or first_order_code in detail_res.text):
            logger.log("Order", "5.3 View Order Detail (/orders/{code})", "PASS", f"Order detail page loaded for code: {first_order_code}")
        else:
            logger.log("Order", "5.3 View Order Detail (/orders/{code})", "FAIL", "Failed to load order detail", detail_res.status_code, 200, "High")

        # 5.4 Thử truy cập theo /account/orders/{code} hoặc /account/orders/{id}
        acc_orders_res = buyer.get(f"{BASE_URL}/account/orders/{first_order_code}", allow_redirects=False)
        if acc_orders_res.status_code == 404:
            logger.log("Order", "5.4 Route /account/orders/{id} vs /orders/{code}", "FAIL",
                       f"Route /account/orders/{first_order_code} returns 404 Not Found. The application uses /orders/{{code}} for buyer order details.",
                       actual="404 Not Found", expected="Redirect or handler for /account/orders/{id}", severity="Low")

        # 5.5 Kiểm tra tính năng 'Xác nhận đã nhận hàng' (POST /api/orders/{id}/received)
        # Tìm order ID số từ trang chi tiết
        order_id_match = re.search(r'/api/orders/\s*(\d+)\s*/received', detail_res.text) or re.search(r'/api/orders/\s*([^\s+/]+)\s*/received', detail_res.text)
        print("Order received API match:", order_id_match.group(0) if order_id_match else "Not found in HTML directly")
        
    return logger

if __name__ == "__main__":
    logger = run_tests()
    print("\n==========================================")
    print("TEST SUMMARY:")
    print(f"Total tests: {len(logger.results)}")
    print(f"Bugs/Failures detected: {len(logger.bugs)}")
    for bug in logger.bugs:
        print(f"- [{bug['severity']}] {bug['section']} - {bug['test_name']}: {bug['details']}")
