#!/usr/bin/env bash
set -euo pipefail

styles=(
  documentation/wiki/assets/css/01-foundation.css
  documentation/wiki/assets/css/02-shell-navigation.css
  documentation/wiki/assets/css/03-content.css
  documentation/wiki/assets/css/04-landing-components.css
  documentation/wiki/assets/css/05-site-chrome.css
  documentation/wiki/assets/css/06-page-sections.css
  documentation/wiki/assets/css/07-home.css
)

max_file_lines=1200
max_total_lines=4500
max_important=300
max_has_selectors=40

for style in "${styles[@]}"; do
  if [[ ! -s "$style" ]]; then
    echo "Missing or empty stylesheet: $style" >&2
    exit 1
  fi

  lines=$(wc -l < "$style")
  if (( lines > max_file_lines )); then
    echo "$style has $lines lines; split it before adding more rules." >&2
    exit 1
  fi

  mkdocs_path=${style#documentation/wiki/}
  if ! grep -Fq "$mkdocs_path" mkdocs.yml; then
    echo "$style is not loaded by mkdocs.yml." >&2
    exit 1
  fi
done

if [[ -e documentation/wiki/assets/css/extra.css ]]; then
  echo "Do not recreate extra.css; use the component stylesheets." >&2
  exit 1
fi

total_lines=$(wc -l "${styles[@]}" | awk 'END { print $1 }')
important_count=$(grep -ho '!important' "${styles[@]}" | wc -l | tr -d ' ')
has_count=$(grep -Fho ':has(' "${styles[@]}" | wc -l | tr -d ' ')

if (( total_lines > max_total_lines )); then
  echo "Documentation CSS has grown to $total_lines lines." >&2
  exit 1
fi

if (( important_count > max_important )); then
  echo "Documentation CSS now contains $important_count !important declarations." >&2
  exit 1
fi

if (( has_count > max_has_selectors )); then
  echo "Documentation CSS now contains $has_count :has() selectors." >&2
  exit 1
fi

echo "CSS architecture check passed: $total_lines lines, $important_count !important declarations, $has_count :has() selectors."
