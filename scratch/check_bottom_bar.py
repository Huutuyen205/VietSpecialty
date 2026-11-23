from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json

options = Options()
options.add_argument('--headless=new')
options.add_argument('--window-size=375,812')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/')

info = driver.execute_script("""
    const bar = document.querySelector('.md\\\\:hidden.fixed.bottom-0.inset-x-0');
    const rect = bar.getBoundingClientRect();
    const style = window.getComputedStyle(bar);
    return {
        outerWidth: window.outerWidth,
        innerWidth: window.innerWidth,
        outerHeight: window.outerHeight,
        innerHeight: window.innerHeight,
        styleBottom: style.bottom,
        stylePosition: style.position,
        rect: { top: rect.top, bottom: rect.bottom, height: rect.height }
    };
""")
print(json.dumps(info, indent=2))
driver.quit()
