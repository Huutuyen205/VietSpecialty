import sys
sys.path.append('d:/VietSpecialty/scratch')
from test_cart_checkout import get_session

buyer = get_session("buyer@vietspecialty.vn", "Password123@")

# Add product 59 (850.000₫) to cart
r_add = buyer.post("http://localhost:8080/api/cart/items", json={"productId": 59, "quantity": 1})
print("Add product 59:", r_add.status_code, r_add.json().get("success"))

# Check cart
r_cart = buyer.get("http://localhost:8080/api/cart")
print("Total cart value:", r_cart.json().get("data", {}).get("grandTotal"))

# Now test vouchers
for v in ["", "VIETSPECIALTY", "FREESHIP30", "FAKE", "TANCUONG10"]:
    url = f"http://localhost:8080/checkout" + (f"?systemVoucherCode={v}" if v else "")
    res = buyer.get(url)
    print(f"Voucher '{v}': status={res.status_code}")
