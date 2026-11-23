from test_cart_checkout import get_session

buyer = get_session("buyer@vietspecialty.vn", "Password123@")
res = buyer.get("http://localhost:8080/orders/VS261118TPUKNM", stream=True)
print("Status code:", res.status_code)
print("Headers:", res.headers)
try:
    content = res.raw.read()
    print("Raw content length:", len(content))
    print("Content preview:", content[:500].decode('utf-8', errors='ignore'))
    print("Content end:", content[-500:].decode('utf-8', errors='ignore'))
except Exception as e:
    print("Read exception:", e)
