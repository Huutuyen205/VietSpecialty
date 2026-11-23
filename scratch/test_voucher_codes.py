import sys
sys.path.append('d:/VietSpecialty/scratch')
from test_cart_checkout import get_session

buyer = get_session("buyer@vietspecialty.vn", "Password123@")

for v in ["", "VIETSPECIALTY", "FREESHIP30", "FAKE", "TANCUONG10"]:
    url = f"http://localhost:8080/checkout" + (f"?systemVoucherCode={v}" if v else "")
    res = buyer.get(url)
    print(f"Voucher '{v}': status={res.status_code}")
