import requests
import re
import urllib.parse

BASE_URL = "http://localhost:8080"

s = requests.Session()

# Login buyer
login_page = s.get(f"{BASE_URL}/login")
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page.text).group(1)
s.post(f"{BASE_URL}/login", data={
    "_csrf": csrf,
    "email": "buyer@vietspecialty.vn",
    "password": "Password123@"
})

print("=== TESTING VOUCHER ON CHECKOUT ===")

# 3.1 Voucher để trống (empty / blank)
res_empty = s.get(f"{BASE_URL}/checkout?systemVoucherCode=")
print("3.1 Empty voucher code (systemVoucherCode=):")
print("Status code:", res_empty.status_code)
print("Is there any error or alert?", "alert" in res_empty.text or "error" in res_empty.text)

# 3.2 Voucher không tồn tại (non-existent code)
res_not_found = s.get(f"{BASE_URL}/checkout?systemVoucherCode=KHONGTONTAI123")
print("\n3.2 Non-existent voucher (systemVoucherCode=KHONGTONTAI123):")
print("Status code:", res_not_found.status_code)
print("Does UI display any error message to user?")
m_err = re.search(r'<div[^>]*class="[^"]*alert[^"]*"[^>]*>(.*?)</div>', res_not_found.text, re.DOTALL)
print("Alert div found:", m_err.group(0) if m_err else "NONE")
m_param_err = re.search(r'th:if="\${param\.error != null}"', res_not_found.text)
print("Contains param.error element:", bool(m_param_err))
# Check if error text is displayed
print("Contains error text in HTML:", "không tồn tại" in res_not_found.text or "không hợp lệ" in res_not_found.text)
# Check voucher input value
m_val = re.search(r'id="voucherInput"[^>]*value="([^"]*)"', res_not_found.text)
print("Voucher input value reflects invalid code:", m_val.group(1) if m_val else "Not found")
# Check discount
m_disc = re.search(r'Giảm giá sàn', res_not_found.text)
print("Discount applied?", bool(m_disc))

# 3.3 Voucher quá hạn (expired code)
# Let's test with a fake expired code or check if an expired code exists
res_expired = s.get(f"{BASE_URL}/checkout?systemVoucherCode=EXPIRED_CODE")
print("\n3.3 Expired voucher code (systemVoucherCode=EXPIRED_CODE):")
print("Status code:", res_expired.status_code)
print("Contains error text in HTML:", "hết hạn" in res_expired.text or "quá hạn" in res_expired.text)

# Now check what happens if user submits place-order with invalid voucher code
print("\n3.4 Place order POST with invalid voucher code:")
checkout_page = s.get(f"{BASE_URL}/checkout")
csrf_token = re.search(r'name="_csrf"\s+value="([^"]+)"', checkout_page.text).group(1)
# Find address id
m_addr = re.search(r'name="addressId"\s+value="(\d+)"', checkout_page.text)
addr_id = m_addr.group(1) if m_addr else "1"

place_data = {
    "_csrf": csrf_token,
    "addressId": addr_id,
    "shippingMethod": "STANDARD",
    "paymentMethod": "COD",
    "systemVoucherCode": "KHONGTONTAI123",
    "buyerNote": "Test note"
}
res_place = s.post(f"{BASE_URL}/checkout/place-order", data=place_data, allow_redirects=False)
print("POST /checkout/place-order status:", res_place.status_code)
print("Redirect location:", res_place.headers.get("Location"))
if res_place.headers.get("Location"):
    redirect_url = res_place.headers.get("Location")
    res_after = s.get(redirect_url)
    print("After redirect page status:", res_after.status_code)
    # Check if error message is shown
    m_msg = re.search(r'<div th:if="\${param\.error != null}"[^>]*>.*?<span>(.*?)</span>', res_after.text, re.DOTALL)
    print("Error message on checkout page:", m_msg.group(1).strip() if m_msg else "Check param: " + redirect_url)

