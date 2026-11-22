---
title: Labour income
---

<div class="module-detail" markdown>

# Labour income

<p class="module-detail__lead">The labour income module projects potential hourly wage rates for each simulated adult in each year and their associated labour activity. Given potential wage rates, hours of paid employment by all adult members of a benefit unit are generated. Gross labour income is then determined by multiplying hours worked by the wage rate.</p>

## Wage rates

Hourly wage rates are simulated for each adult in the model based on Heckman-corrected regressions stratified by gender and lagged employment status, distinguishing between employed and non-employed individuals. The explanatory variables include part-time employment identifiers, age, education, student status, parental education, relationship status, presence of children, health and region. For individuals observed in employment in the previous year, lagged log hourly wage rates are also included as an explanatory variable.

### Potential-earnings diagram

This diagram records the implementation of the potential hourly-earnings update. It does not represent the complete labour-supply model.

<details class="module-process-diagram">
  <summary>View the potential-earnings diagram</summary>
  <div class="module-process-diagram__meta">
    <span>Scroll to follow the complete sequence.</span>
    <a href="/figures/modules/update_full_time_hourly_earnings_module.png" target="_blank" rel="noopener">Open full resolution <span aria-hidden="true">&#8599;</span></a>
  </div>
  <div class="module-process-diagram__viewport" role="region" aria-label="Scrollable potential earnings implementation diagram" tabindex="0">
    <img src="/figures/modules/optimized/potential-earnings-flow.png" alt="Potential hourly earnings implementation flow" width="1200" height="3388" loading="lazy" decoding="async">
  </div>
</details>

## Employment decisions

Two alternative methods for projecting employment decisions can be considered by the model. These alternatives are both designed to reflect the influence of financial incentives on behaviour and are distinguished by whether they reflect forward-looking expectations.

### Default within-period model

The default specification of SimPaths projects labour supply using a non-forward-looking random utility model. The method projects labour supply as though employment decisions are made to maximise within-period benefit-unit utility over a discrete set of labour/income alternatives. Given any labour alternative, labour income is computed by combining hours of work with the respective hourly wage rate. The utility of the benefit unit is calculated using a quadratic utility function of disposable income and the leisure of its adult members.

The UK labour-supply model is estimated using UKMOD in combination with UKHLS data for 2019/20. The estimation employs an alternative-specific conditional logit approach (Stata command `asclogit`).

#### Eligible decision units and alternatives

The default simulation considers labour-supply choices for adults aged 16–75 who are neither students nor retired. A labour-supply-flexible adult without a partner, or with a non-flexible partner, is evaluated individually. Couples in which both partners satisfy the flexible-worker conditions are evaluated jointly.

Each labour-supply-flexible individual chooses among seven mutually exclusive alternatives: non-employment and six weekly hours categories.

| Alternative | Representative weekly hours | Observed hours category |
| --- | ---: | --- |
| 0 | 0 | Non-employment |
| 1 | 10 | 6-15 hours |
| 2 | 20 | 16-25 hours |
| 3 | 30 | 26-35 hours |
| 4 | 38 | 36-40 hours |
| 5 | 45 | 41-49 hours |
| 6 | 55 | 50 hours or more |

For couples, the choice set expands into all combinations of the two partners' options.

The observed choice, together with simulated incomes and computed leisure, identifies the utility parameters.

#### Income and leisure under each alternative

Leisure is computed as total weekly hours minus hours worked and hours spent providing care. Caregiving is therefore treated as a time cost that reduces leisure in the utility function.

For estimation, disposable income for each alternative is simulated using UKMOD as follows. Hourly wages are predicted using a Heckman two-step selection model based on UKHLS-UKMOD panel data. The model is estimated separately for individuals with and without previous employment, and within each group separately for men and women. Predicted wages are used for all individuals, as this approach performs better than using predicted wages for non-workers only. The predicted hourly wage is then used to compute employment income for each hours alternative.

To simulate disposable income for each hours scenario, the following conventions are used:

1. Benefits that UKMOD can simulate, such as Universal Credit and tax credits, are calculated by UKMOD for every hours alternative.
2. Benefits that UKMOD cannot simulate and that depend on employment income are set to zero for all counterfactual alternatives. Otherwise, they would appear only in the observed alternative and distort utility estimation.
3. Benefits that UKMOD cannot simulate, do not depend on employment income and are compatible with work retain their observed amount in all alternatives.
4. Individuals receiving benefits incompatible with work in the observed state are dropped from the estimation sample. This applies, for example, to some disability benefits, whose receipt implies that some choices are not feasible.

UKMOD calculates disposable income for each scenario, and the individual's observed choice identifies which of the seven alternatives they actually selected.

#### Estimation groups and utility specification

The estimation sample is split into subsamples, and `asclogit` is used to estimate the marginal utilities of income and leisure and the preference shifters for each of these subsamples:

- single women;
- single men;
- couples in which both partners are flexible;
- women with a non-flexible partner;
- men with a non-flexible partner;
- adult children, female; and
- adult children, male.

In practice, limited sample sizes required the sexes to be pooled for couples in which only one partner is flexible and for adult children.

Utility is modelled as a quadratic function of disposable income and leisure. The specifications differ across groups:

- In the models for single women and single men, a dummy for full-time work is included to capture any fixed utility shift associated with full-time employment. The specification also conditions on years spent in employment to account for accumulated labour-market experience.
- In the couples' model, full-time-work dummies and previous labour-market experience variables are included for both partners.
- In the model for adults with a non-flexible partner, men and women are estimated jointly, and differences by sex are accounted for by interacting the full-time-work dummy with a sex dummy.
- In the model for adult children, the sample is also pooled, with an interaction between sex and full-time work. Previous labour-market experience is not included, as it was not statistically significant.

### Forward-looking specification

The model can also be directed to project labour supply and discretionary consumption to reflect forward-looking expectations for behavioural incentives. As in the default specification, the unit of analysis is the benefit unit. Incentives are translated into behaviour via an assumed intertemporal utility function. By default, the model adopts a nested constant elasticity of substitution (CES) utility function.

Each adult is considered to have three alternative labour-supply options, corresponding to full-time work, part-time work and non-employment. Labour supply and discretionary consumption are projected as though they maximise the assumed utility function, subject to a hard constraint on net wealth and assumed agent expectations. Expectations are “substantively rational” in the sense that uncertainty is characterised by the random draws that underlie dynamic projection of modelled characteristics. As no analytical solution to this problem exists, numerical solution methods are employed.

The model proceeds in two discrete steps. The first step involves solution of the lifetime decision problem for potential combinations of agent-specific characteristics, with solutions stored in a look-up table. The second step uses the look-up table as the basis for projecting labour supply and discretionary consumption.

## Alignment

When the default specification of SimPaths for projecting labour supply is used, the estimated utility of single men, single women and couples is adjusted to align the aggregate employment rate to the employment rate observed in the data in the validation period. The final adjustment value is used in subsequent periods for which no data are available. This procedure accounts for the existence of unemployment in the real economy and the fact that labour-supply decisions simulated using the random utility model assume no constraints on labour demand in the economy.

## Technical implementation

Annual ordering is coordinated by [`SimPathsModel`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/model/SimPathsModel.java). Person-level wages and employment states are held in [`Person`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/model/Person.java), while joint decisions and income calculations are coordinated at [`BenefitUnit`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/model/BenefitUnit.java) level. Parameter files are summarised under [Model Parameterisation](../parameterisation.md).

</div>
