import sys
sys.path.append('d:/VietSpecialty/scratch')
from test_cart_checkout import get_session
import re
import subprocess

def clean_cart(s):
    res = s.get("http://localhost:8080/api/cart")
    data = res.json().get("data", {})
    if data and data.get("shopGroups"):
        for sg in data["shopGroups"]:
            for item in sg["items"]:
                s.delete(f"http://localhost:8080/api/cart/items/{item['id']}")

buyer = get_session("buyer@vietspecialty.vn", "Password123@")
clean_cart(buyer)

# Add product 59 (Shop 77, price 850.000₫)
buyer.post("http://localhost:8080/api/cart/items", json={"productId": 59, "quantity": 1})

# Get checkout token
co_page = buyer.get("http://localhost:8080/checkout?systemVoucherCode=VIETSPECIALTY")
token = re.search(r'name="checkoutToken"\s+value="([^"]+)"', co_page.text).group(1)

# Place COD order with BOTH system voucher and shop voucher
res = buyer.post("http://localhost:8080/checkout/place-order", data={
    'checkoutToken': token,
    'addressId': '96',
    'shippingMethod': 'STANDARD',
    'paymentMethod': 'COD',
    'note': 'Test Stacking Platform + Shop Voucher',
    'systemVoucherCode': 'VIETSPECIALTY',
    'shopVoucherCodes[77]': 'TANCUONG10'
}, allow_redirects=False)

print("Place order status:", res.status_code, "Location:", res.headers.get("Location"))

cmd = [
    'mysql', '-u', 'root', '-pHuutuyen123@', '-D', 'vietspecialty', '-e',
    "SELECT id, code, shop_id, items_subtotal, shipping_fee, shop_discount, platform_discount, total_amount FROM orders ORDER BY id DESC LIMIT 1;"
]
out = subprocess.run(cmd, capture_output=True, text=True).stdout
print(out)
