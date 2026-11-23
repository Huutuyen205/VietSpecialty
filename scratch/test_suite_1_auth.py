import requests
import re
import urllib.parse

BASE_URL = "http://localhost:8080"

def get_csrf(session, url):
    res = session.get(url)
    m = re.search(r'name="_csrf"\s+value="([^"]+)"', res.text)
    if not m:
        m = re.search(r'content="([^"]+)"\s+name="_csrf"', res.text)
    return m.group(1) if m else None

def test_login():
    print("=== TESTING LOGIN ===")
    s = requests.Session()
    
    # 1.1 Để trống email & password
    csrf = get_csrf(s, f"{BASE_URL}/login")
    res = s.post(f"{BASE_URL}/login", data={
        "_csrf": csrf,
        "email": "",
        "password": ""
    }, allow_redirects=True)
    print("1.1 Login empty fields:")
    print("Status code:", res.status_code, "Final URL:", res.url)
    print("Contains error message:", "Email hoặc mật khẩu không chính xác" in res.text or "errorMessage" in res.text or "error" in res.url)
    
    # 1.2 Sai email format
    csrf = get_csrf(s, f"{BASE_URL}/login")
    res = s.post(f"{BASE_URL}/login", data={
        "_csrf": csrf,
        "email": "not-an-email",
        "password": "Password123@"
    }, allow_redirects=True)
    print("1.2 Login invalid email format:")
    print("Status code:", res.status_code, "Final URL:", res.url)
    print("Error in page:", "Email hoặc mật khẩu không chính xác" in res.text or "error" in res.url)

    # 1.3 Mật khẩu quá ngắn / sai mật khẩu
    csrf = get_csrf(s, f"{BASE_URL}/login")
    res = s.post(f"{BASE_URL}/login", data={
        "_csrf": csrf,
        "email": "buyer@vietspecialty.vn",
        "password": "123"
    }, allow_redirects=True)
    print("1.3 Login short/wrong password:")
    print("Status code:", res.status_code, "Final URL:", res.url)
    print("Error in page:", "Email hoặc mật khẩu không chính xác" in res.text or "error" in res.url)

    # 1.4 Ký tự đặc biệt (SQLi / XSS)
    csrf = get_csrf(s, f"{BASE_URL}/login")
    res = s.post(f"{BASE_URL}/login", data={
        "_csrf": csrf,
        "email": "' OR '1'='1' -- <script>alert(1)</script>",
        "password": "' OR '1'='1'"
    }, allow_redirects=True)
    print("1.4 Login special characters / SQLi:")
    print("Status code:", res.status_code, "Final URL:", res.url)
    print("Is XSS executed or escaped?:", "<script>alert(1)</script>" in res.text)
    # Check if encoded in input value
    m_val = re.search(r'name="email"[^>]*value="([^"]*)"', res.text)
    print("Email input value reflected:", m_val.group(1) if m_val else "Not found")

    # 1.5 Submit liên tiếp 2 lần cùng 1 form/CSRF
    csrf = get_csrf(s, f"{BASE_URL}/login")
    res1 = s.post(f"{BASE_URL}/login", data={
        "_csrf": csrf,
        "email": "buyer@vietspecialty.vn",
        "password": "Password123@"
    }, allow_redirects=False)
    print("1.5 Double submit login 1st request:", res1.status_code, res1.headers.get("Location"))
    res2 = s.post(f"{BASE_URL}/login", data={
        "_csrf": csrf,
        "email": "buyer@vietspecialty.vn",
        "password": "Password123@"
    }, allow_redirects=False)
    print("1.5 Double submit login 2nd request with same session/CSRF:", res2.status_code, res2.headers.get("Location"))

def test_register():
    print("\n=== TESTING REGISTER ===")
    s = requests.Session()

    # 2.1 Để trống tất cả các trường
    csrf = get_csrf(s, f"{BASE_URL}/register")
    res = s.post(f"{BASE_URL}/register", data={
        "_csrf": csrf,
        "fullName": "",
        "email": "",
        "phone": "",
        "password": "",
        "confirmPassword": ""
    }, allow_redirects=True)
    print("2.1 Register empty fields:")
    print("Status code:", res.status_code, "URL:", res.url)
    errors = re.findall(r'class="text-xs text-danger mt-1">([^<]+)<', res.text)
    print("Field errors found:", errors)

    # 2.2 Sai format email
    csrf = get_csrf(s, f"{BASE_URL}/register")
    res = s.post(f"{BASE_URL}/register", data={
        "_csrf": csrf,
        "fullName": "Test User",
        "email": "invalid-email-format",
        "phone": "0987654321",
        "password": "Password123@",
        "confirmPassword": "Password123@"
    }, allow_redirects=True)
    print("2.2 Register invalid email format:")
    errors = re.findall(r'class="text-xs text-danger mt-1">([^<]+)<', res.text)
    print("Field errors found:", errors)

    # 2.3 Mật khẩu quá ngắn (< 8 ký tự)
    csrf = get_csrf(s, f"{BASE_URL}/register")
    res = s.post(f"{BASE_URL}/register", data={
        "_csrf": csrf,
        "fullName": "Test User",
        "email": "testshortpwd@example.com",
        "phone": "0987654321",
        "password": "123",
        "confirmPassword": "123"
    }, allow_redirects=True)
    print("2.3 Register short password (3 chars):")
    errors = re.findall(r'class="text-xs text-danger mt-1">([^<]+)<', res.text)
    print("Field errors found:", errors)

    # 2.4 Ký tự đặc biệt / XSS trong fullName
    csrf = get_csrf(s, f"{BASE_URL}/register")
    res = s.post(f"{BASE_URL}/register", data={
        "_csrf": csrf,
        "fullName": "<script>alert('XSS')</script>",
        "email": "xss_test_1@example.com",
        "phone": "0987654321",
        "password": "Password123@",
        "confirmPassword": "Password123@"
    }, allow_redirects=False)
    print("2.4 Register with XSS in fullName:")
    print("Status code:", res.status_code, "Location:", res.headers.get("Location"))
    if res.status_code == 200:
        errors = re.findall(r'class="text-xs text-danger mt-1">([^<]+)<', res.text)
        print("Field errors found:", errors)
        print("Raw unescaped script tag present in response?", "<script>alert('XSS')</script>" in res.text)

    # 2.5 Submit liên tiếp 2 lần cùng một email
    import time
    test_email = f"duplicate_test_{int(time.time())}@example.com"
    csrf = get_csrf(s, f"{BASE_URL}/register")
    reg_data = {
        "_csrf": csrf,
        "fullName": "Double Submit User",
        "email": test_email,
        "phone": "0912345678",
        "password": "Password123@",
        "confirmPassword": "Password123@"
    }
    res1 = s.post(f"{BASE_URL}/register", data=reg_data, allow_redirects=False)
    print("2.5 Double submit registration (1st):", res1.status_code, res1.headers.get("Location"))
    
    # Gửi lại ngay lập tức lần 2 cùng form data
    res2 = s.post(f"{BASE_URL}/register", data=reg_data, allow_redirects=False)
    print("2.5 Double submit registration (2nd):", res2.status_code, res2.headers.get("Location"))
    if res2.status_code == 200:
        errors = re.findall(r'class="text-xs text-danger mt-1">([^<]+)<', res2.text)
        print("2nd submit errors:", errors)

if __name__ == "__main__":
    test_login()
    test_register()
