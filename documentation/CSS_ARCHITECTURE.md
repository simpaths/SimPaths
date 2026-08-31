# Documentation CSS architecture

The documentation theme is divided into ordered layers under
`documentation/wiki/assets/css/`. MkDocs loads them in the order listed below.
Changing that order can alter the cascade even when no selector changes.

1. `01-foundation.css`: fonts, tokens, palette, dark mode, and global links.
2. `02-shell-navigation.css`: header, tabs, sidebars, and documentation filtering.
3. `03-content.css`: typography, equations, code, admonitions, and tables.
4. `04-landing-components.css`: the documentation landing page only.
5. `05-site-chrome.css`: footer, search, buttons, and shared responsive chrome.
6. `06-page-sections.css`: research, funding, modules, and other page-specific
   sections.
7. `07-roadmap.css`: the development roadmap and its compact contents view.
8. `08-home.css`: the homepage system and its responsive refinements.

`assets/js/site-state.js` is the single adapter between Material's generated
markup and the styling layer. It exposes explicit `sp-page-*`, `sp-tab-*`,
`sp-search-open`, and navigation state classes. CSS should consume those
classes rather than rediscovering page state with relational selectors.

## Rules for future changes

- Put a rule in the narrowest appropriate component file.
- Preserve the order in `mkdocs.yml` unless a cascade change is intentional.
- Prefer a component class over another global Material-theme override.
- Avoid new `!important` declarations. The guard rejects all `:has()`
  selectors; add a narrowly named state in `site-state.js` instead.
- Do not add page-level `<style>` blocks. Move reusable styling into the
  appropriate component file.
- Remove a component's CSS when its final markup is removed. The retired
  generic hero and card systems are guarded against accidental restoration.
- Run the architecture check and strict MkDocs build before publishing.

```bash
bash documentation/scripts/check-docs-css.sh
mkdocs build --strict
cd documentation/visual-tests && npm test
```

The budgets are set against the refactored baseline, with limited headroom for
deliberate additions. If a file approaches its cap, simplify or extract a
coherent responsibility instead of raising the limit by default.

The browser tests cover representative desktop and mobile routes, assert basic
layout invariants, and save full-page screenshots under `test-results/`. The
deployment workflow uploads those screenshots as an artifact so visual changes
can be reviewed without making pixel-level rendering differences block a
release.
