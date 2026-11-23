from test_cart_checkout import get_session
import subprocess
import os

buyer = get_session('buyer@vietspecialty.vn', 'Password123@')
cookie = buyer.cookies.get_dict()
print('Cookie:', cookie)
jsessionid = cookie.get('JSESSIONID')

cmd = ['curl.exe', '-v', '-b', f'JSESSIONID={jsessionid}', 'http://localhost:8080/orders/VS261118TPUKNM', '-o', 'd:\\VietSpecialty\\scratch\\order_detail.html']
p = subprocess.run(cmd, capture_output=True, text=True)
print('Curl returncode:', p.returncode)
print('Curl stderr:\n', p.stderr)
print('File size:', os.path.getsize('d:\\VietSpecialty\\scratch\\order_detail.html'))
