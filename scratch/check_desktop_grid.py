from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json
import time

options = Options()
options.add_argument('--headless=new')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/products')
time.sleep(1)

driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
    'width': 1440,
    'height': 900,
    'deviceScaleFactor': 1,
    'mobile': False
})
time.sleep(0.5)

info = driver.execute_script("""
    const grid = document.querySelector('div.grid.grid-cols-2.sm\\\\:grid-cols-3');
    if (!grid) return { error: 'No grid found' };
    const gridStyle = window.getComputedStyle(grid);
    const colTemplates = gridStyle.gridTemplateColumns.split(' ');
    const cards = Array.from(grid.querySelectorAll('.card'));
    
    return {
        windowInnerWidth: window.innerWidth,
        gridColumnsCount: colTemplates.length,
        gridTemplateColumns: gridStyle.gridTemplateColumns,
        gridGap: gridStyle.gap,
        totalCards: cards.length,
        cardWidths: cards.slice(0, 4).map(c => c.getBoundingClientRect().width),
        pageScrollWidth: document.documentElement.scrollWidth,
        hasPageHorizontalOverflow: document.documentElement.scrollWidth > window.innerWidth
    };
""")

# Also check home page at 1440px
driver.get('http://localhost:8080/')
time.sleep(1)
home_info = driver.execute_script("""
    const ocopGrid = document.querySelector('.grid.grid-cols-2.sm\\\\:grid-cols-3.lg\\\\:grid-cols-4');
    const catGrid = document.querySelector('.grid.grid-cols-2.sm\\\\:grid-cols-4.lg\\\\:grid-cols-8');
    return {
        ocopCols: ocopGrid ? window.getComputedStyle(ocopGrid).gridTemplateColumns.split(' ').length : null,
        catCols: catGrid ? window.getComputedStyle(catGrid).gridTemplateColumns.split(' ').length : null
    };
""")

print("Desktop 1440px products grid:", json.dumps(info, indent=2))
print("Desktop 1440px home grids:", json.dumps(home_info, indent=2))
driver.quit()
