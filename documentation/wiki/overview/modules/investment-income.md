---
title: Investment income
---

<div class="module-detail" markdown>

# Investment income

<p class="module-detail__lead">The investment-income module projects private pension income and returns on assets. Its treatment depends on whether a model variant projects wealth explicitly or uses regression-based proxies for non-labour income.</p>

## Retirement

Retirement can occur for adults who have reached the model's minimum retirement age. Its treatment differs according to whether forward-looking expectations are implicit or explicit.

With implicit expectations, entry to retirement is described by a probit model. Explanatory variables include age, education, employment, income, health and eligibility for the state pension, with the specification depending on the country model. For couples, the partner's circumstances can also enter the equation.

With explicit forward-looking expectations, retirement is treated as a control variable. Retired people may receive pension income under either specification.

### Retirement alignment

When retirement alignment is enabled, the intercept of the retirement probit models is shifted so that the share of retired people in the eligible age range follows targets computed from the initial populations. The adjustment is estimated through the final alignment year and then retained for subsequent years. This alignment applies to the probit specification; retirement chosen through forward-looking optimisation is determined by the decision model.

## Private pension income

When pension income is projected using regression equations, separate processes describe people continuing in retirement and new retirees. In the UK specification, a linear regression for continuing retirees conditions on age, education, lagged household composition, lagged health, lagged pension income, region and year.

For people entering retirement, a logit model first determines whether private pension income is received. It conditions on state pension age, education, lagged employment status, lagged household composition, lagged health, lagged hourly wage potential, region and year. A linear regression using the same observed characteristics then projects the amount.

Under the UK forward-looking specification, an assumed share of benefit-unit wealth is converted at retirement into a life annuity, or a joint-life annuity for couples. Annuity rates are actuarially fair given cohort-specific mortality rates and an assumed internal rate of return.

The UK model also supports separate private-pension wealth projections. With these enabled and forward-looking optimisation disabled, pension income is obtained from the pension-access and in-payment processes. These use the person's pension state and are implemented in [`PrivatePension`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/model/person/PrivatePension.java).

## Capital income

When non-pension wealth is not projected explicitly, capital income is estimated using equations for receipt and amount, for people who have reached the minimum age for income projection. In the UK specification, the receipt equation is a logit model that includes age, lagged health, lagged gross employment and capital income, region and year. For people who are not in continuous education, education, lagged employment and lagged household composition are also included.

For people projected to receive capital income, the amount is described by linear regressions. For students, these condition on gender, age, lagged health, lagged gross employment income, lagged capital income, region and year. For non-students, education, lagged employment and lagged household composition are added.

Under the UK forward-looking specification with explicit non-pension wealth, capital income is the product of net assets and an assumed return. The return varies by year and by benefit-unit net wealth, $w_{i,t}$:

$$
r_{i,t} =
\begin{cases}
r_{a,t}, & w_{i,t} \geq 0, \\
r_{dl,t} + \left(r_{du,t} - r_{dl,t}\right)\phi_{i,t}, & w_{i,t} < 0.
\end{cases}
$$

Here, $i$ denotes the benefit unit and $t$ time. The bounded ratio $0 \leq \phi_{i,t} \leq 1$ represents benefit-unit debt relative to full-time potential earnings. Assuming $r_{du,t} \geq r_{dl,t}$ imposes a soft constraint under which interest rates rise with indebtedness.

When separate non-pension wealth projections are enabled without forward-looking optimisation, financial income and housing returns are calculated from the corresponding asset balances. The specification is selected in [`BenefitUnit.setInvestmentIncomeAnnual()`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/model/BenefitUnit.java).

</div>
