#!/usr/bin/env bash
# Rebuild the gh-pages branch from site/ and optionally push to origin.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Commit or stash working tree changes before updating gh-pages." >&2
  exit 1
fi

if [[ ! -d site ]] || [[ ! -f site/index.html ]]; then
  echo "Missing site/index.html" >&2
  exit 1
fi

git branch -D gh-pages 2>/dev/null || true
git subtree split --prefix=site -b gh-pages
echo "Branch gh-pages updated from site/."
echo "Publish: git push -u origin gh-pages"