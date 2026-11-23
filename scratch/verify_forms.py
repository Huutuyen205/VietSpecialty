import requests
import re

BASE_URL = 'http://localhost:8080'

# Helper to log in
def login(email, password):
    s = requests.Session()
    r = s.get(f'{BASE_URL}/login')
    csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', r.text).group(1)
    r2 = s.post(f'{BASE_URL}/login', data={'email': email, 'password': password, '_csrf': csrf}, allow_redirects=True)
    return s

print("--- Testing FORM-BUG-01 (Voucher Crash 500) ---")
buyer_s = login('buyer@vietspecialty.vn', 'Password123@')

# First ensure buyer has something in cart
# Let's get a product id or add to cart
# Let's check products
r_prod = buyer_s.get(f'{BASE_URL}/products')
m = re.search(r'href="/products/([^"/]+)"', r_prod.text)
slug = m.group(1) if m else 'tra-tan-cuong-moc-cau-thuong-hang'
print(f"Product slug: {slug}")
r_detail = buyer_s.get(f'{BASE_URL}/products/{slug}')
prod_id_m = re.search(r'name="productId"\s+value="(\d+)"', r_detail.text) or re.search(r'data-product-id="(\d+)"', r_detail.text) or re.search(r'/products/(\d+)/', r_detail.text)

# Let's check cart or add item to cart via API
csrf_detail = re.search(r'name="_csrf"\s+value="([^"]+)"', r_detail.text)
csrf_val = csrf_detail.group(1) if csrf_detail else None

# Check /api/cart
r_cart = buyer_s.get(f'{BASE_URL}/api/cart')
print(f"Current cart status: {r_cart.status_code}, length: {len(r_cart.json().get('items', [])) if r_cart.status_code==200 else 0}")
if r_cart.status_code == 200 and len(r_cart.json().get('items', [])) == 0:
    # find first product id from detail page
    p_id = re.search(r'action="[^"]*cart[^"]*"[\s\S]*?name="productId"\s+value="(\d+)"', r_detail.text)
    if not p_id:
        p_id = re.search(r'value="(\d+)"\s+name="productId"', r_detail.text)
    # Add to cart
    add_r = buyer_s.post(f'{BASE_URL}/cart/add', data={'productId': 1, 'quantity': 1, '_csrf': csrf_val})
    print(f"Added item 1 to cart: {add_r.status_code}")

# Now test /checkout with systemVoucherCode=KHONGTONTAI123
r_checkout = buyer_s.get(f'{BASE_URL}/checkout?systemVoucherCode=KHONGTONTAI123')
print(f"FORM-BUG-01 status: {r_checkout.status_code}")
if r_checkout.status_code == 500:
    print("CONFIRMED FORM-BUG-01: Received HTTP 500 Internal Server Error when applying invalid voucher!")
else:
    print(f"FORM-BUG-01 not 500, received {r_checkout.status_code}")

print("\n--- Testing FORM-BUG-02 & FORM-BUG-03 (Q&A Comments) ---")
# Let's find product id from detail page
prod_id = 1
# Find product id in r_detail
prod_id_match = re.search(r'action="/products/(\d+)/comments"', r_detail.text)
if prod_id_match:
    prod_id = prod_id_match.group(1)
print(f"Product ID for comments: {prod_id}")

# FORM-BUG-02: empty comment
csrf_detail = re.search(r'name="_csrf"\s+value="([^"]+)"', r_detail.text).group(1)
r_comment_empty = buyer_s.post(f'{BASE_URL}/products/{prod_id}/comments', data={'content': '   ', '_csrf': csrf_detail}, allow_redirects=True)
print(f"Empty comment post status: {r_comment_empty.status_code}")
has_error_message = 'Nội dung' in r_comment_empty.text or 'error' in r_comment_empty.text.lower() and 'alert' in r_comment_empty.text.lower()
print(f"Empty comment displayed error message on page: {has_error_message}")
# Check if template has th:if="${errorMessage}"
print("Checking template for flash message:", 'errorMessage' in r_comment_empty.text)

# FORM-BUG-03: comment > 1000 chars
long_comment = "A" * 1500
csrf_detail = re.search(r'name="_csrf"\s+value="([^"]+)"', r_comment_empty.text).group(1)
r_comment_long = buyer_s.post(f'{BASE_URL}/products/{prod_id}/comments', data={'content': long_comment, '_csrf': csrf_detail}, allow_redirects=True)
print(f"FORM-BUG-03 (>1000 chars) post status: {r_comment_long.status_code}")
if r_comment_long.status_code == 500:
    print("CONFIRMED FORM-BUG-03: Received HTTP 500 when submitting comment > 1000 chars!")

print("\n--- Testing FORM-BUG-04 (/admin/categories/create) ---")
admin_s = login('admin@vietspecialty.vn', 'Password123@')
r_cat_create = admin_s.get(f'{BASE_URL}/admin/categories/create')
print(f"FORM-BUG-04 /admin/categories/create status: {r_cat_create.status_code}")
if r_cat_create.status_code in [404, 403]:
    print(f"CONFIRMED FORM-BUG-04: /admin/categories/create returns {r_cat_create.status_code}!")
