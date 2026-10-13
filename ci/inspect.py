import re
h = open('is.html', encoding='utf8').read()
m = re.search(r'data-unit="24"\s+data-metal="gold"[\s\S]{0,800}?data-testid="price-figure"[^>]*>\s*([0-9.,]+)', h)
print('CHECK 24', m and m.group(1))
m = re.search(r'prices-strip__name[^>]*>\s*عيار 21\s*<[\s\S]{0,1500}?بيع\s*<[\s\S]{0,300}?>\s*([0-9][0-9.,]*)\s*<', h)
print('CHECK 21', m and m.group(1))
