"""Build the file that guidancetutors.net/start/ serves.

Reads landing/index.html, inlines the logo and favicon as data URIs so the page
needs no other files, and writes landing/dist/start.html. The WordPress site
pulls that file from GitHub (see the "GT landing page /start" code snippet).

Usage: python3 landing/build.py
"""
import base64
import pathlib

HERE = pathlib.Path(__file__).resolve().parent


def data_uri(name):
    raw = (HERE / 'assets' / name).read_bytes()
    return 'data:image/png;base64,' + base64.b64encode(raw).decode()


def main():
    html = (HERE / 'index.html').read_text(encoding='utf-8')
    for old, new in (
        ('url("assets/emblem.png")', 'url("%s")' % data_uri('emblem.png')),
        ('href="assets/favicon.png"', 'href="%s"' % data_uri('favicon.png')),
    ):
        if old not in html:
            raise SystemExit('Expected to find %s in index.html' % old)
        html = html.replace(old, new)
    out = HERE / 'dist' / 'start.html'
    out.parent.mkdir(exist_ok=True)
    out.write_text(html, encoding='utf-8')
    print('Wrote %s (%d bytes)' % (out.relative_to(HERE.parent), len(html.encode('utf-8'))))


if __name__ == '__main__':
    main()
