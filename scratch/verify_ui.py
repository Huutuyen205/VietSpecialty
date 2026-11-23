import re

print("=== VERIFYING UI-BUG-01 & UI-BUG-02 (Mobile Nav & Drawer in Header with backdrop-blur) ===")
with open('src/main/resources/templates/fragments/header.html', encoding='utf-8') as f:
    header_html = f.read()

print("Header tag:", re.findall(r'<header[^>]*>', header_html))
print("Has backdrop-blur in header:", 'backdrop-blur' in header_html)
print("Has mobile bottom nav inside header.html:", 'md:hidden fixed bottom-0' in header_html or 'bottom-0 left-0 right-0 z-40' in header_html or 'nav' in header_html)
for i, line in enumerate(header_html.splitlines(), start=1):
    if 'fixed bottom-0' in line or 'fixed inset-0' in line or 'backdrop-blur' in line:
        print(f"Line {i}: {line.strip()[:100]}")

print("\n=== VERIFYING UI-BUG-03 (Chatbot AI toggle double listener) ===")
with open('src/main/resources/templates/fragments/chat-widget.html', encoding='utf-8') as f:
    chat_html = f.read()

for i, line in enumerate(chat_html.splitlines(), start=1):
    if 'toggleChatBot' in line or 'chatbot-toggle-btn' in line:
        print(f"Line {i}: {line.strip()}")

print("\n=== VERIFYING UI-BUG-04 (Broken image links in sample data) ===")
with open('src/main/resources/templates/fragments/product-card.html', encoding='utf-8') as f:
    card_html = f.read()
print("Has onerror in product-card.html img:", 'onerror' in card_html)
