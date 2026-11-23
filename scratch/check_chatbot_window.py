from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json
import time

options = Options()
options.add_argument('--headless=new')
options.add_argument('--window-size=375,812')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/')
time.sleep(1)

info = driver.execute_script("""
    window.toggleChatBot();
    const win = document.getElementById('chatbot-window');
    const rect = win.getBoundingClientRect();
    const style = window.getComputedStyle(win);
    return {
        displayed: !win.classList.contains('hidden'),
        rect: { top: rect.top, bottom: rect.bottom, left: rect.left, right: rect.right, width: rect.width, height: rect.height },
        windowWidth: window.innerWidth,
        windowHeight: window.innerHeight,
        fitsWidth: rect.left >= 0 && rect.right <= window.innerWidth,
        hasHorizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
    };
""")
print(json.dumps(info, indent=2))
driver.quit()
