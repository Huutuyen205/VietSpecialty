import requests
import re

BASE_URL = "http://localhost:8080"
s_admin = requests.Session()

# Login admin
login_page = s_admin.get(f"{BASE_URL}/login")
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page.text).group(1)
s_admin.post(f"{BASE_URL}/login", data={
    "_csrf": csrf,
    "email": "admin@vietspecialty.vn",
    "password": "Admin@123456"
})

res_vouchers = s_admin.get(f"{BASE_URL}/admin/vouchers")
print("Admin vouchers page status:", res_vouchers.status_code)
# Extract voucher codes and info
voucher_codes = re.findall(r'<span class="font-mono[^>]*>([^<]+)</span>', res_vouchers.text)
print("Voucher codes found in admin:", voucher_codes)
if not voucher_codes:
    # Let's inspect the table or content
    print(res_vouchers.text[:1500])
