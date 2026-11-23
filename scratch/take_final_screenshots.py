from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
import json
import time

options = Options()
options.add_argument('--headless=new')
driver = webdriver.Chrome(options=options)

def login(email, password):
    driver.get('http://localhost:8080/login')
    time.sleep(0.5)
    driver.find_element(By.NAME, 'email').clear()
    driver.find_element(By.NAME, 'email').send_keys(email)
    driver.find_element(By.NAME, 'password').clear()
    driver.find_element(By.NAME, 'password').send_keys(password)
    driver.find_element(By.CSS_SELECTOR, 'form[action*="/login"] button[type="submit"]').click()
    time.sleep(1)

def logout():
    driver.get('http://localhost:8080/logout')
    time.sleep(0.3)
    driver.execute_script("""
        const f = document.querySelector('form[action*="/logout"]');
        if (f) f.submit();
    """)
    time.sleep(0.5)

# Capture Showroom /dev/ui
for w in [375, 768, 1440]:
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': w,
        'height': 900,
        'deviceScaleFactor': 1,
        'mobile': (w < 768)
    })
    driver.get('http://localhost:8080/dev/ui')
    time.sleep(0.5)
    driver.save_screenshot(f'scratch/screenshots/showroom_{w}.png')

# Capture 404
for w in [375, 768, 1440]:
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': w,
        'height': 800,
        'deviceScaleFactor': 1,
        'mobile': (w < 768)
    })
    driver.get('http://localhost:8080/non-existent-page-404')
    time.sleep(0.5)
    driver.save_screenshot(f'scratch/screenshots/page_404_{w}.png')

# Capture 500
for w in [375, 768, 1440]:
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': w,
        'height': 800,
        'deviceScaleFactor': 1,
        'mobile': (w < 768)
    })
    driver.get('http://localhost:8080/error')
    time.sleep(0.5)
    driver.save_screenshot(f'scratch/screenshots/page_500_{w}.png')

# Capture Empty Cart (logged in as buyer)
login('buyer@vietspecialty.vn', 'Password123@')
for w in [375, 768, 1440]:
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': w,
        'height': 800,
        'deviceScaleFactor': 1,
        'mobile': (w < 768)
    })
    driver.get('http://localhost:8080/cart')
    time.sleep(0.5)
    driver.save_screenshot(f'scratch/screenshots/cart_empty_{w}.png')
logout()

# Capture Admin Dashboard (logged in as admin)
login('admin@vietspecialty.vn', 'Password123@')
for w in [768, 1440]:
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': w,
        'height': 900,
        'deviceScaleFactor': 1,
        'mobile': False
    })
    driver.get('http://localhost:8080/admin/dashboard')
    time.sleep(0.5)
    driver.save_screenshot(f'scratch/screenshots/admin_dashboard_{w}.png')
    if w == 768:
        # open drawer
        btn = driver.find_element(By.CSS_SELECTOR, 'header button[aria-label="Mở thanh điều hướng"]')
        driver.execute_script("arguments[0].click();", btn)
        time.sleep(0.5)
        driver.save_screenshot(f'scratch/screenshots/admin_drawer_{w}.png')
logout()

# Capture Seller Dashboard (logged in as seller)
login('seller_bac@vietspecialty.vn', 'Password123@')
for w in [768, 1440]:
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': w,
        'height': 900,
        'deviceScaleFactor': 1,
        'mobile': False
    })
    driver.get('http://localhost:8080/seller/dashboard')
    time.sleep(0.5)
    driver.save_screenshot(f'scratch/screenshots/seller_dashboard_{w}.png')
    if w == 768:
        btn = driver.find_element(By.CSS_SELECTOR, 'header button')
        driver.execute_script("arguments[0].click();", btn)
        time.sleep(0.5)
        driver.save_screenshot(f'scratch/screenshots/seller_drawer_{w}.png')
logout()

driver.quit()
print("All final screenshots captured successfully!")
