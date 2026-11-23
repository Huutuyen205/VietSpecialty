import sys
sys.path.append('d:/VietSpecialty/scratch')
from test_cart_checkout import get_session
import re

buyer = get_session("buyer@vietspecialty.vn", "Password123@")
res = buyer.get("http://localhost:8080/checkout?systemVoucherCode=VIETSPECIALTY")
print("Status:", res.status_code)
for line in res.text.split("\n"):
    if any(k in line.lower() for k in ["giảm giá", "voucher", "50.000", "vietspecialty", "tổng thanh toán"]):
        print("  ->", line.strip())
