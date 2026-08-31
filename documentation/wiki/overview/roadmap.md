# Development Roadmap

<div class="roadmap-page" markdown>

<p class="roadmap-lede">This roadmap lists SimPaths' current, upcoming, and longer-term development priorities. The timing and scope of later work will depend on research needs, data, and funding.</p>

<div class="roadmap-horizon" markdown>

<div class="roadmap-horizon__heading" markdown>

## Current priorities

Work is concentrated on validation, parameter uncertainty, and the relationship between employment histories and health.

</div>

<div class="roadmap-horizon__items" markdown>

<div class="roadmap-item" markdown>

### Validation against source data

Compare model estimates on simulated populations with their input-data equivalents to identify drift, implementation differences, and processes requiring recalibration. This tests whether estimated relationships remain intact once combined in the full simulation.

<div class="roadmap-meta" markdown>Issue: [#192](https://github.com/simpaths/SimPaths/issues/192)</div>

</div>

<div class="roadmap-item" markdown>

### Parameter uncertainty

Expose regression-coefficient bootstrapping through configuration, command-line options, and documentation so uncertainty can be applied consistently across repeated runs.

<div class="roadmap-meta" markdown>Issue: [#329](https://github.com/simpaths/SimPaths/issues/329)</div>

</div>

<div class="roadmap-item" markdown>

### Employment histories and health

Add modifiers estimated from longitudinal employment histories to the health modules, connecting accumulated labour-market experience with later physical and mental health outcomes.

<div class="roadmap-meta" markdown>Issue: [#143](https://github.com/simpaths/SimPaths/issues/143)</div>

</div>

</div>

</div>

<div class="roadmap-horizon" markdown>

<div class="roadmap-horizon__heading" markdown>

## Next priorities

The next stage extends behavioural specification, population representation, matching, performance, and model state.

</div>

<div class="roadmap-horizon__items" markdown>

<div class="roadmap-item" markdown>

### Labour supply, mental health, and childcare

Re-estimate labour supply with individual effects and incorporate mental health, Universal Credit, care time, and childcare costs where supported by the data.

<div class="roadmap-meta" markdown>Issues: [#191](https://github.com/simpaths/SimPaths/issues/191), [#193](https://github.com/simpaths/SimPaths/issues/193), [#433](https://github.com/simpaths/SimPaths/issues/433)</div>

</div>

<div class="roadmap-item" markdown>

### Migration and population representation

Define immigrant status consistently, clarify its relationship with ethnicity, and assess where it should enter behavioural processes.

<div class="roadmap-meta" markdown>Issues: [#303](https://github.com/simpaths/SimPaths/issues/303), [#304](https://github.com/simpaths/SimPaths/issues/304)</div>

</div>

<div class="roadmap-item" markdown>

### Regional matching and performance

Extend tax-benefit donor matching by Government Office Region and make partnership matching scalable through population partitions. Improve tax-donor parsing and yearly simulation performance.

<div class="roadmap-meta" markdown>Issues: [#156](https://github.com/simpaths/SimPaths/issues/156), [#157](https://github.com/simpaths/SimPaths/issues/157), [#253](https://github.com/simpaths/SimPaths/issues/253), [#301](https://github.com/simpaths/SimPaths/issues/301)</div>

</div>

<div class="roadmap-item" markdown>

### Alignment and model state

Estimate alignment adjustments once for reuse across scenarios, compare alternative alignment approaches, and make the initialisation and updating of lagged variables consistent.

<div class="roadmap-meta" markdown>Issues: [#137](https://github.com/simpaths/SimPaths/issues/137), [#139](https://github.com/simpaths/SimPaths/issues/139), [#119](https://github.com/simpaths/SimPaths/issues/119), [#273](https://github.com/simpaths/SimPaths/issues/273)</div>

</div>

</div>

</div>

<div class="roadmap-horizon" markdown>

<div class="roadmap-horizon__heading" markdown>

## Longer-term capabilities

These capabilities would materially extend the questions SimPaths can address. Their scope and timing are not yet fixed.

</div>

<div class="roadmap-horizon__items" markdown>

<div class="roadmap-item" markdown>

### Wealth across the life course

Represent the accumulation of assets and liabilities across the life course, extending current income and pension processes. This would support analysis of financial resilience, retirement, and intergenerational inequality.

</div>

<div class="roadmap-item" markdown>

### Unemployment and retirement transitions

Identify unemployment before labour-supply choices, incorporate health into the utility function, allow selected post-retirement employment, and re-estimate wages for the European models.

</div>

<div class="roadmap-item" markdown>

### Migration and synthetic populations

Develop explicit migration processes and starting populations that reduce dependence on restricted microdata where appropriate.

</div>

<div class="roadmap-item" markdown>

### Macroeconomic context and transfers between households

Represent wider economic conditions and transfers between households beyond the immediate benefit unit.

</div>

<div class="roadmap-item" markdown>

### Health and multidimensional wellbeing

Add multidimensional wellbeing outcomes, SIPHER-7 measures, and a mortality process that reflects health differentials.

<div class="roadmap-meta" markdown>Issue: [#505](https://github.com/simpaths/SimPaths/issues/505)</div>

</div>

</div>

</div>

<div class="roadmap-horizon roadmap-horizon--foundations" markdown>

<div class="roadmap-horizon__heading" markdown>

## Model foundations

Architecture, consistency, and documentation work supports every planning horizon.

</div>

<div class="roadmap-horizon__items" markdown>

<div class="roadmap-item" markdown>

### Structural redesign

Reorganise packages, separate model and experiment parameters, clarify alignment logic, redesign the labour-market class, centralise regressor definitions, and increase test coverage.

<div class="roadmap-meta" markdown>Issues: [#398](https://github.com/simpaths/SimPaths/issues/398), [#396](https://github.com/simpaths/SimPaths/issues/396), [#401](https://github.com/simpaths/SimPaths/issues/401), [#391](https://github.com/simpaths/SimPaths/issues/391), [#435](https://github.com/simpaths/SimPaths/issues/435)</div>

</div>

<div class="roadmap-item" markdown>

### Standardisation and code quality

Standardise transformed-variable names, regression and parameter names, missing-value conventions, and class-level documentation; resolve known inconsistencies in pension receipt and employment status.

<div class="roadmap-meta" markdown>Issues: [#408](https://github.com/simpaths/SimPaths/issues/408), [#410](https://github.com/simpaths/SimPaths/issues/410), [#400](https://github.com/simpaths/SimPaths/issues/400), [#476](https://github.com/simpaths/SimPaths/issues/476)</div>

</div>

<div class="roadmap-item" markdown>

### Documentation and transparency

Document input-data components, matching, interdependencies, health outcomes, and contributor testing requirements.

<div class="roadmap-meta" markdown>Issues: [#141](https://github.com/simpaths/SimPaths/issues/141), [#152](https://github.com/simpaths/SimPaths/issues/152)</div>

</div>

</div>

</div>

<div class="roadmap-contact" markdown>

## Suggest a priority or collaborate

To suggest a priority, contribute evidence, or discuss collaboration, [open a GitHub issue](https://github.com/simpaths/SimPaths/issues/new) or contact the SimPaths team at [info@simpaths.org](mailto:info@simpaths.org).

</div>

</div>
