from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json
import time

options = Options()
options.add_argument('--headless=new')
options.add_argument('--window-size=375,812')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/')

hamburger = driver.find_element("css selector", 'button[aria-label="Mở menu"]')
driver.execute_script("arguments[0].click();", hamburger)
time.sleep(0.6)

info = driver.execute_script("""
    const drawer = document.querySelector('.md\\\\:hidden.fixed.inset-0.z-50');
    const drawerPanel = drawer ? drawer.querySelector('.bg-surface') : null;
    const rect = drawer ? drawer.getBoundingClientRect() : null;
    const panelRect = drawerPanel ? drawerPanel.getBoundingClientRect() : null;
    return {
        drawerRect: rect ? { top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right, width: rect.width, height: rect.height } : null,
        panelRect: panelRect ? { top: panelRect.top, bottom: panelRect.bottom, left: panelRect.left, right: panelRect.right, width: panelRect.width, height: panelRect.height } : null,
        windowHeight: window.innerHeight,
        windowWidth: window.innerWidth
    };
""")
print(json.dumps(info, indent=2))
driver.quit()
