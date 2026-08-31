# Documentation CSS architecture

The documentation theme is divided into ordered layers under
`documentation/wiki/assets/css/`. MkDocs loads them in the order listed below.
Changing that order can alter the cascade even when no selector changes.

1. `01-foundation.css`: fonts, tokens, palette, dark mode, and global links.
2. `02-shell-navigation.css`: header, tabs, sidebars, and documentation filtering.
3. `03-content.css`: typography, equations, code, admonitions, and tables.
4. `04-landing-components.css`: hero, landing-page cards, and country links.
5. `05-site-chrome.css`: footer, search, buttons, and shared responsive chrome.
6. `06-page-sections.css`: research, funding, and other page-specific sections.
7. `07-home.css`: the final homepage system and its responsive refinements.

## Rules for future changes

- Put a rule in the narrowest appropriate component file.
- Preserve the order in `mkdocs.yml` unless a cascade change is intentional.
- Prefer a component class over another global Material-theme override.
- Avoid new `!important` declarations and `:has()` selectors where normal
  specificity or a small JavaScript state class is sufficient.
- Do not add page-level `<style>` blocks. Move reusable styling into the
  appropriate component file.
- Run the architecture check and strict MkDocs build before publishing.

```bash
bash documentation/scripts/check-docs-css.sh
mkdocs build --strict
cd documentation/visual-tests && npm test
```

The guard intentionally allows the current legacy override count while
preventing it from growing. Existing overrides can then be reduced gradually
without placing the current design at risk.

The browser tests cover representative desktop and mobile routes, assert basic
layout invariants, and save full-page screenshots under `test-results/`. The
deployment workflow uploads those screenshots as an artifact so visual changes
can be reviewed without making pixel-level rendering differences block a
release.
