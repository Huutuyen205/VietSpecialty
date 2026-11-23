import sys
sys.path.append('d:/VietSpecialty/scratch')
from test_cart_checkout import get_session

buyer = get_session("buyer@vietspecialty.vn", "Password123@")
res = buyer.get("http://localhost:8080/api/cart")
data = res.json().get("data", {})
if data and data.get("shopGroups"):
    for sg in data["shopGroups"]:
        for item in sg["items"]:
            buyer.delete(f"http://localhost:8080/api/cart/items/{item['id']}")

cnt = buyer.get("http://localhost:8080/api/cart/count").json().get("data")
print("Buyer cart cleaned, count is:", cnt)
