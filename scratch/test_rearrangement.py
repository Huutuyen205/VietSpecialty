from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json
import time

options = Options()
options.add_argument('--headless=new')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/')

info = driver.execute_script("""
    // Rearrange DOM dynamically to simulate the fix
    const header = document.querySelector('header');
    const drawer = document.querySelector('.md\\\\:hidden.fixed.inset-0.z-50');
    const bottomBar = document.querySelector('.md\\\\:hidden.fixed.bottom-0.inset-x-0');
    
    // Move drawer and bottomBar to be siblings of header
    header.parentNode.insertBefore(drawer, header.nextSibling);
    header.parentNode.insertBefore(bottomBar, drawer.nextSibling);
    
    const barRect = bottomBar.getBoundingClientRect();
    
    // Test drawer open
    const hamburger = document.querySelector('button[aria-label="Mở menu"]');
    hamburger.click();
    const drawerRect = drawer.getBoundingClientRect();
    
    return {
        barRect: { top: barRect.top, bottom: barRect.bottom, height: barRect.height },
        drawerRect: { top: drawerRect.top, bottom: drawerRect.bottom, height: drawerRect.height, width: drawerRect.width },
        windowHeight: window.innerHeight,
        windowWidth: window.innerWidth,
        isBarFixedAtBottom: Math.abs(barRect.bottom - window.innerHeight) <= 2,
        isDrawerFullScreen: Math.abs(drawerRect.height - window.innerHeight) <= 2
    };
""")
print(json.dumps(info, indent=2))
driver.quit()
