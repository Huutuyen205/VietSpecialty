from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json
import time

options = Options()
options.add_argument('--headless=new')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/')
time.sleep(1)

results = {}
for w, h in [(375, 812), (390, 844)]:
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': w,
        'height': h,
        'deviceScaleFactor': 3,
        'mobile': True
    })
    time.sleep(0.3)
    
    # Open chatbot properly by calling toggle once
    info = driver.execute_script("""
        const win = document.getElementById('chatbot-window');
        if (win.classList.contains('hidden')) {
            window.toggleChatBot();
        }
        const rect = win.getBoundingClientRect();
        return {
            windowInnerWidth: window.innerWidth,
            windowInnerHeight: window.innerHeight,
            chatWidth: rect.width,
            chatHeight: rect.height,
            chatLeft: rect.left,
            chatRight: rect.right,
            chatTop: rect.top,
            chatBottom: rect.bottom,
            hasHorizontalOverflow: document.documentElement.scrollWidth > window.innerWidth,
            docScrollWidth: document.documentElement.scrollWidth,
            fitsInScreen: rect.left >= 0 && rect.right <= window.innerWidth
        };
    """)
    results[f"{w}x{h}"] = info
    driver.save_screenshot(f"scratch/screenshots/chatbot_open_{w}.png")

print(json.dumps(results, indent=2))
driver.quit()
