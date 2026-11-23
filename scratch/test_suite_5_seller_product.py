import requests
import re
import io

BASE_URL = "http://localhost:8080"
s = requests.Session()

# Login seller
login_page = s.get(f"{BASE_URL}/login")
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page.text).group(1)
res_login = s.post(f"{BASE_URL}/login", data={
    "_csrf": csrf,
    "email": "seller_bac@vietspecialty.vn",
    "password": "Password123@"
}, allow_redirects=True)
print("Seller login URL:", res_login.url)

# Open create product form
res_form = s.get(f"{BASE_URL}/seller/products/create")
print("Create product page status:", res_form.status_code)
csrf_token = re.search(r'name="_csrf"\s+value="([^"]+)"', res_form.text).group(1)

# Helper function to submit product create form
def submit_product(data, files=None):
    post_data = {"_csrf": csrf_token}
    post_data.update(data)
    if files is None:
        # Default empty or dummy image
        files = {"images": ("test.jpg", io.BytesIO(b"fake image bytes"), "image/jpeg")}
    return s.post(f"{BASE_URL}/seller/products/create", data=post_data, files=files, allow_redirects=False)

def extract_errors(html):
    # Field errors
    field_errors = re.findall(r'<p[^>]*class="[^"]*text-danger[^"]*"[^>]*>(.*?)</p>', html)
    # Alert error
    alert_error = re.search(r'<div[^>]*class="alert alert-error"[^>]*>.*?<span>(.*?)</span>', html, re.DOTALL)
    alert_text = alert_error.group(1).strip() if alert_error else None
    return [e.strip() for e in field_errors if e.strip()], alert_text

print("\n=== 5.1 THIẾU TRƯỜNG BẮT BUỘC ===")
# Submit completely empty form
res_empty = submit_product({
    "name": "",
    "categoryId": "",
    "provinceId": "",
    "originDetail": "",
    "shortDescription": "",
    "description": "",
    "price": "",
    "stock": "",
    "unit": "",
    "weightGram": "",
    "shelfLife": "",
    "storageInstructions": "",
    "ocopRating": ""
}, files={"images": ("", io.BytesIO(b""), "application/octet-stream")})
print("Empty form status:", res_empty.status_code)
field_errors, alert_error = extract_errors(res_empty.text)
print("Field errors found:", field_errors)
print("Alert error:", alert_error)

print("\n=== 5.2 GIÁ ÂM (NEGATIVE PRICE) & GIÁ < 1000 ===")
res_neg_price = submit_product({
    "name": "Đặc sản Hợp Lệ A",
    "categoryId": "1",
    "provinceId": "1",
    "originDetail": "Xã Tân Cương",
    "description": "Mô tả chi tiết sản phẩm hợp lệ dài hơn",
    "price": "-50000",
    "stock": "10",
    "unit": "Hộp",
    "weightGram": "500"
})
print("Negative price status:", res_neg_price.status_code)
field_errors, _ = extract_errors(res_neg_price.text)
print("Negative price errors:", field_errors)

res_low_price = submit_product({
    "name": "Đặc sản Hợp Lệ A",
    "categoryId": "1",
    "provinceId": "1",
    "originDetail": "Xã Tân Cương",
    "description": "Mô tả chi tiết sản phẩm hợp lệ dài hơn",
    "price": "500",
    "stock": "10",
    "unit": "Hộp",
    "weightGram": "500"
})
print("Price < 1000 status:", res_low_price.status_code)
field_errors, _ = extract_errors(res_low_price.text)
print("Price < 1000 errors:", field_errors)

print("\n=== 5.3 TỒN KHO ÂM (NEGATIVE STOCK) ===")
res_neg_stock = submit_product({
    "name": "Đặc sản Hợp Lệ B",
    "categoryId": "1",
    "provinceId": "1",
    "originDetail": "Xã Tân Cương",
    "description": "Mô tả chi tiết sản phẩm hợp lệ dài hơn",
    "price": "150000",
    "stock": "-5",
    "unit": "Hộp",
    "weightGram": "500"
})
print("Negative stock status:", res_neg_stock.status_code)
field_errors, _ = extract_errors(res_neg_stock.text)
print("Negative stock errors:", field_errors)

print("\n=== 5.4 TÊN QUÁ DÀI (> 255 KÝ TỰ) & QUÁ NGẮN (< 5 KÝ TỰ) ===")
long_name = "Tên Sản Phẩm Quá Dài " + ("A" * 300)
res_long_name = submit_product({
    "name": long_name,
    "categoryId": "1",
    "provinceId": "1",
    "originDetail": "Xã Tân Cương",
    "description": "Mô tả chi tiết sản phẩm hợp lệ dài hơn",
    "price": "150000",
    "stock": "10",
    "unit": "Hộp",
    "weightGram": "500"
})
print("Long name (>255) status:", res_long_name.status_code)
field_errors, _ = extract_errors(res_long_name.text)
print("Long name errors:", field_errors)

short_name = "Abc"
res_short_name = submit_product({
    "name": short_name,
    "categoryId": "1",
    "provinceId": "1",
    "originDetail": "Xã Tân Cương",
    "description": "Mô tả chi tiết sản phẩm hợp lệ dài hơn",
    "price": "150000",
    "stock": "10",
    "unit": "Hộp",
    "weightGram": "500"
})
print("Short name (<5) status:", res_short_name.status_code)
field_errors, _ = extract_errors(res_short_name.text)
print("Short name errors:", field_errors)

print("\n=== 5.5 THIẾU ẢNH SẢN PHẨM (NO IMAGES) ===")
res_no_img = submit_product({
    "name": "Đặc sản Hợp Lệ Không Ảnh",
    "categoryId": "1",
    "provinceId": "1",
    "originDetail": "Xã Tân Cương",
    "description": "Mô tả chi tiết sản phẩm hợp lệ dài hơn",
    "price": "150000",
    "stock": "10",
    "unit": "Hộp",
    "weightGram": "500"
}, files={"images": ("", io.BytesIO(b""), "application/octet-stream")})
print("No image status:", res_no_img.status_code)
field_errors, alert_error = extract_errors(res_no_img.text)
print("No image alert error:", alert_error)
