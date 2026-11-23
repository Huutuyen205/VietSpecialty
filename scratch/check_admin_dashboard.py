from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
import json
import time

options = Options()
options.add_argument('--headless=new')
driver = webdriver.Chrome(options=options)

driver.get('http://localhost:8080/login')
time.sleep(0.5)
driver.find_element(By.NAME, 'email').send_keys('admin@vietspecialty.vn')
driver.find_element(By.NAME, 'password').send_keys('Password123@')
driver.find_element(By.CSS_SELECTOR, 'form[action*="/login"] button[type="submit"]').click()
time.sleep(1)

results = {}

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
            url: window.location.href,
            windowWidth: window.innerWidth,
            toggleBtnVisible: toggleBtn ? (toggleBtn.offsetParent !== null) : false,
            asideVisible: asideRect ? (asideRect.left >= 0 && asideRect.width > 0) : false,
            asidePosition: asideStyle ? asideStyle.position : null,
            asideRect: asideRect ? { left: asideRect.left, right: asideRect.right, width: asideRect.width } : null
        };
    """)
    
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

print(json.dumps(results, indent=2))
driver.quit()
