# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Important: Directory Path Contains Spaces

The project root is `C:\MyFiles\99 DEV ENV\JAS-MINE\SimPaths`. The path contains spaces, so always wrap paths in double quotes in shell/Bash commands, e.g.:

```bash
cd "C:\MyFiles\99 DEV ENV\JAS-MINE\SimPaths"
mvn -f "C:\MyFiles\99 DEV ENV\JAS-MINE\SimPaths\pom.xml" compile
```

## Build & Run Commands

```bash
# Compile only
mvn compile

# Run unit tests (excludes integration tests)
mvn test

# Run a single test class
mvn test -Dtest=MahalanobisDistanceTest

# Run a single test method
mvn test -Dtest=PersonTest#methodName

# Run integration tests (requires pre-built JARs)
mvn failsafe:integration-test

# Build shaded JARs (produces singlerun.jar and multirun.jar in project root)
mvn package

# Run simulation headless via multirun.jar (uses config/default.yml)
java -jar multirun.jar -config config/default.yml

# Run database setup only
java -jar multirun.jar -DBSetup -config config/default.yml

# Run with GUI (single run)
java -jar singlerun.jar
```

Java 19 is required (configured in pom.xml). Dependencies are pulled from Maven Central and JitPack (for JAS-mine).

## Architecture Overview

