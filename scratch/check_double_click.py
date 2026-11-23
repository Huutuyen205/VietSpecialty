from selenium import webdriver
from selenium.webdriver.chrome.options import Options
import json
import time

options = Options()
options.add_argument('--headless=new')
options.add_argument('--window-size=375,812')
driver = webdriver.Chrome(options=options)
driver.get('http://localhost:8080/')
time.sleep(1)

# Set exact mobile viewport via CDP
driver.execute_cdp_cmd('Emulation.setDeviceMetricsOverride', {
    'width': 375,
    'height': 812,
    'deviceScaleFactor': 3,
    'mobile': True
})

# Let's count how many times toggleChatBot is called on click
call_count = driver.execute_script("""
    let count = 0;
    const orig = window.toggleChatBot;
    window.toggleChatBot = function() {
        count++;
        orig.apply(this, arguments);
    };
    const btn = document.getElementById('chatbot-toggle-btn');
    btn.click();
    return {
        count: count,
        winHidden: document.getElementById('chatbot-window').classList.contains('hidden')
    };
""")
print('Click result:', json.dumps(call_count, indent=2))
driver.quit()
