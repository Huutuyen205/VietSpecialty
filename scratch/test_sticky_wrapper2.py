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
    // Wrap in sticky wrapper
    const header = document.querySelector('header');
    header.classList.remove('sticky', 'top-0', 'z-40');
    
    const wrapper = document.createElement('div');
    wrapper.className = 'sticky top-0 z-40';
    header.parentNode.insertBefore(wrapper, header);
    wrapper.appendChild(header);
    
    // Add drawer and bottomBar as siblings of header inside wrapper
    const drawer = document.querySelector('.md\\\\:hidden.fixed.inset-0.z-50');
    const bottomBar = document.querySelector('.md\\\\:hidden.fixed.bottom-0.inset-x-0');
    wrapper.appendChild(drawer);
    wrapper.appendChild(bottomBar);
    
    // Scroll down 500px
    window.scrollTo(0, 500);
    const headerScrolledRect = header.getBoundingClientRect();
    const barScrolledRect = bottomBar.getBoundingClientRect();
    
    return {
        scrollY: window.scrollY,
        headerTop: headerScrolledRect.top,
        isHeaderStickyWorking: headerScrolledRect.top === 0,
        barBottom: barScrolledRect.bottom,
        windowHeight: window.innerHeight,
        isBarFixedAtBottom: Math.abs(barScrolledRect.bottom - window.innerHeight) <= 2
    };
""")
print(json.dumps(info, indent=2))
driver.quit()
