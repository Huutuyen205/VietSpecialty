import requests

s = requests.Session()
# Login buyer
login_page = s.get("http://localhost:8080/login")
import re
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page.text).group(1)
s.post("http://localhost:8080/login", data={
    "_csrf": csrf,
    "email": "buyer@vietspecialty.vn",
    "password": "Password123@"
})

res = s.get("http://localhost:8080/checkout?systemVoucherCode=KHONGTONTAI123")
print("Status code:", res.status_code)
print("Response text preview:")
print(res.text[:2000])
