from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json
import time

options = Options()
options.add_argument('--headless=new')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/products')
time.sleep(1)

info = driver.execute_script("""
    // Wrap header in a div
    const header = document.querySelector('header');
    const wrapper = document.createElement('div');
    header.parentNode.insertBefore(wrapper, header);
    wrapper.appendChild(header);
    
    // Check initial rect
    const initialRect = header.getBoundingClientRect();
    
    // Scroll down 500px
    window.scrollTo(0, 500);
    const scrolledRect = header.getBoundingClientRect();
    
    return {
        initialTop: initialRect.top,
        scrolledTop: scrolledRect.top,
        scrollY: window.scrollY,
        isStickyWorking: scrolledRect.top === 0
    };
""")
print(json.dumps(info, indent=2))
driver.quit()
