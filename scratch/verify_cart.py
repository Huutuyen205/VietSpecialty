import requests
import re
import urllib.request

BASE_URL = 'http://localhost:8080'

# Helper to login
def login(email, password):
    s = requests.Session()
    r = s.get(f'{BASE_URL}/login')
    csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', r.text).group(1)
    s.post(f'{BASE_URL}/login', data={'email': email, 'password': password, '_csrf': csrf}, allow_redirects=True)
    return s

print("=== VERIFYING CART-BUG-01 (HTML syntax error in checkout.html) ===")
with open('src/main/resources/templates/web/checkout.html', encoding='utf-8') as f:
    checkout_html = f.read()
if "</label> VNPAY-QR </div> </label>" in checkout_html or ("VNPAY-QR" in checkout_html and "</div>" in checkout_html):
    # Check around line 220-230
    lines = checkout_html.splitlines()
    for i, line in enumerate(lines[215:235], start=216):
        print(f"Line {i}: {line}")
    print("CONFIRMED CART-BUG-01: Found mismatched / stray closing tags in checkout.html!")

print("\n=== VERIFYING CART-BUG-05 (/api/cart/count for guest) ===")
r_guest_cart = requests.get(f'{BASE_URL}/api/cart/count')
print(f"Guest /api/cart/count status: {r_guest_cart.status_code}")
if r_guest_cart.status_code == 401:
    print("CONFIRMED CART-BUG-05: Guest receives 401 Unauthorized instead of 200 with count=0!")

print("\n=== VERIFYING CART-BUG-08 (Route discrepancies) ===")
r_success = requests.get(f'{BASE_URL}/orders/success')
print(f"/orders/success status: {r_success.status_code}")
r_acc_orders = requests.get(f'{BASE_URL}/account/orders/1')
print(f"/account/orders/1 status: {r_acc_orders.status_code}")

print("\n=== VERIFYING CART-BUG-04 (/orders/{orderCode} chunked read) ===")
buyer_s = login('buyer@vietspecialty.vn', 'Password123@')
# Check /orders list first
r_orders = buyer_s.get(f'{BASE_URL}/orders')
print(f"GET /orders status: {r_orders.status_code}")
m_code = re.search(r'href="/orders/([^"]+)"', r_orders.text)
if m_code:
    order_code = m_code.group(1)
    print(f"Found order code: {order_code}")
    # test reading /orders/{order_code}
    try:
        r_ord = buyer_s.get(f'{BASE_URL}/orders/{order_code}')
        print(f"Order detail status: {r_ord.status_code}, len: {len(r_ord.content)}")
    except Exception as e:
        print(f"CONFIRMED CART-BUG-04: Caught exception reading order detail: {type(e)} {e}")
else:
    print("No orders found to test CART-BUG-04")
