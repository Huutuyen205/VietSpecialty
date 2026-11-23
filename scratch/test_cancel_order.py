import sys
sys.path.append('d:/VietSpecialty/scratch')
from test_cart_checkout import get_session
import subprocess

buyer = get_session("buyer@vietspecialty.vn", "Password123@")

# Check stock of 59 and voucher usedCount before cancel
cmd = [
    'mysql', '-u', 'root', '-pHuutuyen123@', '-D', 'vietspecialty', '-e',
    "SELECT id, stock FROM products WHERE id = 59; SELECT id, code, used_count FROM vouchers WHERE code IN ('VIETSPECIALTY', 'TANCUONG10'); SELECT id, code, status FROM orders WHERE id = 314;"
]
print("--- BEFORE CANCEL ---")
print(subprocess.run(cmd, capture_output=True, text=True).stdout)

# Cancel order 314
res_cancel = buyer.post("http://localhost:8080/api/orders/314/cancel", json={"reason": "Người mua đổi ý"})
print("Cancel response:", res_cancel.status_code, res_cancel.text)

print("--- AFTER CANCEL ---")
print(subprocess.run(cmd, capture_output=True, text=True).stdout)
