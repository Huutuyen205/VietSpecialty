from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json

options = Options()
options.add_argument('--headless=new')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/')

info = driver.execute_script("""
    // Remove backdrop-blur-md from header and put it on a wrapper inside header
    const header = document.querySelector('header');
    header.classList.remove('backdrop-blur-md');
    
    const bar = document.querySelector('.md\\\\:hidden.fixed.bottom-0.inset-x-0');
    const rect = bar.getBoundingClientRect();
    return {
        headerRect: header.getBoundingClientRect(),
        barRect: { top: rect.top, bottom: rect.bottom, height: rect.height },
        windowHeight: window.innerHeight,
        isFixedAtBottom: Math.abs(rect.bottom - window.innerHeight) <= 2
    };
""")
print(json.dumps(info, indent=2))
driver.quit()
