# GitHub Pages — privacy policy URL for Google Play

## Recommended: `gh-pages` branch

The published site is built from the **`site/`** folder on `main`. The **`gh-pages`** branch contains only those files at the branch root (no Android app code).

| File (on `gh-pages` / in `site/`) | Purpose |
|-----------------------------------|---------|
| `index.html` | English privacy policy — **URL for Play Console** |
| `.nojekyll` | Disable Jekyll on GitHub Pages |

**Language:** the public site is **English only**. Russian text lives in the repo as `docs/PRIVACY_POLICY.md` (not deployed to Pages).

Edit Markdown in `docs/PRIVACY_POLICY.en.md`; keep `site/index.html` (and optionally `docs/index.html`) in sync for Play.

Before publishing, replace `REPLACE_WITH_YOUR_EMAIL@example.com` and the data-controller sentence in section 1 of `site/index.html`.

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
5. Site URL: `https://<github-username>.github.io/<repo-name>/`
6. **Play Console → App content → Privacy policy** → paste that URL.

### Alternative: `/docs` on `main`

You can deploy **`main`** with folder **`/docs`** instead (`docs/index.html`). The dedicated `gh-pages` branch keeps the live site separate from the app tree.

## Custom domain (optional)

Add a custom domain in Pages settings and configure DNS as GitHub describes. Use the HTTPS URL in Play Console.

## Updating the policy

1. Edit `site/index.html` (and `docs/PRIVACY_POLICY.en.md` / `docs/index.html` if you keep them aligned).
2. Bump the **Last updated** date.
3. Commit on `main`, run `./scripts/update-gh-pages.sh`, push `main` and `gh-pages`.