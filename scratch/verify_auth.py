import requests
import re

s = requests.Session()
r = s.get('http://localhost:8080/login')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', r.text).group(1)

# Test 1: Admin@123456
r1 = s.post('http://localhost:8080/login', data={'email': 'admin@vietspecialty.vn', 'password': 'Admin@123456', '_csrf': csrf}, allow_redirects=False)
print('Admin@123456 status:', r1.status_code, 'location:', r1.headers.get('Location'))

# Test 2: Password123@
r = s.get('http://localhost:8080/login')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', r.text).group(1)
r2 = s.post('http://localhost:8080/login', data={'email': 'admin@vietspecialty.vn', 'password': 'Password123@', '_csrf': csrf}, allow_redirects=False)
print('Password123@ status:', r2.status_code, 'location:', r2.headers.get('Location'))
