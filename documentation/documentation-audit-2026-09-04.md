# Documentation audit, 4 September 2026

## Scope and source baseline

This pass corrects documentation, examples, duplication and rendering without redesigning the website. The grey navigation, CSS, page templates, homepage composition, research-card styling, roadmap and diagram widgets are unchanged.

Technical descriptions were checked against committed development revision [b223738b9cdf1d814cc3c6f09b04bc4930d3c667](https://github.com/simpaths/SimPaths/tree/b223738b9cdf1d814cc3c6f09b04bc4930d3c667), dated 28 August 2026. Uncommitted work in the main development checkout was not used as published-model evidence.

The source review covered the Java entry points, configuration, parameter loader and regression dispatch, relevant Person methods, entity mappings, collector and matching implementation, and the committed UK Stata estimation/alignment scripts. The variable codebook, schedule and Stata-parameter workbooks were inspected read-only as cross-checks. Workbooks are not assumed to override code where they disagree.

No model code, data, estimation output or workbooks were changed. The Stata connector was unavailable, so this was a static source review, not a re-estimation or numerical validation exercise.

## Substantive corrections

| Topic | Evidence and correction |
| --- | --- |
| Health H1 | `Parameters.java` constructs a generalised ordered logit for H1; `05_reg_health.do` estimates the five-category self-rated-health outcome using `gologit2`. Corrected the ordered-probit description and the claim that students and other adults use separate H1 equations. |
| Other health outcomes | Distinguished H1/H2 in `reg_health.xlsx` from SF-12 PCS/MCS and life satisfaction in `reg_health_wellbeing.xlsx`, and financial distress in its own workbook. |
| Education E2 | `01_reg_education.do` and the Java loader use generalised ordered logit, not ordered probit. E1a/E1b remain probit. Corrected covariates and eligibility descriptions against `Person.inSchool()`. |
| Regression inventory | Replaced the duplicated, incomplete workbook list with input-file families and links to the canonical repository inventory and loader. Reconciled the Repository Guide's script table with the numbered scripts actually present. |
| Estimation pipeline | Identified `00_master_regression_estimates.do` and the configured output folders. Removed the implication that estimation automatically replaces the Java input files. |
| Multi-run execution | Described sequential repetitions, not parallel execution or automatic aggregation. Clarified wrapper/model configuration precedence and seed interpretation. |
| Uncertainty | `Parameters.bootstrapAll` is static final and true in the checked revision. Changing seeds can change coefficients and other simulation draws; a YAML key is not a reliable way to disable this source-level setting. |
| Single-run entry point | Documented setup/run modes as mutually exclusive switches, saved country/year metadata, supported options and the YAML route for an explicitly configured single repetition. |
| Persistence/output | Replaced older generic persistence instructions with current embedded-key/relationship mappings and collector examples. Distinguished CSV/database export from population persistence. Removed the unsupported universal CSV run-suffix claim. |
| Matching | Replaced Demo07's methods with a source excerpt from `UnionMatching.evaluateGM()`, and explained scoring separately from applying a match. |
| Country coverage | Removed the obsolete readiness split on Model Description. The known country implementations are named without claiming equal validation or research readiness. A current readiness matrix still needs maintainer confirmation. |
| Bibliography | Research now includes the Igelström et al. 2025 EJPH conference abstract already featured on the homepage. How to Cite retains the primary citation and links to Research instead of maintaining another full list. |

The added EJPH entry was checked against the [publisher's record](https://academic.oup.com/eurpub/article/doi/10.1093/eurpub/ckaf161.076/8301736). It is labelled a conference abstract, not a full research article.

## Structure and reader guidance

- Rewrote Single Runs, new-variable, GUI-parameter and GitHub instructions around SimPaths.
- Reworked the JAS-mine architecture, startup and multi-run pages to explain distinct responsibilities and link to the operational guides.
- Added a visible starting route to the Developer Guide and practical output-selection guidance to Statistical display.
- Kept conceptual Alignment and Statistical Package material, with SimPaths-specific context.
- Replaced Input Data's duplicated child-page text with a routing overview.
- Replaced the old aggregate Simulated Modules page with an eleven-module index, preserving the route and its main numbered anchors.
- Replaced the unused parameter-page placeholder with links to the appropriate parameter interfaces.
- Removed editorial screenshot placeholders and replaced relevant code screenshots with copyable examples. GUI screenshots remain where they show the interface.
- Replaced the malformed generic firm/worker Mermaid content with the actual SimPaths entity hierarchy and navigation example.
- Corrected heading levels, list rendering, browser titles, selected spelling inconsistencies and stale wiki references.
- Kept page routes and explicitly retained useful existing anchors on rewritten reference pages. The local link checker verifies links present in the current site; it does not promise preservation of every historical external deep link.

Several supplied review findings were already fixed before this pass: Validation's list nesting and unfinished item, the Model overview's template/reading measure, and its redundant table of contents. These were regression-tested rather than redesigned.

## Checks

- `mkdocs build --strict`: passed.
- `python3 documentation/scripts/check-docs-content.py`: passed across 66 HTML pages and 6,262 internal links. Checks page titles, H1 counts, relative targets/anchors and selected editorial placeholders.
- CSS architecture guard: passed, unchanged at 3,770 lines and 251 important declarations.
- Desktop/mobile Playwright suite: 77 passed; 3 existing desktop-only tests skipped on mobile.
- Visual review: Single Runs on the live local preview, plus desktop captures of Single Runs, Querying the Database and Health.
- `git diff --check`: passed.

The new content/link check is included in the documentation deployment workflow. The browser suite now also covers eight rewritten guides and the corrected scientific labels, examples and bibliography routing.

An initial browser-test attempt encountered an unresponsive preview server. Restarting that local server resolved the connection failures; the subsequent suite passed.

## Matters still requiring confirmation or separate implementation work

1. Country readiness: confirm available research versions and the status of Germany, Spain and Sweden before revising the homepage's country-status sentence. The existence of an enum or directory is not evidence of a validated research release.
2. Estimation orchestration: the regression master contains ordering/dependency concerns, and some later scripts are marked as not yet refactored. The docs now require checking paths and prerequisites; the Stata pipeline itself has not been repaired or executed.
3. Javadoc deployment: the checked workflow gates publication with `if: github.event.push`. Its actual publication behaviour needs a separate workflow check; the API page no longer assumes that generating Javadoc proves publication.
4. Historical diagrams: the health and education text now specifies the checked implementation and warns that historical diagrams/releases may differ. Diagram assets were not redrawn or independently validated process by process.
5. Research verification: executable commands and model descriptions were checked statically. Training simulations, end-to-end research-data preparation, coefficient re-estimation and numerical validation were not run in this documentation-only pass.

This audit does not certify every scientific statement on the site or establish the status of unpublished model work.
