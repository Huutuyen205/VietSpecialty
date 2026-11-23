import requests

BASE_URL = 'http://localhost:8080'

print("=== VERIFYING PROD-BUG-01 (/regions) ===")
try:
    r = requests.get(f'{BASE_URL}/regions', timeout=5)
    print("Status:", r.status_code, "Content length:", len(r.content))
except Exception as e:
    print("Caught exception on /regions:", type(e), e)

# Test with raw socket or curl-like check
import urllib.request
try:
    with urllib.request.urlopen(f'{BASE_URL}/regions') as resp:
        data = resp.read()
        print("urllib read:", len(data))
except Exception as e:
    print("urllib exception on /regions:", type(e), e)

print("\n=== VERIFYING PROD-BUG-02 (/provinces/thai-nguyen) ===")
try:
    r = requests.get(f'{BASE_URL}/provinces/thai-nguyen', timeout=5)
    print("Status:", r.status_code, "Content length:", len(r.content))
except Exception as e:
    print("Caught exception on /provinces/thai-nguyen:", type(e), e)

try:
    with urllib.request.urlopen(f'{BASE_URL}/provinces/thai-nguyen') as resp:
        data = resp.read()
        print("urllib read:", len(data))
except Exception as e:
    print("urllib exception on /provinces/thai-nguyen:", type(e), e)

print("\n=== VERIFYING PROD-BUG-03 (/products?keyword=Tra) ===")
r = requests.get(f'{BASE_URL}/products?keyword=Tra')
print("Status:", r.status_code)
# Check if products found or 0 results
print("Contains 'Không tìm thấy đặc sản phù hợp':", "Không tìm thấy đặc sản phù hợp" in r.text)

print("\n=== VERIFYING PROD-BUG-04 (Autocomplete /api/products/suggest) ===")
r_suggest_keyword = requests.get(f'{BASE_URL}/api/products/suggest?keyword=Tra')
print("Suggest with ?keyword=Tra:", r_suggest_keyword.status_code, r_suggest_keyword.text)
r_suggest_q = requests.get(f'{BASE_URL}/api/products/suggest?q=Tra')
print("Suggest with ?q=Tra:", r_suggest_q.status_code, r_suggest_q.text)
