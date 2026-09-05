# Funding interaction colours

Checked 5 September 2026. These are small hover and keyboard-focus accents, not reproductions of the funders' complete visual identities. Resting rows remain neutral. Grant titles, dates, links and status do not depend on colour.

The `data-funder` values in `wiki/funding/index.md` map to the CSS tokens in `wiki/assets/css/06-page-sections.css`. Grants from the same funder share a token. An unrecognised funder falls back to neutral styling.

| Funder / programme | Accent | Evidence and choice |
| --- | --- | --- |
| NIHR | `#0051c2` | Blue Flame in the current NIHR digital palette; its Navy `#1c285e` is used for light-mode text. Checked the digital palette on page 34 of the [2026 brand guide](https://nihr.widen.net/s/zxwcfsdmhc/nihr_brand-guidelines_aw), linked from [NIHR SPHR's brand-refresh guidance](https://sphr.nihr.ac.uk/nihr-brand-refresh-guidance-for-sphr-members/). This is the refreshed palette, not the older navy. |
| Horizon Europe | `#003399` | EU-emblem blue, present in the [European Commission logo](https://research-and-innovation.ec.europa.eu/themes/contrib/oe_theme/dist/ec/images/logo/positive/logo-ec--en.svg) on the [official programme page](https://research-and-innovation.ec.europa.eu/funding/funding-opportunities/funding-programmes-and-open-calls/horizon-europe_en). |
| UKRI PHI / Policy Modelling for Health | `#f0d764` | Golden-yellow pentagon on the [programme website](https://www.phiuk.org/policy-modelling-for-health), sampled from its SVG fill. This is the programme's identity, not a claim that all UKRI programmes use gold. |
| CHANSE/NORFACE | `#42bccd` | Turquoise used on the [CHANSE website](https://chanse.org/). The joint award uses this programme accent, not an invented combination of two brands. |
| INAPP | `#18376e` | Approximate navy sampled from the INAPP wordmark in its [official public letter](https://www.inapp.gov.it/wp-content/uploads/2023/01/Lettera-di-presentazione_1.pdf), page 1. The logo is rasterised, so this is not asserted to be an exact brand-guide specification. |
| Health Foundation | `#de0031` | Red sampled from its [published RGB logo](https://healthequals.org.uk/wp-content/uploads/2022/11/THF_Logotype_2015_RGB-1.jpg) on the Foundation's [Health Equals campaign website](https://healthequals.org.uk/). The main health.org.uk site blocked direct asset access; this is the Foundation logo, not the campaign's orange. |
| JPI More Years, Better Lives | `#3c76bb` | Blue fill in the [official logo SVG](https://jp-demographic.eu/wp-content/themes/Theme-JP-demographic/assets/images/logo-jpd.svg). The [WELLCARE project record](https://www.iser.essex.ac.uk/research/projects/caring-over-the-lifecycle-the-roles-of-families-and-welfare-states-today-and-into-the-future-wellcare) identifies JPI MYBL as the relevant programme. |
| ERC | `#ff7d00` | Orange used on the [ERC website](https://erc.europa.eu/homepage). |
| ESPON | `#63b9ea` | Light blue in the visual identity published by its designer, [BGRAPHIC](https://bgraphic.dk/en/cases/espon-visual-identity/). The official ESPON site blocked direct visual access. This is an accent in the published palette, not its dark navy wordmark colour. |

Brighter colours have darker, hue-related text variants in light mode so titles and labels remain readable. Dark mode uses a lightened version of the accent. The same tint, text, badge and arrow treatment applies on hover and keyboard focus; keyboard focus also retains its outline. The browser tests check contrast against the composited backgrounds, unchanged row geometry, neutral resting colours, and the NIHR mapping across all three awards.
