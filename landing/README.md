# Landing page: guidancetutors.net/start

`index.html` is the source. `assets/` holds the logo and favicon.

## Publishing a change

1. Edit `index.html`, then run `python3 landing/build.py`. This writes `dist/start.html` with the images inlined.
2. Commit and push.
3. Ask the site to pull the new file. Send `POST /wp-json/gt/v1/landing-sync` as an administrator, with `{"ref": "<commit sha or branch>"}`.

WordPress side:
- **The "start" page (ID 14132):** its content stores the full HTML document.
- **The "GT landing page /start" code snippet:** serves that HTML as is, without the theme, and provides the sync route.
