from test_cart_checkout import get_session
import re

buyer = get_session("buyer@vietspecialty.vn", "Password123@")
res = buyer.get("http://localhost:8080/checkout?systemVoucherCode=VIETSPECIALTY")
print("Status code:", res.status_code)
# Let's extract any error message or stack trace in 500 page
print("Text length:", len(res.text))
for line in res.text.split("\n"):
    if any(k in line.lower() for k in ["exception", "error", "cause", "message", "trace", "status"]):
        print("  ->", line.strip()[:200])
