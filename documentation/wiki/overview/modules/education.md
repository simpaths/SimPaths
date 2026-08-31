---
title: Education
---

<div class="module-detail" markdown>

# Education

<p class="module-detail__lead">The education module determines transitions into and out of student status. Students do not enter the labour-supply module; when they leave education, their education level is evaluated and they may enter employment.</p>

## Student status

People leave continuous full-time education within an assumed age band, set to 16-29 in the UK parametrisation. Within that range, a probit model describes the probability of remaining in continuous education, conditional on gender, age, the education levels of the person's mother and father, region and year. Anyone still in continuous education above the upper age limit leaves.

People who were not students in the preceding period may re-enter education. The re-entry equation conditions on gender, age, lagged education, lagged employment status, the lagged number of children in the household, the lagged number of children aged 0-2, parental education, region and year. A person who returns to education can leave again in a subsequent year. The research wiki describes a UK re-entry age band of 16-45; the current implementation no longer applies that upper-age condition and instead excludes people who were already retired in the preceding period.

## Education level

When a person ceases to be a student, education level is assigned using an ordered probit model conditional on gender, age, parental education, region and year. For people leaving education after returning to study, education may remain unchanged or increase, but cannot decrease.

## Process diagram

The source diagram records the decisions used to update student status and education level.

<details class="module-process-diagram">
  <summary>View the education diagram</summary>
  <div class="module-process-diagram__meta">
    <span>Scroll to follow the complete sequence.</span>
    <a href="/figures/modules/education_module.png" target="_blank" rel="noopener">Open full resolution <span aria-hidden="true">&#8599;</span></a>
  </div>
  <div class="module-process-diagram__viewport" role="region" aria-label="Scrollable education implementation diagram" tabindex="0">
    <img src="/figures/modules/optimized/education-flow.png" alt="Education implementation flow" width="1200" height="3700" loading="lazy" decoding="async">
  </div>
</details>

</div>
