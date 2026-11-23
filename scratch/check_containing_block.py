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
    const header = document.querySelector('header');
    const headerRect = header.getBoundingClientRect();
    const barRect = bar.getBoundingClientRect();
    let parent = bar.parentElement;
    const ancestors = [];
    while (parent) {
        const s = window.getComputedStyle(parent);
        ancestors.push({
            tag: parent.tagName,
            class: parent.className,
            transform: s.transform,
            backdropFilter: s.backdropFilter,
            filter: s.filter,
            position: s.position,
            rect: parent.getBoundingClientRect()
        });
        parent = parent.parentElement;
    }
    return {
        headerRect,
        barRect,
        ancestors
    };
""")
print(json.dumps(info, indent=2))
driver.quit()
