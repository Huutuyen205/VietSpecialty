import requests
from lxml import html

r = requests.get('http://localhost:8080/products')
print('Products page status:', r.status_code)
tree = html.fromstring(r.content)
grids = tree.xpath('//div[contains(@class, "grid-cols-2")]')
print('Grids with grid-cols-2:', len(grids))
for i, g in enumerate(grids):
    print(f'Grid {i} classes:', g.get('class'), 'children:', len(g.getchildren()))
    cards = g.xpath('.//div[contains(@class, "card")]')
    print(f'  card elements inside grid {i}:', len(cards))

# Also check home page
r2 = requests.get('http://localhost:8080/')
print('\nHome page status:', r2.status_code)
tree2 = html.fromstring(r2.content)
grids2 = tree2.xpath('//div[contains(@class, "grid")]')
print('Total grids on home:', len(grids2))
for i, g in enumerate(grids2):
    cards = g.xpath('.//div[contains(@class, "card")]')
    print(f'Home Grid {i} classes:', g.get('class'), 'cards:', len(cards))
