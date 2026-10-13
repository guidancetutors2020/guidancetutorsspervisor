import re, sys
h = open('is.html', encoding='utf8').read()
for k in ('24', '21'):
    m = re.search(r'data-unit="' + k + r'"\s+data-metal="gold"[\s\S]{0,800}?data-testid="price-figure"[^>]*>\s*([0-9.,]+)', h)
    print('ticker', k, m and m.group(1))
for m in re.finditer('prices-strip__name', h):
    seg = re.sub(r'<[^>]+>', '|', h[m.start():m.start() + 1500])
    print('STRIP', re.sub(r'[\s|]*\|[\s|]*', '|', seg)[:400])
