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
    time.sleep(0.5)
    driver.execute_script("""
        const f = document.querySelector('form[action*="/logout"]');
        if (f) f.submit();
    """)
    time.sleep(0.5)

results = {}

# 1. Admin at 768px & 1440px
login('admin@vietspecialty.vn', 'Admin@123456')

for w, h in [(768, 1024), (1440, 900)]:
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': w,
        'height': h,
        'deviceScaleFactor': 1,
        'mobile': False
    })
    driver.get('http://localhost:8080/admin/dashboard')
    time.sleep(0.5)
    
    info = driver.execute_script("""
        const aside = document.querySelector('aside');
        const toggleBtn = document.querySelector('header button[aria-label="Mở thanh điều hướng"]');
        const asideRect = aside ? aside.getBoundingClientRect() : null;
        const asideStyle = aside ? window.getComputedStyle(aside) : null;
        return {
            windowWidth: window.innerWidth,
            toggleBtnVisible: toggleBtn ? (toggleBtn.offsetParent !== null) : false,
            asideVisible: asideRect ? (asideRect.left >= 0 && asideRect.width > 0) : false,
            asidePosition: asideStyle ? asideStyle.position : null,
            asideLeft: asideRect ? asideRect.left : null,
            asideWidth: asideRect ? asideRect.width : null
        };
    """)
    
    # If 768px, test opening drawer
    if w == 768:
        toggle = driver.find_elements(By.CSS_SELECTOR, 'header button[aria-label="Mở thanh điều hướng"]')
        drawer_opened = False
        if toggle and toggle[0].is_displayed():
            driver.execute_script("arguments[0].click();", toggle[0])
            time.sleep(0.5)
            drawer_opened = driver.execute_script("""
                const aside = document.querySelector('aside');
                const rect = aside.getBoundingClientRect();
                return rect.left >= 0 && rect.width > 0;
            """)
        info['drawerOpenedAfterClick'] = drawer_opened
        
    results[f"admin_{w}"] = info

logout()

# 2. Seller at 768px & 1440px
login('seller_bac@vietspecialty.vn', 'Password123@')

for w, h in [(768, 1024), (1440, 900)]:
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': w,
        'height': h,
        'deviceScaleFactor': 1,
        'mobile': False
    })
    driver.get('http://localhost:8080/seller/dashboard')
    time.sleep(0.5)
    
    info = driver.execute_script("""
        const aside = document.querySelector('aside');
        const toggleBtn = document.querySelector('header button');
        const asideStyle = aside ? window.getComputedStyle(aside) : null;
        const asideRect = aside ? aside.getBoundingClientRect() : null;
        return {
            windowWidth: window.innerWidth,
            toggleBtnVisible: toggleBtn ? (toggleBtn.offsetParent !== null) : false,
            asideVisible: asideStyle ? (asideStyle.display !== 'none') : false,
            asidePosition: asideStyle ? asideStyle.position : null,
            asideRect: asideRect ? { left: asideRect.left, width: asideRect.width } : null
        };
    """)
    
    # If 768px, test opening drawer
    if w == 768:
        toggle = driver.find_elements(By.CSS_SELECTOR, 'header button')
        drawer_opened = False
        if toggle and toggle[0].is_displayed():
            driver.execute_script("arguments[0].click();", toggle[0])
            time.sleep(0.5)
            drawer_opened = driver.execute_script("""
                const aside = document.querySelector('aside');
                const style = window.getComputedStyle(aside);
                return style.display !== 'none';
            """)
        info['drawerOpenedAfterClick'] = drawer_opened
        
    results[f"seller_{w}"] = info

logout()

# 3. Buyer empty cart at 375, 768, 1440
login('buyer@vietspecialty.vn', 'Password123@')
for w, h in [(375, 812), (768, 1024), (1440, 900)]:
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': w,
        'height': h,
        'deviceScaleFactor': 1,
        'mobile': (w < 768)
    })
    driver.get('http://localhost:8080/cart')
    time.sleep(0.5)
    cart_info = driver.execute_script("""
        const h2 = document.querySelector('h2');
        const cta = document.querySelector('a[href="/products"]');
        return {
            url: window.location.href,
            h2Text: h2 ? h2.innerText.trim() : null,
            hasCta: !!cta,
            ctaText: cta ? cta.innerText.trim() : null,
            overflow: document.documentElement.scrollWidth > window.innerWidth
        };
    """)
    results[f"cart_{w}"] = cart_info

print(json.dumps(results, indent=2))
driver.quit()
