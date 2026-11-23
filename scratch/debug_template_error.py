from test_cart_checkout import get_session

buyer = get_session("buyer@vietspecialty.vn", "Password123@")
res = buyer.get("http://localhost:8080/orders/VS261118TPUKNM", headers={"Accept-Encoding": "identity"}, stream=True)
print("Status code:", res.status_code)
print("Headers:", res.headers)
try:
    chunks = []
    while True:
        chunk = res.raw.read(1024)
        if not chunk:
            break
        chunks.append(chunk)
except Exception as e:
    print("Stopped reading due to:", e)

raw_data = b"".join(chunks).decode('utf-8', errors='ignore')
print("Total bytes read:", len(raw_data))
print("Last 1000 characters read:")
print(raw_data[-1000:])
