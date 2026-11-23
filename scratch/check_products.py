import requests
import json

BASE_URL = "http://localhost:8080"

def get_products():
    res = requests.get(f"{BASE_URL}/api/products/suggest?keyword=a")
    print("Suggest products:")
    print(json.dumps(res.json(), indent=2, ensure_ascii=False))

if __name__ == "__main__":
    get_products()
