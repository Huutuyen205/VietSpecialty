import json
import time
import sys
import os
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By

BASE_URL = 'http://localhost:8080'
SCREENSHOT_DIR = 'scratch/screenshots'
os.makedirs(SCREENSHOT_DIR, exist_ok=True)

def create_driver():
    options = Options()
    options.add_argument('--headless=new')
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    options.add_argument('--disable-gpu')
    options.add_argument('--window-size=1440,900')
    driver = webdriver.Chrome(options=options)
    return driver

def check_horizontal_overflow(driver):
    return driver.execute_script("""
        return {
            scrollWidth: document.documentElement.scrollWidth,
            clientWidth: document.documentElement.clientWidth,
            hasOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
        };
    """)

def check_images(driver):
    return driver.execute_script("""
        const imgs = Array.from(document.querySelectorAll('img'));
        const broken = imgs.filter(img => !img.complete || img.naturalWidth === 0).map(img => img.src || img.getAttribute('src'));
        return {
            total: imgs.length,
            brokenCount: broken.length,
            brokenImages: broken.slice(0, 5)
        };
    """)

results = {
    "mobile": {},
    "tablet": {},
    "desktop": {},
    "special_pages": {},
    "evaluations": []
}

driver = create_driver()

try:
    print("=== 1. KIỂM THỬ MOBILE (375px & 390px) ===")
    for width, height in [(375, 812), (390, 844)]:
        vp_name = f"{width}x{height}"
        print(f"Testing viewport: {vp_name}")
        driver.set_window_size(width, height)
        driver.get(f"{BASE_URL}/")
        time.sleep(1)

        # 1.1 Header thu gọn & Nút hamburger mở menu drawer
        hamburgers = driver.find_elements(By.CSS_SELECTOR, 'button[aria-label="Mở menu"]')
        has_hamburger = len(hamburgers) > 0 and hamburgers[0].is_displayed()
        
        # Desktop search bar & mega-nav ẩn trên mobile?
        desktop_search_visible = driver.execute_script("""
            const ds = document.querySelector('.hidden.md\\\\:flex');
            return ds ? (window.getComputedStyle(ds).display !== 'none') : false;
        """)
        desktop_nav_visible = driver.execute_script("""
            const dn = document.querySelector('nav.hidden.md\\\\:block');
            return dn ? (window.getComputedStyle(dn).display !== 'none') : false;
        """)

        # Mở mobile drawer
        drawer_opened = False
        drawer_content = {}
        if has_hamburger:
            driver.execute_script("arguments[0].click();", hamburgers[0])
            time.sleep(0.5)
            driver.save_screenshot(f"{SCREENSHOT_DIR}/mobile_drawer_{width}.png")
            
            drawer_info = driver.execute_script("""
                const drawer = document.querySelector('.md\\\\:hidden.fixed.inset-0.z-50');
                if (!drawer) return null;
                const style = window.getComputedStyle(drawer);
                const links = Array.from(drawer.querySelectorAll('a')).map(a => ({
                    text: a.innerText.trim(),
                    href: a.getAttribute('href')
                }));
                return {
                    visible: style.display !== 'none',
                    links: links
                };
            """)
            if drawer_info and drawer_info['visible']:
                drawer_opened = True
                drawer_content = drawer_info

            # Đóng drawer
            close_btn = driver.find_elements(By.CSS_SELECTOR, '.md\\:hidden.fixed.inset-0.z-50 button')
            if close_btn:
                driver.execute_script("arguments[0].click();", close_btn[0])
                time.sleep(0.3)

        # 1.2 Mobile bottom navigation bar 5 nút
        bottom_bar_info = driver.execute_script("""
            const bar = document.querySelector('.md\\\\:hidden.fixed.bottom-0.inset-x-0');
            if (!bar) return null;
            const style = window.getComputedStyle(bar);
            const rect = bar.getBoundingClientRect();
            const links = Array.from(bar.querySelectorAll('a')).map(a => ({
                text: a.innerText.trim(),
                href: a.getAttribute('href'),
                displayed: a.offsetParent !== null
            }));
            return {
                visible: style.display !== 'none',
                position: style.position,
                bottom: rect.bottom,
                windowHeight: window.innerHeight,
                isFixedAtBottom: Math.abs(rect.bottom - window.innerHeight) <= 2,
                buttonsCount: links.length,
                buttons: links
            };
        """)

        # 1.3 Chatbot AI floating button & window
        chatbot_pos = driver.execute_script("""
            const el = document.getElementById('chatbot-container');
            const btn = document.getElementById('chatbot-toggle-btn');
            if (!el || !btn) return null;
            const rect = el.getBoundingClientRect();
            const btnRect = btn.getBoundingClientRect();
            const style = window.getComputedStyle(el);
            return {
                bottomOffset: window.innerHeight - btnRect.bottom,
                rightOffset: window.innerWidth - btnRect.right,
                classes: el.className,
                isAboveBottomBar: (window.innerHeight - btnRect.bottom) >= 60
            };
        """)

        # Bật Chatbot Window
        chatbot_btn = driver.find_element(By.ID, 'chatbot-toggle-btn')
        driver.execute_script("arguments[0].click();", chatbot_btn)
        time.sleep(0.5)
        driver.save_screenshot(f"{SCREENSHOT_DIR}/mobile_chatbot_{width}.png")

        chatbot_win = driver.execute_script("""
            const win = document.getElementById('chatbot-window');
            if (!win) return null;
            const rect = win.getBoundingClientRect();
            const style = window.getComputedStyle(win);
            return {
                displayed: style.display !== 'none',
                width: rect.width,
                height: rect.height,
                left: rect.left,
                right: rect.right,
                windowWidth: window.innerWidth,
                windowHeight: window.innerHeight,
                fitsWithinViewport: rect.left >= 0 && rect.right <= window.innerWidth + 2,
                hasOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
            };
        """)
        # Đóng Chatbot
        driver.execute_script("arguments[0].click();", chatbot_btn)
        time.sleep(0.3)

        # 1.4 Lưới thẻ đặc sản 2 cột trên trang /products
        driver.get(f"{BASE_URL}/products")
        time.sleep(1)
        driver.save_screenshot(f"{SCREENSHOT_DIR}/mobile_products_{width}.png")

        grid_mobile = driver.execute_script("""
            const grid = document.querySelector('.grid.grid-cols-2');
            if (!grid) return null;
            const style = window.getComputedStyle(grid);
            const cols = style.gridTemplateColumns.split(' ').length;
            const cards = Array.from(grid.querySelectorAll('.card'));
            const cardData = cards.slice(0, 4).map(c => {
                const r = c.getBoundingClientRect();
                const img = c.querySelector('img');
                const title = c.querySelector('h4');
                return {
                    width: r.width,
                    height: r.height,
                    imgComplete: img ? img.complete : false,
                    imgNaturalWidth: img ? img.naturalWidth : 0,
                    imgNaturalHeight: img ? img.naturalHeight : 0,
                    titleText: title ? title.innerText.trim() : '',
                    titleScrollWidth: title ? title.scrollWidth : 0,
                    titleClientWidth: title ? title.clientWidth : 0,
                    titleOverflow: title ? title.scrollWidth > title.clientWidth + 1 : false
                };
            });
            return {
                columnsCount: cols,
                gridTemplateColumns: style.gridTemplateColumns,
                totalCards: cards.length,
                sampleCards: cardData,
                areColumnsEqual: cardData.length >= 2 ? Math.abs(cardData[0].width - cardData[1].width) <= 2 : true
            };
        """)
        overflow_mobile = check_horizontal_overflow(driver)
        images_mobile = check_images(driver)

        results["mobile"][vp_name] = {
            "header": {
                "has_hamburger": has_hamburger,
                "desktop_search_hidden": not desktop_search_visible,
                "desktop_nav_hidden": not desktop_nav_visible,
                "drawer_opened": drawer_opened,
                "drawer_links_count": len(drawer_content.get('links', []))
            },
            "bottom_bar": bottom_bar_info,
            "chatbot": {
                "position": chatbot_pos,
                "window": chatbot_win
            },
            "product_grid": grid_mobile,
            "page_horizontal_overflow": overflow_mobile,
            "images_status": images_mobile
        }

    print("Mobile tests complete.\n")

    print("=== 2. KIỂM THỬ TABLET (768px) ===")
    driver.set_window_size(768, 1024)
    driver.get(f"{BASE_URL}/products")
    time.sleep(1)
    driver.save_screenshot(f"{SCREENSHOT_DIR}/tablet_products_768.png")

    tablet_grid = driver.execute_script("""
        const grid = document.querySelector('.grid.grid-cols-2');
        if (!grid) return null;
        const style = window.getComputedStyle(grid);
        const cols = style.gridTemplateColumns.split(' ').length;
        const cards = Array.from(grid.querySelectorAll('.card'));
        const cardData = cards.slice(0, 3).map(c => {
            const r = c.getBoundingClientRect();
            return { width: r.width, height: r.height, top: r.top, left: r.left };
        });
        return {
            columnsCount: cols,
            gridTemplateColumns: style.gridTemplateColumns,
            totalCards: cards.length,
            sampleCards: cardData
        };
    """)
    tablet_overflow = check_horizontal_overflow(driver)

    # 2.2 Sidebar / Drawer Admin ở 768px
    driver.get(f"{BASE_URL}/login")
    time.sleep(0.5)
    driver.find_element(By.NAME, 'email').send_keys('admin@vietspecialty.vn')
    driver.find_element(By.NAME, 'password').send_keys('Admin@123456')
    driver.find_element(By.CSS_SELECTOR, 'form[action*="/login"] button[type="submit"]').click()
    time.sleep(1)

    driver.get(f"{BASE_URL}/admin/dashboard")
    time.sleep(1)
    driver.save_screenshot(f"{SCREENSHOT_DIR}/tablet_admin_768.png")

    admin_initial = driver.execute_script("""
        const toggleBtn = document.querySelector('header button[aria-label="Mở thanh điều hướng"]');
        const aside = document.querySelector('aside');
        const asideRect = aside ? aside.getBoundingClientRect() : null;
        return {
            toggleVisible: toggleBtn ? (toggleBtn.offsetParent !== null) : false,
            asideHiddenOnLoad: asideRect ? (asideRect.left < 0 || asideRect.right <= 0) : false,
            asideRect: asideRect
        };
    """)

    admin_drawer_opened = False
    admin_toggle = driver.find_elements(By.CSS_SELECTOR, 'header button[aria-label="Mở thanh điều hướng"]')
    if admin_toggle and admin_toggle[0].is_displayed():
        driver.execute_script("arguments[0].click();", admin_toggle[0])
        time.sleep(0.5)
        driver.save_screenshot(f"{SCREENSHOT_DIR}/tablet_admin_drawer_768.png")
        admin_drawer_opened = driver.execute_script("""
            const aside = document.querySelector('aside');
            if (!aside) return false;
            const rect = aside.getBoundingClientRect();
            return rect.left >= 0 && rect.width > 0;
        """)

    # 2.3 Sidebar / Drawer Seller ở 768px
    driver.get(f"{BASE_URL}/login")
    time.sleep(0.5)
    driver.find_element(By.NAME, 'email').send_keys('seller_bac@vietspecialty.vn')
    driver.find_element(By.NAME, 'password').send_keys('Password123@')
    driver.find_element(By.CSS_SELECTOR, 'form[action*="/login"] button[type="submit"]').click()
    time.sleep(1)

    driver.get(f"{BASE_URL}/seller/dashboard")
    time.sleep(1)
    driver.save_screenshot(f"{SCREENSHOT_DIR}/tablet_seller_768.png")

    seller_initial = driver.execute_script("""
        const toggleBtn = document.querySelector('header button');
        const aside = document.querySelector('aside');
        const style = aside ? window.getComputedStyle(aside) : null;
        return {
            toggleVisible: toggleBtn ? (toggleBtn.offsetParent !== null) : false,
            asideHiddenOnLoad: style ? (style.display === 'none') : false
        };
    """)

    seller_drawer_opened = False
    seller_toggle = driver.find_elements(By.CSS_SELECTOR, 'header button')
    if seller_toggle and seller_toggle[0].is_displayed():
        driver.execute_script("arguments[0].click();", seller_toggle[0])
        time.sleep(0.5)
        driver.save_screenshot(f"{SCREENSHOT_DIR}/tablet_seller_drawer_768.png")
        seller_drawer_opened = driver.execute_script("""
            const aside = document.querySelector('aside');
            if (!aside) return false;
            const style = window.getComputedStyle(aside);
            return style.display !== 'none';
        """)

    results["tablet"] = {
        "grid": tablet_grid,
        "horizontal_overflow": tablet_overflow,
        "admin_sidebar": {
            "initial": admin_initial,
            "drawer_opened": admin_drawer_opened
        },
        "seller_sidebar": {
            "initial": seller_initial,
            "drawer_opened": seller_drawer_opened
        }
    }
    print("Tablet tests complete.\n")

    print("=== 3. KIỂM THỬ DESKTOP (1440px) ===")
    driver.set_window_size(1440, 900)

    # 3.1 Header đầy đủ thanh tìm kiếm & điều hướng desktop
    driver.get(f"{BASE_URL}/")
    time.sleep(1)
    driver.save_screenshot(f"{SCREENSHOT_DIR}/desktop_home_1440.png")

    desktop_header = driver.execute_script("""
        const searchInput = document.querySelector('input[name="keyword"]');
        const searchForm = document.querySelector('form[action="/products"]');
        const megaNav = document.querySelector('nav.hidden.md\\\\:block');
        const hamburger = document.querySelector('button[aria-label="Mở menu"]');
        const bottomBar = document.querySelector('.md\\\\:hidden.fixed.bottom-0.inset-x-0');
        const megaLinks = megaNav ? Array.from(megaNav.querySelectorAll('a')).map(a => a.innerText.trim()) : [];
        return {
            searchBarVisible: searchInput ? (searchInput.offsetParent !== null) : false,
            searchPlaceholder: searchInput ? searchInput.getAttribute('placeholder') : '',
            megaNavVisible: megaNav ? (window.getComputedStyle(megaNav).display !== 'none') : false,
            megaNavLinks: megaLinks,
            hamburgerHidden: hamburger ? (hamburger.offsetParent === null) : true,
            bottomBarHidden: bottomBar ? (bottomBar.offsetParent === null) : true
        };
    """)

    # 3.2 Lưới sản phẩm Desktop trên /products & home
    driver.get(f"{BASE_URL}/products")
    time.sleep(1)
    driver.save_screenshot(f"{SCREENSHOT_DIR}/desktop_products_1440.png")

    desktop_prod_grid = driver.execute_script("""
        const grid = document.querySelector('.grid.grid-cols-2');
        if (!grid) return null;
        const style = window.getComputedStyle(grid);
        const cols = style.gridTemplateColumns.split(' ').length;
        return {
            columnsCount: cols,
            gridTemplateColumns: style.gridTemplateColumns
        };
    """)

    # Kiểm tra lưới trên Home (Categories 8 cột, OCOP 4 cột)
    driver.get(f"{BASE_URL}/")
    time.sleep(1)
    desktop_home_grids = driver.execute_script("""
        const catGrid = document.querySelector('.grid.grid-cols-2.sm\\\\:grid-cols-4.lg\\\\:grid-cols-8');
        const ocopGrid = document.querySelector('.grid.grid-cols-2.sm\\\\:grid-cols-3.lg\\\\:grid-cols-4');
        return {
            catGridCols: catGrid ? window.getComputedStyle(catGrid).gridTemplateColumns.split(' ').length : null,
            ocopGridCols: ocopGrid ? window.getComputedStyle(ocopGrid).gridTemplateColumns.split(' ').length : null
        };
    """)

    # 3.3 Sidebar Admin cố định ở 1440px
    driver.get(f"{BASE_URL}/admin/dashboard")
    time.sleep(1)
    driver.save_screenshot(f"{SCREENSHOT_DIR}/desktop_admin_1440.png")
    admin_desktop = driver.execute_script("""
        const aside = document.querySelector('aside');
        const toggleBtn = document.querySelector('header button[aria-label="Mở thanh điều hướng"]');
        const style = aside ? window.getComputedStyle(aside) : null;
        const rect = aside ? aside.getBoundingClientRect() : null;
        return {
            toggleHidden: toggleBtn ? (toggleBtn.offsetParent === null) : true,
            asideVisible: rect ? (rect.left >= 0 && rect.width > 0) : false,
            asidePosition: style ? style.position : null,
            isStaticLayout: style ? (style.position === 'static') : false
        };
    """)

    # 3.4 Sidebar Seller cố định ở 1440px
    driver.get(f"{BASE_URL}/seller/dashboard")
    time.sleep(1)
    driver.save_screenshot(f"{SCREENSHOT_DIR}/desktop_seller_1440.png")
    seller_desktop = driver.execute_script("""
        const aside = document.querySelector('aside');
        const toggleBtn = document.querySelector('header button');
        const style = aside ? window.getComputedStyle(aside) : null;
        const rect = aside ? aside.getBoundingClientRect() : null;
        return {
            toggleHidden: toggleBtn ? (toggleBtn.offsetParent === null) : true,
            asideVisible: style ? (style.display !== 'none') : false,
            isBlockStatic: style ? (style.position === 'static' || style.display === 'block') : false
        };
    """)

    results["desktop"] = {
        "header": desktop_header,
        "product_grid_page": desktop_prod_grid,
        "home_grids": desktop_home_grids,
        "admin_sidebar": admin_desktop,
        "seller_sidebar": seller_desktop
    }
    print("Desktop tests complete.\n")

    print("=== 4. KIỂM THỬ CÁC TRANG ĐẶC BIỆT ===")
    # 4.1 Showroom /dev/ui
    driver.get(f"{BASE_URL}/dev/ui")
    time.sleep(1)
    driver.save_screenshot(f"{SCREENSHOT_DIR}/showroom_dev_ui.png")
    dev_ui = driver.execute_script("""
        return {
            title: document.title,
            h1: document.querySelector('h1') ? document.querySelector('h1').innerText.trim() : null,
            sectionsCount: document.querySelectorAll('section').length,
            overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
        };
    """)

    # 4.2 Trang 404
    driver.get(f"{BASE_URL}/khong-ton-tai-404-check")
    time.sleep(1)
    driver.save_screenshot(f"{SCREENSHOT_DIR}/error_404.png")
    p404 = driver.execute_script("""
        return {
            title: document.title,
            h1: document.querySelector('h1') ? document.querySelector('h1').innerText.trim() : null,
            hasHomeCta: !!document.querySelector('a[href="/"]'),
            hasProductsCta: !!document.querySelector('a[href="/products"]')
        };
    """)

    # 4.3 Trang 500
    driver.get(f"{BASE_URL}/error/500")
    time.sleep(1)
    driver.save_screenshot(f"{SCREENSHOT_DIR}/error_500.png")
    p500 = driver.execute_script("""
        return {
            title: document.title,
            h1: document.querySelector('h1') ? document.querySelector('h1').innerText.trim() : null,
            hasHomeCta: !!document.querySelector('a[href="/"]'),
            hasReloadBtn: !!document.querySelector('button[onclick*="reload"]')
        };
    """)

    # 4.4 Trang Giỏ hàng trống (/cart)
    # Logout and ensure guest empty cart
    driver.get(f"{BASE_URL}/logout")
    driver.execute_script("""
        const f = document.querySelector('form[action*="/logout"]');
        if (f) f.submit();
    """)
    time.sleep(1)
    driver.get(f"{BASE_URL}/cart")
    time.sleep(1)
    driver.save_screenshot(f"{SCREENSHOT_DIR}/cart_empty.png")
    cart_empty = driver.execute_script("""
        const emptyH2 = document.querySelector('h2');
        const ctaBtn = document.querySelector('a[href="/products"]');
        return {
            title: document.title,
            emptyTitle: emptyH2 ? emptyH2.innerText.trim() : null,
            hasCtaBtn: !!ctaBtn,
            ctaText: ctaBtn ? ctaBtn.innerText.trim() : null,
            overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth
        };
    """)

    results["special_pages"] = {
        "showroom_dev_ui": dev_ui,
        "page_404": p404,
        "page_500": p500,
        "empty_cart": cart_empty
    }
    print("Special pages tests complete.\n")

finally:
    driver.quit()
    with open('scratch/test_ui_results.json', 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print("Saved test results to scratch/test_ui_results.json")
