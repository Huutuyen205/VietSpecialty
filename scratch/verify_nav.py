import os
import re

print("=== VERIFYING NAV-BUG-03 (toast.js in <head> without defer) ===")
with open('src/main/resources/templates/layouts/base.html', encoding='utf-8') as f:
    base_html = f.read()

head_match = re.search(r'<head>([\s\S]*?)</head>', base_html)
if head_match:
    head_content = head_match.group(1)
    for line in head_content.splitlines():
        if 'toast.js' in line:
            print(f"Script tag: {line.strip()}")

with open('src/main/resources/static/js/core/toast.js', encoding='utf-8') as f:
    toast_js = f.read()
lines = toast_js.splitlines()
for i in range(min(15, len(lines))):
    print(f"toast.js line {i+1}: {lines[i]}")
print("toast.js last 5 lines:")
for line in lines[-5:]:
    print(line)

print("\n=== VERIFYING NAV-BUG-04 (product-detail.html Alpine x-data syntax error) ===")
with open('src/main/resources/templates/web/product-detail.html', encoding='utf-8') as f:
    prod_html = f.read()
if "[[${product.id}]]" in prod_html:
    print("Found [[${product.id}]] in product-detail.html:")
    for i, line in enumerate(prod_html.splitlines(), start=1):
        if "[[${product.id}]]" in line:
            print(f"Line {i}: {line}")

print("\n=== VERIFYING NAV-BUG-05 (/vendor/chart.js vs /vendor/chart.min.js) ===")
vendor_files = os.listdir('src/main/resources/static/vendor')
print("Vendor files:", vendor_files)
with open('src/main/resources/templates/layouts/admin-base.html', encoding='utf-8') as f:
    admin_base = f.read()
for line in admin_base.splitlines():
    if 'chart' in line.lower():
        print("admin-base.html chart import:", line.strip())

with open('src/main/resources/templates/layouts/seller-base.html', encoding='utf-8') as f:
    seller_base = f.read()
for line in seller_base.splitlines():
    if 'chart' in line.lower():
        print("seller-base.html chart import:", line.strip())

print("\n=== VERIFYING NAV-BUG-08 (Title duplicated suffix) ===")
import requests
r = requests.get('http://localhost:8080/')
title_m = re.search(r'<title>(.*?)</title>', r.text)
if title_m:
    print(f"Homepage title: {title_m.group(1)}")
r_prod = requests.get('http://localhost:8080/products')
title_m2 = re.search(r'<title>(.*?)</title>', r_prod.text)
if title_m2:
    print(f"Products title: {title_m2.group(1)}")
