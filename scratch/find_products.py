import requests
import re

res = requests.get("http://localhost:8080/products")
print("Status:", res.status_code)
# Find products links or onclick quickAddToCart
cards = re.findall(r'quickAddToCart\((\d+)\)', res.text)
print("Products with quickAddToCart:", cards[:10])

slugs = re.findall(r'href="/products/([^"]+)"', res.text)
print("Product slugs:", set(slugs[:10]))
