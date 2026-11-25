---
title: Health
---

<div class="module-detail" markdown>

# Health

<p class="module-detail__lead">The health module projects self-rated health and determines whether a person is long-term sick or disabled. The equations and available health measures vary across country models. The specifications below describe the UK model.</p>

## Physical health

Self-rated health is projected on a five-point scale from poor to excellent. The current UK implementation uses a generalised ordered logit model (process H1), which allows relationships with the explanatory variables to differ across health thresholds.

The equation includes student status, gender, age, education, lagged SF-12 physical and mental health scores, lagged employment, income quintile, household composition and disability, as well as region, ethnicity and time effects.

## Long-term sickness and disability

For people who have reached the model's minimum age for this process and have left continuous education, a probit model (H2) determines long-term sickness or disability. Its covariates include lagged disability, lagged SF-12 physical and mental health scores, gender, age, education, lagged income quintile and household composition, region, ethnicity and time effects. Under intertemporal optimisation, this process is enabled when disability is included in the decision model.

Self-rated health and disability are updated together in the UK health process.

## Process diagram

The diagram shows the UK health and disability process.

<details class="module-process-diagram">
  <summary>View the health diagram</summary>
  <div class="module-process-diagram__meta">
    <span>Scroll to follow the complete sequence.</span>
    <a href="/figures/modules/health_module.png" target="_blank" rel="noopener">Open full resolution <span aria-hidden="true">&#8599;</span></a>
  </div>
  <div class="module-process-diagram__viewport" role="region" aria-label="Scrollable physical health implementation diagram" tabindex="0">
    <img src="/figures/modules/optimized/health-flow.png" alt="Physical health and disability implementation flow" width="1200" height="3588" loading="lazy" decoding="async">
  </div>
</details>

## Psychological distress

The UK model also projects psychological distress using the 12-item General Health Questionnaire. Its baseline estimates and subsequent adjustments for economic transitions and the Covid-19 period are described on the [Mental health](mental-health.md) page.

## Other health and wellbeing measures

Alongside categorical self-rated health, the UK model projects continuous SF-12 physical and mental component scores (PCS and MCS) and life satisfaction. Their equations are supplied in `reg_health_wellbeing.xlsx`.

See the [UK health estimation script](https://github.com/simpaths/SimPaths/blob/develop/input/regression_files/RegressionEstimates/05_reg_health.do) and [parameter-loading code](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/data/Parameters.java) for the estimated specifications and their implementation.

</div>
