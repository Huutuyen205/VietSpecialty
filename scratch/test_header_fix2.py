from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json

options = Options()
options.add_argument('--headless=new')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/')

info = driver.execute_script("""
    const header = document.querySelector('header');
    header.style.backdropFilter = 'none';
    header.style.webkitBackdropFilter = 'none';
    
    const bar = document.querySelector('.md\\\\:hidden.fixed.bottom-0.inset-x-0');
    const rect = bar.getBoundingClientRect();
    return {
        headerBackdrop: window.getComputedStyle(header).backdropFilter,
        barRect: { top: rect.top, bottom: rect.bottom, height: rect.height },
        windowHeight: window.innerHeight,
        isFixedAtBottom: Math.abs(rect.bottom - window.innerHeight) <= 2
    };
""")
print(json.dumps(info, indent=2))
driver.quit()