SimPaths is a **dynamic microsimulation model** built on the [JAS-mine](https://github.com/jasmineRepo) simulation framework. It projects individual and household life histories year-by-year across domains: labour market, health, demographics, social care, and taxes/benefits.

### Simulation entity hierarchy

```
Household          — groups BenefitUnits sharing a dwelling (parent–adult-child links)
  └─ BenefitUnit  — the tax/benefit assessment unit (couple or single + dependent children)
       └─ Person  — the individual agent (all life-course state is stored here)
```

All three are JPA `@Entity` classes persisted via Hibernate/H2. Each carries a `PanelEntityKey` (id + simulation_time + simulation_run).

### Simulation lifecycle

`SimPathsModel` (extends `AbstractSimulationManager`) owns the main simulation loop. `SimPathsModel.buildSchedule()` defines the ordered events it fires each year, which update entities via `EventListener.onEvent()`. From the second simulated year, the sequence is:

1. `BenefitUnit.Update`, then `Person.Update`: last year's values are stored as lags before anyone changes benefit unit.
2. Ageing (at 18 a person gets their own benefit unit), retirement, education, leaving the parental home, home ownership, physical health.
3. Partnership formation and dissolution, union matching, fertility.
4. Social care (if enabled), unemployment, then labour supply and tax-benefit imputation (`LabourMarketUpdate`), followed by benefit receipt, wealth and consumption.
5. `CalculateChangeInEDI`, then `CalculateAtRiskOfPoverty`: current-year income and poverty status.
6. Financial distress, mental health (GHQ-12), SF-12 and life satisfaction.
7. Mortality, population alignment, inheritance, EQ-5D, output variables.

`SimPathsCollector` then collects statistics, ordered after the model's events. The first simulated year (`startYear`) runs a reduced schedule (`firstYearSched`) with only mortality, population alignment, inheritance and bookkeeping. The behavioural equations therefore first run in the second simulated year.

### Key classes to know

| Class | Role |
|---|---|
| `SimPathsModel` | Orchestrates yearly event schedule; holds all population lists |
| `SimPathsStart` | Entry point for single runs (GUI or headless) |
| `SimPathsMultiRun` | Entry point for multi-run / sensitivity analysis |
| `SimPathsCollector` | Collects and exports output statistics each year |
| `Parameters` | Static store for all model parameters and regression coefficients loaded from Excel |
| `ManagerRegressions` | Routes regression evaluation calls to the correct JAS-mine regression objects |
| `Person` / `BenefitUnit` | Core simulation agents — most behavioural logic lives here |

### Tax-benefit imputation (`model/taxes/`)

Tax and benefit outcomes are not calculated analytically. Instead, the model uses **statistical matching**: simulated households are matched to a pre-built donor database derived from UKMOD output. `TaxDonorDataParser` builds this database; `DonorTaxImputation` performs the matching using a nearest-neighbour key function (`KeyFunction1`–`KeyFunction4`).

### Intertemporal optimisation (`model/decisions/`)

An optional backward-induction module solves for optimal lifetime consumption and labour supply. `ManagerSolveGrids` runs the solution over a multi-dimensional state-space grid (age, health, education, pension, region, etc.). `ManagerPopulateGrids` sets up the grid geometry. This is computationally expensive and disabled by default (`enableIntertemporalOptimisations: false`).

### Regression system

All behavioural equations are estimated externally and loaded at startup from Excel files in `input/`. `Parameters` loads them via JAS-mine's `ExcelAssistant`/`MultiKeyCoefficientMap`. `RegressionName` (enum) names every equation; `ManagerRegressions` dispatches calls by type (linear, probit, ordered probit, multinomial logit, etc.).

### Alignment

Several demographic processes are aligned to external targets (ONS projections, LFS shares) via `ActivityAlignmentV2`, `FertilityAlignment`, `PartnershipAlignment`, `InSchoolAlignment`, `RetirementAlignment`, `SocialCareAlignment`. Alignment factors are exported to `AlignmentStatistics.csv` each run.

Retirement alignment (`alignRetirement`, default `false`) shifts the R1a/R1b probit index in `Person.updateRetirementStatus` so that the share of retired persons among those aged `MIN_AGE_TO_RETIRE` (50) or older meets `input/alignment_files/alignment_targets_retirement.xlsx`. `RetirementAlignment.inTargetPopulation` defines that population for `alignRtrdSimShare` in `AlignmentStatistics.csv`. The alignment itself runs just before the Retirement process, when `labC4` is still null (it is `@NullInitialised`, unlike in SimPathsEU), so like `Person.updateRetirementStatus` it reads the lagged status `labC4L1` and takes everyone aged 50+ as the population. Code that ports alignment logic from SimPathsEU must account for this. `input/alignment_files/DoFilesTarget/05_retirement_targets_initpopdata.do` computes the target from the initial populations on the persons aged 50+ with a labour status, so its `min_age_to_retire` must equal `MIN_AGE_TO_RETIRE`. The adjustment lives in the `retirement_adjustment` sheet of `alignment_adjustment_series.xlsx` (`TimeSeriesVariable.RetirementAdjustment`) and, like `students_adjustment`, is a direct probit shift that is not appended to the regression coefficients. The search runs up to `RETIREMENT_ALIGNMENT_END_YEAR` (2023, settable through `model_args`) and later years keep its last adjustment, so in a run starting in 2023 the one-year catch-up from the starting population is applied in every later year; leave it off for long projections.

### Configuration

Runs are configured via YAML files in `config/`. `default.yml` documents all available keys with their defaults. CLI flags override YAML values. `SimPathsMultiRun` reads the YAML and reflectively sets fields on `SimPathsModel`, `SimPathsCollector`, and `Parameters`.

### Output

Output is written to `output/<run-timestamp>/csv/` by `SimPathsCollector`. Key files:
- `WealthIncomeStatistics.csv` — income and wealth (Gini, percentiles, plus income and wealth by age band)
- `DemographicStatistics.csv` — demographics by age band (partnership, dependent children, population counts)
- `HealthStatistics.csv` — population health by age band (self-rated health, disability shares)
- `LabourStatistics.csv` — labour market transitions, participation, full-time/part-time shares by age band
- `AlignmentStatistics.csv` — alignment diagnostics
- `WellbeingByGender.csv` — wellbeing and quality of life (SF-12, GHQ-12, EQ-5D), ages 25–64, three rows per year (Total/Male/Female)

The four age-band outputs share one population traversal per year via `AgeBandAggregates`; the bands are 18–29, 30–54 and 55–74.

### Integration tests

`RunSimPathsIntegrationTest` runs the full simulation end-to-end using the built JARs and compares CSV output against reference files in `src/test/java/simpaths/integrationtest/expected/`. If a substantive change shifts the output, update the expected files and commit them. See `documentation/wiki/developer-guide/how-to/integration-tests.md` for how to run it (`mvn package` first; `mvn test` does not run it) and how to update the expected files.

## Domain Knowledge

This section records design principles and non-obvious rationale accumulated through development. Update it as new insights emerge.

### Price levels: SimPaths vs. the tax donor database

All SimPaths financial variables are stored in **real 2015 prices** (`BASE_PRICE_YEAR`). The tax donor database, however, is denominated in **nominal prices of the respective policy year**.

Tax donor matching therefore requires a two-step price bridge:
1. Inflate SimPaths income to policy-year prices before matching.
2. Deflate the imputed financial values from the matched donor record back to 2015 prices.

The default uprating series is `TimeSeriesVariable.Inflation`, sourced from the `inflation` worksheet in `input/economic_time_series.xlsx`. An alternative option (added 2026-04) allows wage growth to be used instead of price growth for the initial matching step, controlled via a config flag.

### Lagged regressors are kept on the person

The equations were estimated with lags defined per person (`xtset idperson swv` in Stata), so a lagged regressor follows the person, not their current benefit unit. Each lag is an `@Lag` field on `Person`, filled by a private `get*ForLag()` getter in `Person.Update`, before ageing or partnership changes move anyone to a new unit. If a person has no history yet (first simulated year, newborns, immigrants), the read-time getter falls back as shown below. The copy constructor copies all of these lags.

| Lag | `Person` field | Captured from the unit's | Under 18 | No personal history |
|---|---|---|---|---|
| Income quintile | `yHhQuintilesMonthC5L1` | `yHhQuintilesMonthC5` | parents' value | unit's lag → Q3 |
| Home ownership | `demPrptyFlagL1` | stored lag `wealthPrptyFlagL1` | parents' value | not owned |
| Number of children | `numberChildrenAllL1`, `numberChildren02L1` | current counts | 0 | unit's lag → 0 |
| Household composition | `demCompHhC4L1` | `demCompHhC4` | `SingleNoChildren` | unit's lag → `SingleNoChildren` |
| At-risk-of-poverty flag | `yPvrtyFlagL1` | `yPvrtyFlag` | parents' value | unit's lag → no transition |
| Equivalised disposable income | `yDispEquivYearL1` | stored lag `yDispEquivYearL1` | parents' value | unit's lag → no change |

- **Capture order.** `BenefitUnit.Update` runs before `Person.Update` and nulls the unit's `@NullInitialised` fields (e.g. `wealthPrptyFlag`, `yDispEquivYear`). For those, copy the unit's stored lag, not its current value. Check this when adding a new lag.
- **Under 18.** Children and household composition use 0 children and `SingleNoChildren` below `Parameters.AGE_TO_BECOME_RESPONSIBLE`. The other lags use the parents' unit's value.
- **Home ownership.** `demPrptyFlagL1` is a nullable `Boolean`; read it with `Boolean.TRUE.equals(...)`.
- **Poverty and income change.** These regressors compare the person's lag with the current unit's current value (`getYPvrtyFlag()`, `getEquivalisedDisposableIncomeYearly()`).

Unit tests: `src/test/java/simpaths/model/person/Lagged*Test`.

Whether the education equations (E1a/E1b) should use parental rather than own-unit income is a separate, unresolved specification question (issue #543); it needs consistent re-estimation and Java changes.

### Current-year poverty status is classified mid-year

`BenefitUnit.yPvrtyFlag` is 1 when equivalised disposable income is below 60% of the weighted median. The flag used to be set only by the collector, after all of the year's events. So `FinancialDistress` and the mental-health and wellbeing equations (`HM1`/`HM2`, `MCS`/`PCS`, `DLS`) read last year's flag, or no flag for a unit formed that year. Entry into and exit from poverty therefore never switched on.

`SimPathsModel.Processes.CalculateAtRiskOfPoverty` now runs right after `CalculateChangeInEDI` and before `FinancialDistress`. It calls `collector.calculateAtRiskOfPoverty()` and throws if no collector is attached. Any new process that reads the current poverty flag must be scheduled after this event. Unit tests don't run the yearly schedule, so only the integration test checks this ordering.

### Equivalence scale

`BenefitUnit.getEquivalisedWeight()` and `Household.getEquivalisedDisposableIncomeYearly()` must both use the OECD scale: 1.0 for the first adult, 0.5 for each further person aged 14+, 0.3 per child under 14 (issue #541).
