---
title: Consumption
---

<div class="module-detail" markdown>

# Consumption

<p class="module-detail__lead">Given disposable income and household composition, the consumption module projects benefit-unit expenditure. Its relationship with saving and asset accumulation depends on the model specification. The alternatives below describe the UK model.</p>

## Non-discretionary expenditure

The model projects formal social-care and formal childcare costs. Social-care costs combine projected hours of formal care with assumed hourly wage rates for social-care workers.

Childcare costs are estimated with a double-hurdle model. A probit equation describes whether formal childcare costs are incurred, and a linear least-squares equation describes their amount when they occur. Both equations include the number and ages of dependent children, adults' partnership and employment status, whether any adult has higher education, region and year.

## Discretionary consumption

The UK model provides three approaches to discretionary consumption, depending on whether forward-looking optimisation and non-pension wealth projections are enabled.

With neither enabled, consumption follows an income-based rule. Annual disposable income is equivalised using the modified OECD scale. For benefit units whose reference person is retired, equivalised consumption equals equivalised disposable income. For other benefit units, it is adjusted by an assumed saving rate.

With non-pension wealth projections enabled and forward-looking optimisation disabled, a regression equation (FW1a) projects discretionary consumption. A residual draw represents variation around the predicted value. The resulting expenditure enters the calculation of non-pension wealth.

With explicit forward-looking expectations, employment and discretionary consumption are chosen jointly. The model solves the lifetime decision problem and stores solutions in a look-up table. For discretionary consumption, the table records the ratio of consumption to cash on hand: net wealth, disposable income and available credit, less non-discretionary expenditure. The ratio is bounded between zero and one, which assists numerical solution and the evaluation of policy counterfactuals.

## Asset accumulation

Net wealth connects decisions across time when forward-looking expectations are explicit. In most periods it follows the accounting identity

$$
w_{i,t} = w_{i,t-1} + y_{i,t} - c_{i,t} - \bar{c}_{i,t},
$$

where $w_{i,t}$ is the net wealth of benefit unit $i$ in period $t$, $y_{i,t}$ disposable income, $c_{i,t}$ discretionary consumption and $\bar{c}_{i,t}$ non-discretionary expenditure. At retirement, if $w_{i,t} > 0$, a fixed share of wealth is converted into a life annuity.

Separate asset projections distinguish housing, mortgages, financial assets, unsecured debt and private pensions. Non-pension wealth is updated using income and expenditure, while housing and debt processes determine its composition. Pension wealth is projected separately. These processes are controlled by the non-pension and pension wealth settings.

## Homeownership

Homeownership is represented under both treatments of wealth. Without separate non-pension wealth projections, a probit equation evaluated for the benefit-unit reference person determines homeownership. With these projections enabled, homeownership is carried forward from the housing state and updated by the housing-wealth process.

The consumption, investment-income and homeownership calculations are implemented in [`BenefitUnit`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/model/BenefitUnit.java). [Investment income](investment-income.md) describes how asset returns and private pensions contribute to income.

</div>
