import requests
import re

BASE_URL = "http://localhost:8080"
s = requests.Session()

# Login admin
login_page = s.get(f"{BASE_URL}/login")
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page.text).group(1)
res_login = s.post(f"{BASE_URL}/login", data={
    "_csrf": csrf,
    "email": "admin@vietspecialty.vn",
    "password": "Password123@"
}, allow_redirects=True)
print("Admin login URL:", res_login.url)

# 6.1 Check URL /admin/categories/create
print("\n=== 6.1 CHECKING /admin/categories/create ROUTE ===")
res_get_create = s.get(f"{BASE_URL}/admin/categories/create")
print("GET /admin/categories/create status:", res_get_create.status_code)
csrf_admin = re.search(r'name="_csrf"\s+value="([^"]+)"', res_get_create.text)
token_admin = csrf_admin.group(1) if csrf_admin else csrf

res_post_create = s.post(f"{BASE_URL}/admin/categories/create", data={
    "_csrf": token_admin,
    "name": "Test Cat Route"
})
print("POST /admin/categories/create status:", res_post_create.status_code)

# 6.2 Check actual form on /admin/categories
print("\n=== 6.2 CHECKING /admin/categories FORM ===")
res_cats = s.get(f"{BASE_URL}/admin/categories")
print("GET /admin/categories status:", res_cats.status_code)
csrf_cat = re.search(r'name="_csrf"\s+value="([^"]+)"', res_cats.text).group(1)

# Helper function to submit category create
def create_cat(name, desc=None, icon=None):
    data = {
        "_csrf": csrf_cat,
        "name": name
    }
    if desc is not None:
        data["description"] = desc
    if icon is not None:
        data["icon"] = icon
    res = s.post(f"{BASE_URL}/admin/categories", data=data, allow_redirects=True)
    return res

# 6.3 Để trống tên danh mục
print("\n=== 6.3 EMPTY CATEGORY NAME ===")
res_empty_cat = create_cat("")
print("Empty cat name status:", res_empty_cat.status_code)
print("Error in page:", "Tên danh mục không được để trống" in res_empty_cat.text)

# 6.4 Trùng tên (Duplicate Name) & Slug tự sinh
print("\n=== 6.4 DUPLICATE NAME & SLUG AUTO-GENERATION ===")
test_name = "Đặc Sản Rừng Tây Bắc"
# Tạo lần 1
res_cat1 = create_cat(test_name, "Mô tả lần 1", "mountain")
print("Create cat 1st time status:", res_cat1.status_code)
print("Success message in page:", "Đã thêm danh mục đặc sản mới thành công!" in res_cat1.text)
# Tìm slug sinh ra cho lần 1
slugs_1 = re.findall(r'<td class="[^"]*font-mono[^"]*">([^<]+)</td>', res_cat1.text)
print("All slugs after 1st create:", slugs_1)

# Tạo lần 2 TRÙNG TÊN "Đặc Sản Rừng Tây Bắc"
res_cat2 = create_cat(test_name, "Mô tả lần 2 trùng tên", "tree")
print("\nCreate cat 2nd time (duplicate name) status:", res_cat2.status_code)
print("Success message in page:", "Đã thêm danh mục đặc sản mới thành công!" in res_cat2.text)
print("Does system allow duplicate category names or reject with error?")
slugs_2 = re.findall(r'<td class="[^"]*font-mono[^"]*">([^<]+)</td>', res_cat2.text)
print("All slugs after 2nd create:", slugs_2)

# 6.5 Icon Font / Lucide (Valid, Default, Invalid/XSS)
print("\n=== 6.5 ICON TESTING ===")
# Default icon (khi để trống)
res_icon_empty = create_cat("Mật Ong Rừng Test", "Mô tả", "")
m_icon = re.search(r'data-lucide="([^"]+)"', res_icon_empty.text)
print("Icon when left blank:", re.findall(r'<i data-lucide="([^"]+)"', res_icon_empty.text)[-5:])

# Valid Lucide icon (e.g. "coffee", "fish", "sparkles")
res_icon_valid = create_cat("Cà Phê Rang Xay Test", "Mô tả", "coffee")
print("Valid icon coffee in page:", 'data-lucide="coffee"' in res_icon_valid.text)

# Invalid / arbitrary string icon (e.g. "invalid-icon-xyz-123")
res_icon_invalid = create_cat("Danh Mục Icon Lạ", "Mô tả", "invalid-icon-xyz-123")
print("Invalid icon status:", res_icon_invalid.status_code)
print("Invalid icon rendered as:", 'data-lucide="invalid-icon-xyz-123"' in res_icon_invalid.text)

# XSS in icon name
res_icon_xss = create_cat("Danh Mục Icon XSS", "Mô tả", '"><script>alert("icon_xss")</script>')
print("Icon with XSS payload status:", res_icon_xss.status_code)
if '<script>alert("icon_xss")</script>' in res_icon_xss.text:
    print("CRITICAL: Stored XSS in Lucide icon attribute!")
else:
    print("Icon attribute is safely escaped by Thymeleaf th:data-lucide")
    m_xss = re.search(r'data-lucide="([^"]*script[^"]*)"', res_icon_xss.text)
    if m_xss:
        print("Escaped icon attribute:", m_xss.group(1))

