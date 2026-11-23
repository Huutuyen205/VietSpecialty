import sys
sys.path.append('d:/VietSpecialty/scratch')
from test_cart_checkout import get_session
import json
import re

buyer = get_session("buyer@vietspecialty.vn", "Password123@")

# Check cart
cart_res = buyer.get("http://localhost:8080/api/cart")
print("Cart data:", json.dumps(cart_res.json(), indent=2, ensure_ascii=False))

# Try /checkout with systemVoucherCode=VIETSPECIALTY
res_voucher = buyer.get("http://localhost:8080/checkout?systemVoucherCode=VIETSPECIALTY")
print("Status:", res_voucher.status_code)
print("URL:", res_voucher.url)

# Search for discount or error in checkout HTML
for line in res_voucher.text.split("\n"):
    if any(k in line.lower() for k in ["giảm giá", "voucher", "50.000", "vietspecialty", "tổng cộng"]):
        print("  ", line.strip())
