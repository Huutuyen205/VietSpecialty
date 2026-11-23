import sys
sys.path.append('d:/VietSpecialty/scratch')
from test_cart_checkout import get_session
import subprocess
import requests

BASE_URL = "http://localhost:8080"

buyer = get_session("buyer@vietspecialty.vn", "Password123@")
seller = get_session("seller_bac@vietspecialty.vn", "Password123@")

# Lấy 1 đơn hàng của buyer thuộc shop 77 (HTX Trà Tân Cương - của seller_bac)
cmd = [
    'mysql', '-u', 'root', '-pHuutuyen123@', '-D', 'vietspecialty', '-N', '-e',
    "SELECT id, code, status, shop_id FROM orders WHERE user_id = 140 AND shop_id = 77 ORDER BY id DESC LIMIT 1;"
]
out = subprocess.run(cmd, capture_output=True, text=True).stdout.strip().split()
print("Order found:", out)
if not out:
    print("No order found, let's create one first!")
    sys.exit(1)

order_id = out[0]
order_code = out[1]
order_status = out[2]
shop_id = out[3]

print(f"Testing Order ID={order_id}, Code={order_code}, Status={order_status}, Shop={shop_id}")

# 1. Thử xác nhận khi status không phải SHIPPING
rec_premature = buyer.post(f"{BASE_URL}/api/orders/{order_id}/received")
print(f"Premature confirm received: Status={rec_premature.status_code}, Body={rec_premature.text}")

# 2. Seller xác nhận đơn nếu đang PENDING_CONFIRM
if order_status == "PENDING_CONFIRM":
    seller_conf = seller.post(f"{BASE_URL}/seller/orders/{order_id}/confirm", allow_redirects=False)
    print(f"Seller confirm: Status={seller_conf.status_code}, Location={seller_conf.headers.get('Location')}")

# 3. Seller giao hàng (ship)
seller_ship = seller.post(f"{BASE_URL}/seller/orders/{order_id}/ship", data={
    'carrierName': 'Giao Hàng Tiết Kiệm',
    'trackingCode': 'GHTK123456789'
}, allow_redirects=False)
print(f"Seller ship: Status={seller_ship.status_code}, Location={seller_ship.headers.get('Location')}")

# 4. Kiểm tra trạng thái đơn trong database
status_check = subprocess.run([
    'mysql', '-u', 'root', '-pHuutuyen123@', '-D', 'vietspecialty', '-N', '-e',
    f"SELECT status FROM orders WHERE id = {order_id};"
], capture_output=True, text=True).stdout.strip()
print(f"Status after seller ship: {status_check}")

# 5. Buyer bấm xác nhận đã nhận hàng
rec_success = buyer.post(f"{BASE_URL}/api/orders/{order_id}/received")
print(f"Buyer confirm received: Status={rec_success.status_code}, Body={rec_success.text}")

# 6. Kiểm tra trạng thái cuối cùng trong database
final_status = subprocess.run([
    'mysql', '-u', 'root', '-pHuutuyen123@', '-D', 'vietspecialty', '-N', '-e',
    f"SELECT status FROM orders WHERE id = {order_id};"
], capture_output=True, text=True).stdout.strip()
print(f"Final Order Status in DB: {final_status}")
