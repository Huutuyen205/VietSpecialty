from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json

options = Options()
options.add_argument('--headless=new')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/')

info = driver.execute_script("""
    const header = document.querySelector('header');
    const s = window.getComputedStyle(header);
    return {
        transform: s.transform,
        filter: s.filter,
        backdropFilter: s.backdropFilter,
        perspective: s.perspective,
        contain: s.contain,
        willChange: s.willChange
    };
""")
print(json.dumps(info, indent=2))
driver.quit()
