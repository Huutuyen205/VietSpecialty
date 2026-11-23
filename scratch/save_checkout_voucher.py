import sys
sys.path.append('d:/VietSpecialty/scratch')
from test_cart_checkout import get_session

buyer = get_session("buyer@vietspecialty.vn", "Password123@")
res = buyer.get("http://localhost:8080/checkout?systemVoucherCode=VIETSPECIALTY")
print("Status code:", res.status_code)
print("URL:", res.url)
with open("d:/VietSpecialty/scratch/checkout_voucher.html", "w", encoding="utf-8") as f:
    f.write(res.text)
print("Saved d:/VietSpecialty/scratch/checkout_voucher.html, size:", len(res.text))
