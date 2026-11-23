from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
import time

options = Options()
options.add_argument('--headless=new')
driver = webdriver.Chrome(options=options)

driver.get('http://localhost:8080/login')
time.sleep(0.5)
driver.find_element(By.NAME, 'email').send_keys('admin@vietspecialty.vn')
driver.find_element(By.NAME, 'password').send_keys('Admin@123456')
driver.find_element(By.CSS_SELECTOR, 'form[action*="/login"] button[type="submit"]').click()
time.sleep(1)

print('Current URL after admin login:', driver.current_url)
print('Page Title:', driver.title)

driver.get('http://localhost:8080/admin/dashboard')
time.sleep(1)
print('Admin dashboard URL:', driver.current_url)
print('Admin dashboard Title:', driver.title)
print('Page source snippet:', driver.page_source[:500])

driver.quit()
