---
title: Family composition
---

<div class="module-detail" markdown>

# Family composition

<p class="module-detail__lead">The family composition module is the principal source of interactions between simulated agents. It projects the formation and dissolution of cohabiting relationships, partner matching and fertility.</p>

When a relationship forms, partners are selected through a matching process designed to reflect correlations between partners' characteristics observed in survey data. The proportion of the population in a cohabiting relationship can be aligned to observed population aggregates, accounting for changes in household structure introduced by population alignment.

Eligible women can give birth to one child in each simulated year. Fertility depends on individual and household characteristics, and fertility rates can be aligned to targets derived from official population projections.

## Implementation diagram

The diagram shows the UK module's annual sequence and branching choices.

<details class="module-process-diagram">
  <summary>View the family composition diagram</summary>
  <div class="module-process-diagram__meta">
    <span>Scroll to follow the complete sequence.</span>
    <a href="/figures/modules/family_composition_module.png" target="_blank" rel="noopener">Open full resolution <span aria-hidden="true">&#8599;</span></a>
  </div>
  <div class="module-process-diagram__viewport" role="region" aria-label="Scrollable family composition implementation diagram" tabindex="0">
    <img src="/figures/modules/optimized/family-composition-flow.png" alt="Family composition implementation flow" width="1200" height="4703" loading="lazy" decoding="async">
  </div>
</details>

## Partnerships and cohabitation

Individuals who have reached the minimum partnership age and do not have a partner may enter a partnership according to a probit model. Explanatory variables describe demographic characteristics, education, employment, household income, children and health. The estimated specification varies across country models.

Individuals selected to enter a partnership are matched using either a parametric or non-parametric process. The current processes focus on opposite-sex relationships.

In the default parametric process, the model searches the pools of men and women selected to cohabit and minimises a score based on the distance between each person's characteristics and their prospective partner's preferred age and earnings potential. Matching is attempted within regions first. If a sufficient number or quality of matches cannot be found, the remaining candidates are considered in a national pool.

The non-parametric alternative uses iterative proportional fitting to reproduce the distribution of matches observed in survey data. A person type is defined by gender, region, education and age.

### Partnership alignment

The matching process does not necessarily identify a partner for every person selected to enter a relationship. This can lead to an under-representation of couples in the simulated population. When cohabitation alignment is enabled, the intercepts of the relationship-formation equations are adjusted so that the incidence of couples follows survey targets. After the final alignment year, the last estimated adjustment is retained.

## Partnership dissolution

Partnership dissolution is modelled using a probit model. Explanatory variables describe the partners' characteristics, household circumstances and partnership duration, with the precise specification depending on the country model. In the UK implementation, the probability is evaluated for the female partner and the resulting separation updates both partners' household and benefit-unit membership.

## Fertility

Women within the model's fertility age range may give birth to one child in a simulated year. Eligibility also depends on the assumption about births to women without a partner; the UK model includes these births.

In the UK implementation, a probit model (F1) supplies each eligible woman's probability of giving birth, conditional on her observed characteristics. A random draw determines whether a birth occurs. The age limits and eligibility settings are defined in the [model parameters](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/data/Parameters.java), and the equation is supplied in `reg_fertility.xlsx`.

### Birth alignment

In the UK model, fertility alignment adjusts the probit intercept so that the expected fertility rate matches the target derived from population projections. Individual birth outcomes remain stochastic. After the final alignment year, the last adjustment is retained. The calculation is implemented in [`FertilityAlignment`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/model/FertilityAlignment.java).

## Technical implementation

### Annual scheduling

The UK annual schedule coordinates the module in the following order:

1. `UpdatePotentialHourlyEarnings` refreshes full-time hourly earning potentials used in partner matching.
2. `CohabitationAlignment` estimates the partnership adjustment when alignment is enabled; `Cohabitation` then identifies people to be considered for matching in `personsToMatch`.
3. `PartnershipDissolution` evaluates existing couples.
4. `UnionMatching` forms new couples using the selected matching method.
5. `FertilityAlignment`, when enabled, is followed by the person-level `Fertility` and `GiveBirth` processes.

The model-level ordering is defined in [`SimPathsModel.buildSchedule()`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/model/SimPathsModel.java). Person-level transitions are implemented in [`Person`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/model/Person.java). See [The SimPathsModel Class](../../developer-guide/internals/simpaths-model.md) for the wider scheduling architecture.

### Regional and national matching

SimPaths provides three matching methods: `SBAM`, `Parametric` and `ParametricNoRegion`. The default is `ParametricNoRegion`.

For parametric matching, `SimPathsModel.unionMatching()` constructs sets of unmatched men and women for each region and passes each regional pool to `evalMatches()`. Under `ParametricNoRegion`, `unionMatchingNoRegion()` then repeats the procedure for the candidates left in `personsToMatch`, this time using a national pool. `evalMatches()` creates a [`UnionMatching`](https://github.com/simpaths/SimPaths/blob/develop/src/main/java/simpaths/model/UnionMatching.java) object, runs the global matching algorithm and removes accepted pairs from the candidate pools.

### Pair selection and household formation

`UnionMatching.evaluateGM()` constructs every feasible man-woman candidate pair and assigns a score using `localGetValue()`. Candidate pairs are sorted from the lowest score upwards. A pair is accepted only if both people remain unmatched; `localMatch()` then records the match, removes both people from the unmatched sets and creates their benefit unit and household.

The matching score combines two differences:

- the observed age difference, adjusted for the man's preferred age difference;
- the observed difference in full-time earning potential, adjusted for the woman's preferred earnings difference.

Pairs involving a parent and child, or pairs outside the configured age- and earnings-difference bounds, receive an infinite score and are excluded. For an accepted pair, the woman's region is set to the man's region, partnership duration is initialised and `setupNewBenefitUnit()` updates the simulated household structure.

The detailed source diagrams remain available for the [cohabitation process](../../figures/modules/partnership_module.png), the [`UnionMatching` algorithm](../../figures/modules/union_matching_module.png) and [fertility](../../figures/modules/fertility_module.png).

</div>
