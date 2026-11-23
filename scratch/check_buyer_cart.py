import requests
import re

BASE_URL = "http://localhost:8080"
s = requests.Session()

# Login buyer
login_page = s.get(f"{BASE_URL}/login")
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page.text).group(1)
s.post(f"{BASE_URL}/login", data={
    "_csrf": csrf,
    "email": "buyer@vietspecialty.vn",
    "password": "Password123@"
})

# Check cart
res_cart = s.get(f"{BASE_URL}/cart")
print("Cart page status:", res_cart.status_code)
print("Cart empty?", "Giỏ hàng trống" in res_cart.text or "chưa có sản phẩm" in res_cart.text)

# Check checkout
res_checkout = s.get(f"{BASE_URL}/checkout")
print("Checkout page status:", res_checkout.status_code, "URL:", res_checkout.url)
