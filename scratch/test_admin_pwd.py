import requests
import re

s = requests.Session()
r1 = s.get('http://localhost:8080/login')
csrf = re.search(r'name="_csrf" value="([^"]+)"', r1.text).group(1)

for pwd in ['Password123@', 'Admin@123456', 'admin123', 'admin']:
    r2 = s.post('http://localhost:8080/login', data={'email': 'admin@vietspecialty.vn', 'password': pwd, '_csrf': csrf}, allow_redirects=False)
    print(f'pwd {pwd} -> status {r2.status_code}, location: {r2.headers.get("Location")}')
    if r2.headers.get('Location') in ['/admin/dashboard', '/', '/admin']:
        print("FOUND ADMIN PWD:", pwd)
        break
