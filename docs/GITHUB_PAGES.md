# GitHub Pages — privacy policy URL for Google Play

Use the English HTML page as the public privacy policy link in Play Console.

## Files

| File | Purpose |
|------|---------|
| `docs/index.html` | **Play Store URL target** — full English privacy policy |
| `docs/PRIVACY_POLICY.en.md` | Same content in Markdown (editing / version control) |
| `docs/PRIVACY_POLICY.md` | Russian version |

Before publishing, replace `REPLACE_WITH_YOUR_EMAIL@example.com` in `index.html` and both Markdown files, and fill in the data-controller sentence in section 1.

## Enable GitHub Pages (repo settings)

1. Push the repo to GitHub.
2. **Settings → Pages**
3. **Build and deployment → Source:** Deploy from a branch
4. **Branch:** `main` (or your default), folder **`/docs`**
5. Save. After 1–2 minutes the site is live at:

   `https://<github-username>.github.io/<repo-name>/`

   Example: `https://octocat.github.io/nettoolssys/`

6. In **Play Console → App content → Privacy policy**, paste that URL (trailing slash or `/index.html` both work).

## Custom domain (optional)

In Pages settings, set a custom domain (e.g. `privacy.yourdomain.com`) and add the DNS records GitHub shows. Use the HTTPS URL in Play Console.

## Updating the policy

1. Edit `docs/PRIVACY_POLICY.en.md` and sync changes into `docs/index.html` (or edit HTML directly).
2. Bump **Last updated** in both files.
3. Commit and push; Pages redeploys automatically.

## Note on Russian Markdown link

`index.html` links to `PRIVACY_POLICY.md` for readers browsing the repo site. Raw Markdown may render as plain text on Pages depending on theme—that link is optional for Play; the English HTML page is the required public policy.