# Development Roadmap

<div class="roadmap-page" markdown>

This roadmap gives researchers, users, and contributors a view of where SimPaths is heading, why the planned work matters, and how it may change the model. It deliberately groups related tasks into outcomes rather than reproducing the full development backlog.

Priorities can change as research needs, funding, data availability, and technical dependencies evolve. The roadmap therefore communicates direction and relative planning horizon rather than firm delivery commitments.

**Last reviewed:** August 2026<br>
**Proposed update cycle:** Quarterly

## Development direction

The current programme of work has four broad aims:

1. Broaden the life-course model by developing wealth, unemployment, migration, macroeconomic context, transfers between households, and additional health and wellbeing outcomes.
2. Improve behavioural and empirical realism through updated labour-supply estimates, richer employment histories, health-sensitive mortality, regional matching, and better representation of childcare and care.
3. Strengthen confidence in results through validation, parameter-uncertainty analysis, reproducibility checks, and clearer alignment methods.
4. Make SimPaths easier to maintain and extend by simplifying the architecture, standardising variables and parameters, improving performance, and expanding technical documentation and tests.

## Working on now

These activities are recorded as active work in the development project. Public issue state has also been checked so that completed work is not presented as ongoing.

### Validation against source data

Simulated outcomes need to reproduce the relationships and transitions found in the data used to estimate the model. Current work compares estimates evaluated on simulated populations with their input-data equivalents, helping the team identify drift, implementation differences, or processes that require recalibration.

<div class="roadmap-impact" markdown>
**Improves** Confidence that estimated processes behave as intended after they are integrated into a full simulation.
</div>

