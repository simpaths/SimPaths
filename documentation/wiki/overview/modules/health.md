---
title: Health
---

<div class="module-detail" markdown>

# Health

<p class="module-detail__lead">The health module projects self-rated physical health and determines whether a person is long-term sick or disabled. Psychological distress is documented separately because it combines a baseline health process with later economic transitions.</p>

## Physical health

Physical health is projected on a discrete five-point scale corresponding to self-reported survey responses from poor to excellent. Dynamics are estimated with an ordered probit model that distinguishes people who remain in continuous education from those who have left it.

For continuing full-time students, the equation conditions on gender, age, lagged benefit-unit income quintile, lagged physical health, region and year. For people who have left continuous education, it also includes education, lagged employment status and lagged benefit-unit composition.

## Long-term sickness and disability

Any person aged 16 or over who is not in continuous education may become long-term sick or disabled. The probability is described by a probit equation conditional on lagged disability, current and lagged physical health, gender, age, education, income quintile and lagged family demographics.

The disability decision is integrated into the physical-health implementation rather than run as a separate module.

## Process diagram

The diagram shows the combined physical-health and disability process.

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

SimPaths also projects psychological distress using the 12-item General Health Questionnaire. Its baseline estimates and subsequent adjustments for economic transitions and the Covid-19 period are described on the [Mental health](mental-health.md) page.

</div>
