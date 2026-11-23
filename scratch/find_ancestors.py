import requests
from lxml import html

tree = html.fromstring(requests.get('http://localhost:8080/products').content)
g = tree.xpath('//div[contains(@class, "grid-cols-2") and contains(@class, "sm:grid-cols-3")]')[0]
p = g
path = []
while p is not None:
    cls = '.'.join(p.get('class', '').split()[:2])
    path.append(f"{p.tag}.{cls}")
    p = p.getparent()
print(' -> '.join(reversed(path)))
