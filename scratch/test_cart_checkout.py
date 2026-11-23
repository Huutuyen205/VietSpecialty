import requests
import re
import json

BASE_URL = "http://localhost:8080"

def get_session(email=None, password=None):
    s = requests.Session()
    login_page = s.get(f"{BASE_URL}/login")
    csrf_match = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page.text)
    csrf = csrf_match.group(1) if csrf_match else ''
    s.headers.update({'X-CSRF-TOKEN': csrf})
    
    if email and password:
        login_resp = s.post(f"{BASE_URL}/login", data={
            'email': email,
            'password': password,
            '_csrf': csrf
        }, allow_redirects=True)
        # re-extract csrf if session changed
        page = s.get(f"{BASE_URL}/")
        csrf_match = re.search(r'name="_csrf"\s+value="([^"]+)"', page.text)
        if csrf_match:
            s.headers.update({'X-CSRF-TOKEN': csrf_match.group(1)})
    return s

def test_initial_state():
    print("=== TEST INITIAL STATE ===")
    anon = requests.Session()
    res = anon.get(f"{BASE_URL}/api/cart/count")
    print("Anon cart count:", res.status_code, res.json())
    
    res = anon.get(f"{BASE_URL}/api/cart")
    print("Anon cart get:", res.status_code, res.json())
    
    res = anon.get(f"{BASE_URL}/cart", allow_redirects=False)
    print("Anon /cart page status:", res.status_code, "Location:", res.headers.get('Location'))

    res = anon.get(f"{BASE_URL}/checkout", allow_redirects=False)
    print("Anon /checkout page status:", res.status_code, "Location:", res.headers.get('Location'))

    buyer = get_session("buyer@vietspecialty.vn", "Password123@")
    res = buyer.get(f"{BASE_URL}/api/cart/count")
    print("Buyer cart count:", res.status_code, res.json())
    
    res = buyer.get(f"{BASE_URL}/api/cart")
    print("Buyer cart items:", res.status_code, res.json())

if __name__ == "__main__":
    test_initial_state()
