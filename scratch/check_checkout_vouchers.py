from test_cart_checkout import get_session
import re

buyer = get_session("buyer@vietspecialty.vn", "Password123@")
res = buyer.get("http://localhost:8080/checkout")
print("Checkout status:", res.status_code)
# Find voucher codes or info in checkout html
vouchers = re.findall(r'([A-Z0-9_-]{4,20})', res.text)
# Look for systemVouchers in page
print("Occurrences of voucher in page:")
for line in res.text.split("\n"):
    if "voucher" in line.lower() or "giảm giá" in line.lower():
        print(line.strip())
