import requests
import re

s = requests.Session()
p = s.get('http://localhost:8080/login')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', p.text).group(1)
r = s.post('http://localhost:8080/login', data={'_csrf': csrf, 'email': 'admin@vietspecialty.vn', 'password': 'Admin@123456'}, allow_redirects=False)
print("Login response:", r.status_code, r.headers.get('Location'))
r_admin = s.get('http://localhost:8080/admin/categories')
print("Admin categories status:", r_admin.status_code, "URL:", r_admin.url)
