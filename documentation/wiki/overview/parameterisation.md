# Model Parameterisation

Each country model combines estimated behavioural relationships, demographic and alignment targets, tax-benefit donor data, and scenario assumptions. The file inventory and preparation links below refer to the UK model; the European models' inputs are maintained in the [SimPathsEU repository](https://github.com/simpaths/SimPathsEU/tree/main/input).

The [UK input directory](https://github.com/simpaths/SimPaths/tree/develop/input) contains the model's parameter files. The [parameter-loading code](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/data/Parameters.java) selects workbooks and worksheets according to the enabled modules and scenario settings.

## Tax and benefit inputs {#1-description-of-the-tax-and-benefit-system-display}

Tax-benefit outcomes are imputed from precomputed donor files: UKMOD supplies these for the UK model and EUROMOD for the European models. The policy schedule maps simulation years to policy systems. UK donor files are stored in `input/UKMOD/UKMODoutput/`; [Tax-Benefit Donors (UK)](../getting-started/data/tax-benefit-donors-uk.md) explains how to obtain and prepare them, and [Modifying Tax-Benefit Settings](../user-guide/tax-benefit-parameters.md) explains UK scenario configuration.

Training donor data are available in the repository for testing. They are not a substitute for authorised research data.

## Parameters and targets {#2-model-parameters}

<div class="technical-table" role="region" aria-labelledby="2-model-parameters" tabindex="0" markdown="1">

| File family | Role |
| --- | --- |
| `reg_*.xlsx` | Regression coefficients and, where provided, covariance matrices. Some workbooks contain multiple processes; `reg_RMSE.xlsx` contains residual error scales rather than a separate behavioural equation. |
| `align_*.xlsx` and `alignment_targets_*.xlsx` | Targets used to align selected simulated outcomes. |
| `projections_*.xlsx` | Demographic projections, including fertility and mortality inputs. |
| `scenario_*.xlsx` | Time paths and assumptions for the selected scenario. |
| `UKMODpolicySchedule.xlsx` | Maps simulation years to tax-benefit systems. |
| `social_care_parameters.xlsx` | Social-care policy parameters. |
| `economic_time_series.xlsx` | Price and wage indices, returns, debt costs and care wages. |
| `alignment_adjustment_series.xlsx` | Time-varying adjustments for alignment processes. |

</div>

The [Repository Guide's estimation reference](../developer-guide/repository-guide.md#data-pipeline-reference) maps UK Stata scripts to the outcomes they estimate. A script can update several workbooks, and a workbook can supply several processes.

For example, `reg_health.xlsx` supplies self-rated health and disability; `reg_health_wellbeing.xlsx` supplies SF-12 physical and mental health scores and life satisfaction; and `reg_financial_distress.xlsx` supplies financial distress.

## Interpreting estimates and references

The [SimPaths reference paper](https://microsimulation.pub/articles/00318) provides the model-level description. The [Labour supply](modules/labour-income.md) and [Mental health](modules/mental-health.md) pages explain the corresponding processes.

Richiardi and He's 2021 paper, “No one left behind: The labour supply behaviour of the entire Italian population”, describes the Italian labour-supply application.

Kopasker et al. (2024), [Evaluating the influence of taxation and social security policies on psychological distress](https://doi.org/10.1016/j.socscimed.2024.116953), describes the UK mental-health application.

## Reproducible use

Record the code revision, parameter-file versions, initial population, donor data, policy schedule and configuration with each analysis. After re-estimation, check coefficient names, outcome coding, worksheet names and covariance matrices against the Java loader, then perform the relevant [validation](../validation/index.md). Do not mix parameter files from different revisions without checking compatibility.

See [Uncertainty Analysis](../user-guide/uncertainty-analysis.md) for the distinction between coefficient resampling, simulation randomness and sensitivity to assumptions.
