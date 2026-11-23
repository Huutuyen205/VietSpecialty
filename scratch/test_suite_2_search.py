import requests
import re
import urllib.parse

BASE_URL = "http://localhost:8080"

def test_search():
    s = requests.Session()
    print("=== TESTING SEARCH FORM & AUTOCOMPLETE ===")

    # 2.1 Keyword để trống
    print("\n--- 2.1 Empty Keyword ---")
    res_empty_web = s.get(f"{BASE_URL}/products?keyword=")
    print("GET /products?keyword= -> Status:", res_empty_web.status_code)
    # Does it return all products or show error?
    m_count = re.findall(r'(\d+)\s+đặc sản', res_empty_web.text)
    print("Result count text:", m_count)
    
    res_empty_api = s.get(f"{BASE_URL}/api/products/suggest?keyword=")
    print("GET /api/products/suggest?keyword= -> Status:", res_empty_api.status_code, "Body:", res_empty_api.text)

    res_spaces_web = s.get(f"{BASE_URL}/products?keyword=   ")
    print("GET /products?keyword=   -> Status:", res_spaces_web.status_code)

    res_spaces_api = s.get(f"{BASE_URL}/api/products/suggest?keyword=   ")
    print("GET /api/products/suggest?keyword=   -> Status:", res_spaces_api.status_code, "Body:", res_spaces_api.text)

    # 2.2 Ký tự HTML / XSS
    print("\n--- 2.2 HTML / XSS payloads ---")
    xss_payloads = [
        "<script>alert(1)</script>",
        "<img src=x onerror=alert('xss')>",
        "'\"><svg/onload=alert(1)>",
        "<a href=\"javascript:alert(1)\">click</a>"
    ]
    for payload in xss_payloads:
        # Web search
        res_web = s.get(f"{BASE_URL}/products", params={"keyword": payload})
        print(f"Web payload [{payload[:20]}...] -> Status: {res_web.status_code}")
        # Check if payload is reflected raw and unescaped
        if payload in res_web.text:
            print("  VULNERABILITY: Raw XSS payload reflected in HTML!")
        else:
            print("  Safe: Payload is escaped or not reflected unescaped.")
            # Check how it's reflected in input or title
            m_input = re.search(r'name="keyword"[^>]*value="([^"]*)"', res_web.text)
            if m_input:
                print("  Reflected in input:", m_input.group(1))

        # API suggest
        res_api = s.get(f"{BASE_URL}/api/products/suggest", params={"keyword": payload})
        print(f"API suggest [{payload[:20]}...] -> Status: {res_api.status_code}, Body length: {len(res_api.text)}")

    # 2.3 Chuỗi quá dài (500 ký tự)
    print("\n--- 2.3 Long string (500 characters) ---")
    long_keyword = "A" * 500
    res_long_web = s.get(f"{BASE_URL}/products", params={"keyword": long_keyword})
    print(f"GET /products with 500 chars -> Status: {res_long_web.status_code}")
    print("Response length:", len(res_long_web.text))
    
    res_long_api = s.get(f"{BASE_URL}/api/products/suggest", params={"keyword": long_keyword})
    print(f"GET /api/products/suggest with 500 chars -> Status: {res_long_api.status_code}")
    print("API Response:", res_long_api.text[:200])

    # 2.4 Chuỗi cực dài (e.g. 5000 ký tự - DoS test / buffer overflow)
    print("\n--- 2.4 Extremely long string (5000 characters) ---")
    ext_keyword = "B" * 5000
    res_ext_web = s.get(f"{BASE_URL}/products", params={"keyword": ext_keyword})
    print(f"GET /products with 5000 chars -> Status: {res_ext_web.status_code}")

    res_ext_api = s.get(f"{BASE_URL}/api/products/suggest", params={"keyword": ext_keyword})
    print(f"GET /api/products/suggest with 5000 chars -> Status: {res_ext_api.status_code}")

if __name__ == "__main__":
    test_search()