<div class="roadmap-meta" markdown>
Related: [test estimates on simulated and input data](https://github.com/simpaths/SimPaths/issues/192). A related task to [restore value checks in integration tests](https://github.com/simpaths/SimPaths/issues/432) has recently been completed.
</div>

### Parameter uncertainty

SimPaths can resample regression coefficients to represent uncertainty in estimated parameters, but this behaviour is not yet sufficiently visible or configurable for users running multiple simulations. Work is underway to expose bootstrapping more clearly through configuration, command-line options, and documentation.

<div class="roadmap-impact" markdown>
**Improves** More transparent uncertainty analysis and a lower risk of users interpreting results from non-bootstrapped runs as statistically complete.
</div>

<div class="roadmap-meta" markdown>
Related: [make parameter bootstrapping configurable](https://github.com/simpaths/SimPaths/issues/329).
</div>

### Employment histories and health

The health modules are being extended to represent how previous employment experiences affect later health. The planned modifiers are estimated using longitudinal causal methods and will connect labour-market trajectories more closely to physical and mental health outcomes.

<div class="roadmap-impact" markdown>
**Improves** Analysis of how employment transitions and accumulated labour-market experience contribute to health inequalities over the life course.
</div>

<div class="roadmap-meta" markdown>
Related: [employment-history effects in health modules](https://github.com/simpaths/SimPaths/issues/143).
</div>

## Working on next

The following areas are in the agreed backlog but are not recorded as active work. Their ordering is indicative and depends on data, estimation work, and other model changes.

### Labour supply, mental health, and childcare

The labour-supply model is due for re-estimation with individual effects and a richer representation of the circumstances shaping work decisions. Proposed additions include mental-health effects, the utility implications of Universal Credit, care time, and a more explicit treatment of childcare time and costs.

<div class="roadmap-impact" markdown>
**Enables** More credible analysis of employment transitions and of policies whose effects vary with health, caring responsibilities, and benefit receipt.
</div>

<div class="roadmap-meta" markdown>
Related: [re-estimate labour supply with individual effects](https://github.com/simpaths/SimPaths/issues/191), [include Universal Credit and mental-health effects](https://github.com/simpaths/SimPaths/issues/193), and [represent childcare time and costs](https://github.com/simpaths/SimPaths/issues/433).
</div>

### Migration and population representation

Planned work will clarify the definition of immigrant status, its relationship with ethnicity, and whether it should enter additional behavioural processes. This supports the longer-term migration and synthetic-population features described below.

<div class="roadmap-impact" markdown>
**Enables** More transparent population definitions and better analysis of heterogeneous outcomes across population groups.
</div>

<div class="roadmap-meta" markdown>
Related: [define immigrant status](https://github.com/simpaths/SimPaths/issues/303) and [evaluate its inclusion in model processes](https://github.com/simpaths/SimPaths/issues/304).
</div>

### Regional matching and large-population performance

Several planned changes address the quality and speed of matching. Tax-benefit donors could be matched by Government Office Region, while partnership matching could first operate within meaningful population partitions before considering a national pool. Data-parsing and yearly simulation performance are also under review.

<div class="roadmap-impact" markdown>
**Enables** Better regional policy analysis and more practical simulations using large populations or many repeated runs.
</div>

<div class="roadmap-meta" markdown>
Related: [regional tax-benefit matching](https://github.com/simpaths/SimPaths/issues/156), [scalable partnership matching](https://github.com/simpaths/SimPaths/issues/157), [faster tax-donor parsing](https://github.com/simpaths/SimPaths/issues/253), and [simulation performance](https://github.com/simpaths/SimPaths/issues/301).
</div>

### Alignment and model state

The team is considering how alignment adjustments can be estimated once and reused across scenarios, how alternative approaches affect outcome distributions, and how lagged variables should be initialised and updated. These are important foundations for interpretable counterfactual comparisons.

<div class="roadmap-impact" markdown>
**Enables** Cleaner comparisons between baseline and policy scenarios, with fewer unintended differences caused by alignment or state-management rules.
</div>

<div class="roadmap-meta" markdown>
Related: [logit alignment adjustments](https://github.com/simpaths/SimPaths/issues/137), [reusable alignment parameters](https://github.com/simpaths/SimPaths/issues/139), and consistent handling of [initial](https://github.com/simpaths/SimPaths/issues/119) and [period-by-period](https://github.com/simpaths/SimPaths/issues/273) lagged variables.
</div>

## Major capabilities in the pipeline

These items appear in the development project's **New features** pipeline. They represent intended directions of travel, but most do not yet have a public specification or committed delivery date.

### Wealth across the life course

A dedicated wealth module would extend SimPaths beyond current income and pension processes to represent how assets and liabilities develop across the life course. This would strengthen analysis of financial resilience, retirement, intergenerational inequality, and the distributional effects of policy.

<div class="roadmap-impact roadmap-target" markdown>
**Indicative target** August 2026. This timing should be reconfirmed before publication.
</div>

### Unemployment and retirement transitions

The proposed unemployment development includes identifying unemployed people before evaluating labour-supply choices, incorporating mental and physical health in the utility function, allowing some retired people to work, and re-estimating wages—particularly for the European models.

<div class="roadmap-impact" markdown>
**Enables** A more realistic account of worklessness, return-to-work decisions, health-related employment constraints, and work after retirement.
</div>

### Migration and synthetic populations

New migration processes and synthetic-population methods are being considered together. Synthetic populations could reduce dependence on restricted microdata for some applications, while explicit migration processes would improve population dynamics and country or regional adaptation.

<div class="roadmap-impact" markdown>
**Enables** More flexible starting populations, clearer demographic scenarios, and easier adaptation of SimPaths to new settings.
</div>

### Macroeconomic context and transfers between households

A macro module and explicit inter-household transfers would allow individual and household trajectories to respond to a richer economic environment and to resources exchanged beyond the immediate benefit unit.

<div class="roadmap-impact" markdown>
**Enables** Analysis of family support, redistribution between households, and scenarios involving wider economic conditions.
</div>

### Health and multidimensional wellbeing

The feature pipeline includes additional outcomes developed through SIPHER-7, multidimensional wellbeing measures, and a mortality process that reflects health differentials.

<div class="roadmap-impact" markdown>
**Enables** Analysis that goes beyond income and employment to capture multiple dimensions of wellbeing and the way health inequalities accumulate into mortality differences.
</div>

<div class="roadmap-meta" markdown>
Related: [health differentials in mortality](https://github.com/simpaths/SimPaths/issues/505).
</div>

## Strengthening the model foundations

Some of the most important development will not appear as a new simulated outcome. Two coordinated task forces focus on making the framework more reliable, understandable, and easier to extend.

### Structural redesign

The structural programme includes reorganising packages, separating model parameters from experiment settings, clarifying the location of alignment logic, redesigning the labour-market class, centralising regressor definitions, improving filter composition, and simplifying statistics calculations.

<div class="roadmap-impact" markdown>
**Enables** A clearer architecture, reducing the risk of unintended interactions and making it easier for new contributors to modify processes safely.
</div>

<div class="roadmap-meta" markdown>
Related: [package organisation](https://github.com/simpaths/SimPaths/issues/398), [parameter separation](https://github.com/simpaths/SimPaths/issues/396), [labour-market redesign](https://github.com/simpaths/SimPaths/issues/401), [regressor definitions](https://github.com/simpaths/SimPaths/issues/391), [filter composition](https://github.com/simpaths/SimPaths/issues/404), [test coverage](https://github.com/simpaths/SimPaths/issues/435), and [statistics helpers](https://github.com/simpaths/SimPaths/issues/436).
</div>

### Standardisation and code quality

The clean-up programme covers variable and codebook consistency, naming of transformed variables, regression and parameter naming, missing-value conventions, removal of unused methods, class-level documentation, and known inconsistencies such as pension receipt and employment status.

<div class="roadmap-impact" markdown>
**Enables** Easier tracing of a concept from source data and Stata estimates into Java processes and simulation outputs.
</div>

<div class="roadmap-meta" markdown>
Related: [variable codebook](https://github.com/simpaths/SimPaths/issues/428), [transformation naming](https://github.com/simpaths/SimPaths/issues/408), [parameter harmonisation](https://github.com/simpaths/SimPaths/issues/410), [class and method documentation](https://github.com/simpaths/SimPaths/issues/400), and [pension-income consistency](https://github.com/simpaths/SimPaths/issues/476).
</div>

### Documentation and transparency

Planned documentation work includes a clearer account of input-data components, matching, and interdependencies; documentation and codebook coverage for health outcomes; guidance on testing code updates; and improved comments in the Java source.

<div class="roadmap-impact" markdown>
**Enables** Researchers to understand required data, model interactions, and contribution checks without relying on informal knowledge from the core team.
</div>

<div class="roadmap-meta" markdown>
Related: [data components and interdependencies](https://github.com/simpaths/SimPaths/issues/141) and [EQ-5D documentation and codebook coverage](https://github.com/simpaths/SimPaths/issues/152).
</div>

## Recent progress

The March 2026 release standardised variable names, revised the Education, Health, Partnership, and Social Care modules, updated transition estimates and alignment targets, and introduced new documentation and debugging support. Alongside that release, recent work has expanded health, wellbeing, and disability outcomes, strengthened labour-market diagnostics, and made setup and execution more consistent. See the [2026.03.07 release notes](https://github.com/simpaths/SimPaths/releases/tag/2026.03.07) for details.

## How this roadmap is maintained

- The internal development project remains the operational source of truth.
- This public page summarises work at the level most useful to researchers and users.
- Public issue links are provided where they add useful technical detail.
- The page should be reviewed quarterly and after significant releases.
- Completed work should move into the release notes; this page should retain only a short progress summary.
- New-feature descriptions and delivery timing should be confirmed by the responsible development leads before publication.

## Suggest a priority or collaborate

Feedback from researchers and users helps the team understand which capabilities would be most valuable. Please [open a GitHub issue](https://github.com/simpaths/SimPaths/issues/new) or contact the SimPaths team at [info@simpaths.org](mailto:info@simpaths.org) if you would like to suggest a priority, contribute evidence, or discuss collaboration.

</div>
