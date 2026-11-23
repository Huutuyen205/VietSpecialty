import requests
import re

BASE_URL = "http://localhost:8080"
s = requests.Session()

# 4.0 Check unauthenticated comment post
res_unauth = s.post(f"{BASE_URL}/products/1/comments", data={"content": "Hello"}, allow_redirects=False)
print("4.0 Unauthenticated comment POST status:", res_unauth.status_code, res_unauth.headers.get("Location"))

# Login buyer
login_page = s.get(f"{BASE_URL}/login")
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page.text).group(1)
s.post(f"{BASE_URL}/login", data={
    "_csrf": csrf,
    "email": "buyer@vietspecialty.vn",
    "password": "Password123@"
})

# Find a product
res_prod = s.get(f"{BASE_URL}/products")
m_link = re.search(r'href="/products/([^"/]+)"', res_prod.text)
prod_slug = m_link.group(1) if m_link else "tra-tan-cuong-dac-biet"
print(f"Testing on product slug: {prod_slug}")

# Get product detail page to find product ID and CSRF
res_detail = s.get(f"{BASE_URL}/products/{prod_slug}")
m_id = re.search(r'action="/products/(\d+)/comments"', res_detail.text)
if not m_id:
    m_id = re.search(r'/products/(\d+)/comments', res_detail.text)
prod_id = m_id.group(1) if m_id else "1"
print(f"Product ID: {prod_id}")

csrf_token = re.search(r'name="_csrf"\s+value="([^"]+)"', res_detail.text)
csrf_val = csrf_token.group(1) if csrf_token else csrf

# 4.1 Nội dung để trống (empty / blank)
print("\n--- 4.1 Empty content ---")
res_empty = s.post(f"{BASE_URL}/products/{prod_id}/comments", data={
    "_csrf": csrf_val,
    "productSlug": prod_slug,
    "content": ""
}, allow_redirects=True)
print("POST empty content status:", res_empty.status_code, "Final URL:", res_empty.url)
print("Error message in page:", "Nội dung câu hỏi không được để trống" in res_empty.text)

# Also test with whitespace only "   "
res_whitespace = s.post(f"{BASE_URL}/products/{prod_id}/comments", data={
    "_csrf": csrf_val,
    "productSlug": prod_slug,
    "content": "     "
}, allow_redirects=True)
print("POST whitespace content status:", res_whitespace.status_code)
print("Error message in page:", "Nội dung câu hỏi không được để trống" in res_whitespace.text)

# 4.2 Ký tự đặc biệt (XSS payloads, emojis, special characters)
print("\n--- 4.2 Special Characters & XSS ---")
xss_comment = "<script>alert('XSS_COMMENT')</script><b>Bold Question</b> & ? ! # $ % ^ *"
res_xss = s.post(f"{BASE_URL}/products/{prod_id}/comments", data={
    "_csrf": csrf_val,
    "productSlug": prod_slug,
    "content": xss_comment
}, allow_redirects=True)
print("POST XSS content status:", res_xss.status_code, "Final URL:", res_xss.url)
print("Success message in page:", "Câu hỏi của bạn đã được gửi" in res_xss.text)

# Check if the comment is rendered unescaped on the product detail page!
if "<script>alert('XSS_COMMENT')</script>" in res_xss.text:
    print("CRITICAL: Stored XSS vulnerability in Product Comments! Raw script tag rendered in HTML!")
else:
    print("Script tag is escaped or not rendered raw.")
    if "&lt;script&gt;" in res_xss.text:
        print("Confirmed: Script is safely HTML-escaped by Thymeleaf th:text.")

# 4.3 Chuỗi cực dài (e.g. 10000 ký tự)
print("\n--- 4.3 Extremely long comment content ---")
long_comment = "Câu hỏi dài: " + ("A" * 5000)
res_long = s.post(f"{BASE_URL}/products/{prod_id}/comments", data={
    "_csrf": csrf_val,
    "productSlug": prod_slug,
    "content": long_comment
}, allow_redirects=True)
print("POST long comment status:", res_long.status_code)
print("Success or error:", "Câu hỏi của bạn đã được gửi" in res_long.text)

# 4.4 API comment endpoint POST /api/products/{id}/comments
print("\n--- 4.4 API comment endpoint test ---")
api_headers = {"X-CSRF-TOKEN": csrf_val}
res_api_empty = s.post(f"{BASE_URL}/api/products/{prod_id}/comments", data={"content": ""}, headers=api_headers)
print("API empty content status:", res_api_empty.status_code, "Body:", res_api_empty.text)

res_api_valid = s.post(f"{BASE_URL}/api/products/{prod_id}/comments", data={"content": "San pham nay con han su dung bao lau?"}, headers=api_headers)
print("API valid content status:", res_api_valid.status_code, "Body:", res_api_valid.text)
