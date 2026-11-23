from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json
import time

options = Options()
options.add_argument('--headless=new')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/products')
time.sleep(1)

results = {}
for w, h in [(375, 812), (390, 844)]:
    driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
        'width': w,
        'height': h,
        'deviceScaleFactor': 3,
        'mobile': True
    })
    time.sleep(0.5)

    info = driver.execute_script("""
        const grid = document.querySelector('div.grid.grid-cols-2.sm\\\\:grid-cols-3');
        if (!grid) return { error: 'No grid found' };
        const gridStyle = window.getComputedStyle(grid);
        const colTemplates = gridStyle.gridTemplateColumns.split(' ');
        const cards = Array.from(grid.querySelectorAll('.card'));
        
        const cardDetails = cards.map((c, i) => {
            const rect = c.getBoundingClientRect();
            const img = c.querySelector('img');
            const h4 = c.querySelector('h4');
            const price = c.querySelector('.font-serif');
            const origin = c.querySelector('.truncate.font-medium');
            
            return {
                index: i,
                width: rect.width,
                height: rect.height,
                imgNaturalWidth: img ? img.naturalWidth : 0,
                imgNaturalHeight: img ? img.naturalHeight : 0,
                imgComplete: img ? img.complete : false,
                isImgBroken: img ? (!img.complete || img.naturalWidth === 0) : true,
                imgSrc: img ? img.src : null,
                title: h4 ? h4.innerText.trim() : '',
                titleOverflow: h4 ? h4.scrollWidth > h4.clientWidth + 1 : false,
                originText: origin ? origin.innerText.trim() : '',
                priceText: price ? price.innerText.trim() : ''
            };
        });

        const brokenImgs = cardDetails.filter(c => c.isImgBroken);
        const textOverflows = cardDetails.filter(c => c.titleOverflow);

        return {
            windowInnerWidth: window.innerWidth,
            gridColumnsCount: colTemplates.length,
            gridTemplateColumns: gridStyle.gridTemplateColumns,
            gridGap: gridStyle.gap,
            totalCards: cards.length,
            cardWidths: cardDetails.slice(0, 4).map(c => c.width),
            areFirstTwoEqual: cardDetails.length >= 2 ? Math.abs(cardDetails[0].width - cardDetails[1].width) <= 2 : true,
            totalBrokenImages: brokenImgs.length,
            sampleBrokenSrc: brokenImgs.slice(0, 3).map(c => c.imgSrc),
            totalTextOverflows: textOverflows.length,
            pageScrollWidth: document.documentElement.scrollWidth,
            hasPageHorizontalOverflow: document.documentElement.scrollWidth > window.innerWidth
        };
    """)
    results[f"{w}x{h}"] = info

print(json.dumps(results, indent=2))
driver.quit()
