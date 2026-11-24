# GitHub Pages — privacy policy URL for Google Play

## Recommended: branch `gh-pages`

Сайт собирается из каталога **`site/`** в корне репозитория (на `main`). Ветка **`gh-pages`** содержит только эти файлы в корне (без кода Android).

| File (on `gh-pages` / in `site/`) | Purpose |
|-----------------------------------|---------|
| `index.html` | English privacy policy — **Play Console URL** |
| `privacy-ru.html` | Russian privacy policy |
| `.nojekyll` | Disable Jekyll on GitHub Pages |

Markdown-исходники для правок: `docs/PRIVACY_POLICY.en.md`, `docs/PRIVACY_POLICY.md`. HTML для Play обновляйте в `site/index.html` (и при необходимости дублируйте в `docs/index.html`).

Before publishing, replace placeholder e-mails in `site/*.html` and the data-controller text in section 1.

### Publish or refresh `gh-pages`

```bash
# after committing changes under site/
./scripts/update-gh-pages.sh
git push -u origin gh-pages
```

### Enable Pages on GitHub

1. Push **`main`** (with `site/`) and **`gh-pages`**.
2. **Settings → Pages → Build and deployment**
3. **Source:** Deploy from a branch
4. **Branch:** `gh-pages`, folder **`/ (root)`**
5. URL: `https://<github-username>.github.io/<repo-name>/`
6. Play Console → **App content → Privacy policy** → paste that URL.

### Alternative: `/docs` on `main`

You can instead deploy **`main`** + folder **`/docs`** (same HTML in `docs/index.html`). The dedicated `gh-pages` branch keeps the published site separate from the app repo layout.

## Custom domain (optional)

In Pages settings, add a custom domain and DNS records from GitHub. Use the HTTPS URL in Play Console.

## Updating the policy

1. Edit `site/index.html` / `site/privacy-ru.html` (and Markdown in `docs/` if you keep them in sync).
2. Bump **Last updated** dates.
3. Commit on `main`, run `./scripts/update-gh-pages.sh`, push `main` and `gh-pages`.