import requests
import re

BASE_URL = "http://localhost:8080"

s = requests.Session()
# Login as the user registered with <script>
res_login_page = s.get(f"{BASE_URL}/login")
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', res_login_page.text).group(1)
res_login = s.post(f"{BASE_URL}/login", data={
    "_csrf": csrf,
    "email": "xss_test_1@example.com",
    "password": "Password123@"
}, allow_redirects=True)

print("Logged in URL:", res_login.url)

# Check profile page
res_profile = s.get(f"{BASE_URL}/account/profile")
print("Profile page status:", res_profile.status_code)
if "<script>alert('XSS')</script>" in res_profile.text:
    print("WARNING: Raw unescaped script tag found in /account/profile!")
else:
    print("Script tag is escaped or not raw in /account/profile")
    m = re.search(r'value="([^"]*script[^"]*)"', res_profile.text)
    if m:
        print("Escaped attribute value:", m.group(1))

# Now check as Admin viewing /admin/users
s_admin = requests.Session()
res_admin_login_page = s_admin.get(f"{BASE_URL}/login")
csrf_admin = re.search(r'name="_csrf"\s+value="([^"]+)"', res_admin_login_page.text).group(1)
s_admin.post(f"{BASE_URL}/login", data={
    "_csrf": csrf_admin,
    "email": "admin@vietspecialty.vn",
    "password": "Admin@123456"
}, allow_redirects=True)

res_admin_users = s_admin.get(f"{BASE_URL}/admin/users")
print("Admin users page status:", res_admin_users.status_code)
if "<script>alert('XSS')</script>" in res_admin_users.text:
    print("CRITICAL: Raw unescaped script tag found in /admin/users!")
else:
    print("Script tag in /admin/users is properly escaped by Thymeleaf th:text")
